package com.nbk.insights.service

import com.nbk.insights.dto.BudgetAdherenceResponse
import com.nbk.insights.dto.CategoryAdherence
import com.nbk.insights.dto.ChatRequest
import com.nbk.insights.dto.ChatResponse
import com.nbk.insights.dto.Message
import com.nbk.insights.repository.OffersEntity
import com.nbk.insights.repository.OffersRepository
import com.nbk.insights.repository.RecommendationEntity
import com.nbk.insights.repository.RecommendationRepository
import com.nbk.insights.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.lang.RuntimeException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nbk.insights.dto.CashFlowCategorizedResponse
import com.nbk.insights.dto.CategoryRecommendationResponse
import com.nbk.insights.dto.OfferBrief
import com.nbk.insights.dto.OffersRecommendationResponse
import com.nbk.insights.dto.QuickInsightsResponse
import com.nbk.insights.repository.MccRepository
import java.time.LocalDateTime


@Service
class RecommendationsService(
    private val openAIWebClient: WebClient,
    private val limitsService: LimitsService,
    private val userRepository: UserRepository,
    private val offersRepository: OffersRepository,
    private val recommendationRepository: RecommendationRepository,
    private val transactionsService: TransactionsService,
    private val mccRepository: MccRepository,
) {

    fun getCategoryRecommendations(userId: Long): List<CategoryRecommendationResponse> {
        val budgetAdherence = limitsService.checkBudgetAdherence(userId)
        val categories = budgetAdherence.categoryAdherences.map { it.category }.distinct()
        val relevantOffers = offersRepository.findAllByMccCategories(categories)
        val prompt = buildFullPrompt(budgetAdherence.categoryAdherences, relevantOffers)

        val request = ChatRequest(
            model = "gpt-4o",
            messages = listOf(
                Message("system", "You are an assistant providing budgeting recommendations tailored to user's spending habits and available offers at NBK"),
                Message("user", prompt)
            ),
            temperature = 0.7
        )

        val response = openAIWebClient.post()
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.isError }) {
                throw RuntimeException("OpenAI returned error status ${it.statusCode()}")
            }
            .bodyToMono(ChatResponse::class.java)
            .block() ?: throw RuntimeException("No response from OpenAI")

        val cleanedJson = response.choices.firstOrNull()?.message?.content
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw IllegalStateException("ChatGPT returned no valid content")

        val mapper = jacksonObjectMapper()
        val parsedJson: List<Map<String, String>> = mapper.readValue(cleanedJson)

        val result = parsedJson.mapNotNull { entry ->
            val category = entry["category"]
            val recommendation = entry["recommendation"]
            if (category != null && recommendation != null) {
                recommendationRepository.save(
                    RecommendationEntity(
                        userId = userId,
                        reason = "[$category] $recommendation",
                        createdAt = LocalDateTime.now()
                    )
                )
                CategoryRecommendationResponse(category, recommendation)
            } else null
        }

        return result
    }

    fun getOffersRecommendation(userId: Long): OffersRecommendationResponse {
        val latestRecommendations = recommendationRepository.findLatestRecommendationsPerCategory(userId)

        if (latestRecommendations.isEmpty()) {
            return OffersRecommendationResponse(
                message = "No personalized offer insights available yet. Start budgeting to unlock your top NBK offers!",
                offers = emptyList()
            )
        }

        val categories = latestRecommendations.mapNotNull {
            Regex("""\[(.*?)]""").find(it.reason)?.groupValues?.get(1)
        }.distinct()

        // Fetch offer details with subcategory
        val relevantOffers = offersRepository.findAllByMccCategories(categories).mapNotNull { offerEntity ->
            val mccEntity = mccRepository.findById(offerEntity.mccCategoryId).orElse(null)
            OfferBrief(
                id = offerEntity.id,
                description = offerEntity.description,
                subCategory = mccEntity?.subCategory
            )
        }


        val prompt = buildBestOffersPrompt(categories, relevantOffers)

        val request = ChatRequest(
            model = "gpt-4o",
            messages = listOf(
                Message("system", "You are an NBK assistant recommending the best offers..."),
                Message("user", prompt)
            ),
            temperature = 0.5
        )

        val response = openAIWebClient.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatResponse::class.java)
            .block() ?: throw RuntimeException("No response from OpenAI")

        val content = response.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("ChatGPT returned no content")

        val cleanedJson = content.replace("```json", "").replace("```", "").trim()
        val mapper = jacksonObjectMapper()
        val parsed = mapper.readValue<Map<String, Any>>(cleanedJson)

        val message = parsed["message"] as? String
            ?: throw IllegalStateException("Missing message in ChatGPT response")

        val offerIdsFromChatGPT = (parsed["offerIds"] as? List<*>)?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()

        // Final offers to return
        val selectedOffers = relevantOffers.filter { it.id in offerIdsFromChatGPT }

        return OffersRecommendationResponse(
            message = message,
            offers = selectedOffers
        )
    }


    fun getQuickInsights(userId: Long): QuickInsightsResponse {
        val thisMonth = transactionsService.getUserCurrentMonthCashFlow(userId)
        val lastMonth = transactionsService.getUserPreviousMonthCashFlow(userId)
        val adherence = limitsService.checkBudgetAdherence(userId)

        val prompt = buildQuickInsightsPrompt(thisMonth, lastMonth, adherence)

        val request = ChatRequest(
            model = "gpt-4o",
            messages = listOf(
                Message("system", "You provide financial summary insights"),
                Message("user", prompt)
            ),
            temperature = 0.7
        )

        val chatResponse = openAIWebClient.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatResponse::class.java)
            .block() ?: throw RuntimeException("No response from ChatGPT")

        val jsonContent = chatResponse.choices.firstOrNull()?.message?.content
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw RuntimeException("No valid content from GPT")

        return jacksonObjectMapper().readValue(jsonContent, QuickInsightsResponse::class.java)
    }


    private fun buildFullPrompt(
        adherences: List<CategoryAdherence>,
        offers: List<OffersEntity>
    ): String {
        val builder = StringBuilder()
        builder.appendLine("You are a budgeting assistant for National Bank of Kuwait (NBK).")
        builder.appendLine("A user wants category-wise recommendations based on their monthly spending and budget adherence.")
        builder.appendLine("You also have access to exclusive NBK offers related to each spending category and make sure to mention they are from NBK.")
        builder.appendLine("Instructions:")
        builder.appendLine("- Use the data for each category.")
        builder.appendLine("- Give JSON output: category as key, short friendly recommendation as value, no emojis, no HTML tags, no special characters, no rude tone and be as concise as possible with professionalism.")
        builder.appendLine("- If they overspent, gently inform them that they have underspent and suggest NBK offers to help reduce next month’s spending. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights, and if you recommend them something in the form of investment or saving their money look up any account types NBK would support doing that")
        builder.appendLine("- If they underspent, inform them and praise them and recommend how to leverage NBK’s offers. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights, and if you recommend them something in the form of investment or saving their money look up any account types NBK would support doing that")
        builder.appendLine("- If they are within budget, positively reinforce and suggest further optimization.")
        builder.appendLine("- Use a friendly, concise tone. Prioritize personalization and empathy.")
        builder.appendLine("- Use the following list of NBK offers if they are relevant.\n")
        builder.appendLine("- when mentioning money make sure to add KD right after the number \"17.89KD\"")
        builder.appendLine("- when you want to mention how much they overspent or underspent use percentages and thats it no using terms like out of")
        builder.appendLine("- Decision Logic:\n" +
                " • Overspent (PercentageUsed > 100):\n" +
                "   – Inform user of \"X% over budget.\"\n" +
                "   – Offer one tip to reduce next month’s spending.\n" +
                "   – Suggest relevant NBK offer.\n" +
                " • Underspent (PercentageUsed < 50):\n" +
                "   – Praise user for \"X% under budget.\"\n" +
                "   – Offer one practical tip to stay on track.\n" +
                "   – Suggest relevant NBK offer and optionally mention NBK Savings or Fixed Deposit.\n" +
                "• On track (50 ≤ PercentageUsed ≤ 100):\n" +
                "   – Reinforce \"You’re X% used.\"\n" +
                "   – Offer one optimization tip.\n" +
                "   – Suggest relevant NBK offer.")

        // Include only offers the user might care about
        offers.groupBy { it.mccCategoryId }.forEach { (mccId, offerGroup) ->
            val mcc = offerGroup.first().mccCategoryId
            val descriptions = offerGroup.joinToString(separator = "\n- ") { it.description }
            builder.appendLine("Offers for MCC ID $mcc:\n- $descriptions\n")
        }

        builder.appendLine("\nHere’s the user's budget data:")
        adherences.forEach {
            builder.appendLine("""
            {
              "Category": "${it.category}",
              "BudgetAmount": "${it.budgetAmount} KWD",
              "SpentAmount": "${it.spentAmount} KWD",
              "PercentageUsed": "${"%.2f".format(it.percentageUsed)}%",
              "RemainingAmount": "${it.remainingAmount} KWD",
              "AdherenceLevel": "${it.adherenceLevel.displayName}",
              "RenewalDate": "${it.renewsAt}",
              "PeriodStart": "${it.periodStart}",
              "PeriodEnd": "${it.periodEnd}",
              "DaysInPeriod": "${it.daysInPeriod}",
              "IsActivePeriod": "${it.isActivePeriod}",
              "LastMonthSpent": "${it.lastMonthSpentAmount} KWD",
              "SpendingTrend": "${it.spendingTrend.displayName}",
              "SpendingChange": "${it.spendingChange} KWD",
              "SpendingChangePercentage": "${"%.2f".format(it.spendingChangePercentage)}%"
            },
            """.trimIndent())
        }

        builder.appendLine("\nNow reply in a JSON array format. Each element should have:")
        builder.appendLine("- category: the name of the spending category")
        builder.appendLine("- recommendation: your actual response")

        return builder.toString()
    }

    private fun buildBestOffersPrompt(
        categories: List<String>,
        offers: List<OfferBrief>
    ): String {
        val builder = StringBuilder()

        builder.appendLine("You are an assistant for the National Bank of Kuwait (NBK).")
        builder.appendLine("The user is looking for the most suitable offers to explore based on their recent spending patterns and categories they received recommendations for.")
        builder.appendLine("You are given a list of offers by category (MCC ID) that the user is eligible for.")
        builder.appendLine("You must write a short, friendly, and informative message suggesting the best offers based on the user's interests and prior recommendations.")

        builder.appendLine()
        builder.appendLine("Instructions:")
        builder.appendLine("- Write a clear and concise message (1–2 sentences) summarizing which offers they should explore based on their spending and recommendations.")
        builder.appendLine("- DO NOT list every offer. Pick only the most relevant ones across all categories.")
        builder.appendLine("- Keep the tone helpful, positive, and brief.")
        builder.appendLine("- Do NOT mention IDs of offers in the description")
        builder.appendLine("- Only recommend actual NBK offers provided below.")
        builder.appendLine("- When referencing money, write values like this: '10KD'.")
        builder.appendLine("- Avoid using special characters, emojis, or HTML tags.")
        builder.appendLine("- Prioritize relevance over quantity — the goal is to highlight the *best-fit* offers.")
        builder.appendLine()
        builder.appendLine("Output format (in JSON):")
        builder.appendLine(
            """
        {
          "message": "<your recommendation message>",
          "offerIds": [<IDs of the offers you mentioned in the message>]
        }
        """.trimIndent()
        )
        builder.appendLine("- Do NOT include offerIds unless the offer was actually mentioned in the message.")
        builder.appendLine("- Be accurate when mapping descriptions to IDs.")

        builder.appendLine()
        builder.appendLine("Here are the NBK offers grouped by MCC category:")

        builder.appendLine("\nHere are the NBK offers grouped by subcategory:")
        offers.groupBy { it.subCategory ?: "General" }.forEach { (subCat, offerGroup) ->
            builder.appendLine("Offers for $subCat:")
            offerGroup.forEach { offer ->
                builder.appendLine("- ID ${offer.id}: ${offer.description}")
            }
            builder.appendLine()
        }

        return builder.toString()
    }

    private fun buildQuickInsightsPrompt(
        thisMonth: CashFlowCategorizedResponse,
        lastMonth: CashFlowCategorizedResponse,
        adherence: BudgetAdherenceResponse
    ): String {
        return buildString {
            appendLine("You are an assistant helping users understand their financial behavior using data from NBK. Make sure the suggestions for each field is short enough to add in a card in a lazy row in the frontend")
            appendLine("Each insight should be unique and not repeat information covered in the others.")
            appendLine("Based on the information below, generate 3 short insights:")
            appendLine("1. spendingComparedToLastMonth - a brief summary comparing this month's vs last month's total spending. Mention actual KD values (e.g., You spent 124KD less than last month), and optionally percentage (e.g., 40% less). Do NOT use confusing expressions like '0.6 times less'. Avoid using 'times' language completely.")
            appendLine("2. budgetLimitWarning - identify any categories where spending is near, at, or over the budget (mention category names and level) an example for this is you exceeded the category budget by X times this amount or underspent. if all are within budget just tell them that their are well within their budget")
            appendLine("3. savingInsights - offer a savings-oriented insight such as identifying good cash flow surplus, recommending categories where they can allocate more to savings, or suggesting saving behaviors based on low spending (e.g., consider saving the unused amount). Highlight opportunities to put money aside or where habits indicate long-term saving potential.")
            appendLine("Avoid generic advice and refer to actual data in your message.")

            appendLine("\nReturn only a JSON response with the following structure:")
            appendLine("{")
            appendLine("  \"spendingComparedToLastMonth\": \"...\",")
            appendLine("  \"budgetLimitWarning\": \"...\",")
            appendLine("  \"savingInsights\": \"...\"")
            appendLine("}")

            appendLine("\n--- Current Month Cash Flow ---")
            appendLine("Total Money In: ${thisMonth.moneyIn}KD")
            appendLine("Total Money Out: ${thisMonth.moneyOut}KD")
            appendLine("Net Cash Flow: ${thisMonth.netCashFlow}KD")

            appendLine("\nMoney Out by Category:")
            thisMonth.moneyOutByCategory.forEach { (category, amount) ->
                appendLine("- $category: ${amount}KD")
            }

            appendLine("\n--- Previous Month Cash Flow ---")
            appendLine("Total Money In: ${lastMonth.moneyIn}KD")
            appendLine("Total Money Out: ${lastMonth.moneyOut}KD")
            appendLine("Net Cash Flow: ${lastMonth.netCashFlow}KD")

            appendLine("\nMoney Out by Category (Last Month):")
            lastMonth.moneyOutByCategory.forEach { (category, amount) ->
                appendLine("- $category: $amount KD")
            }

            appendLine("\n--- Budget Adherence ---")
            appendLine("Total Budget: ${adherence.totalBudget}KD")
            appendLine("Total Spent: ${adherence.totalSpent}KD")
            appendLine("Overall Adherence: ${adherence.overallAdherence.displayName}")

            adherence.categoryAdherences.forEach {
                appendLine("- ${it.category}:")
                appendLine("  - Budget: ${it.budgetAmount}KD")
                appendLine("  - Spent: ${it.spentAmount}KD")
                appendLine("  - Usage: ${String.format("%.1f", it.percentageUsed)}% (${it.adherenceLevel.displayName})")
                appendLine("  - Remaining: ${it.remainingAmount}KD")
                appendLine("  - Spending Trend: ${it.spendingTrend.displayName}")
                appendLine("  - Change from last month: ${it.spendingChange}KD (${String.format("%.1f", it.spendingChangePercentage)}%)")
            }
        }
    }
}

