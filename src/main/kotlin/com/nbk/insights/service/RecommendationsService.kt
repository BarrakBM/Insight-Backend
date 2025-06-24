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
import com.nbk.insights.dto.CategoryRecommendationResponse
import com.nbk.insights.dto.OffersRecommendationResponse
import java.time.LocalDateTime


@Service
class RecommendationsService(
    private val openAIWebClient: WebClient,
    private val limitsService: LimitsService,
    private val userRepository: UserRepository,
    private val offersRepository: OffersRepository,
    private val recommendationRepository: RecommendationRepository
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
                message = "No personalized offer insights available yet. Start budgeting to unlock your top NBK offers!"
            )
        }

        val categories = latestRecommendations.mapNotNull {
            Regex("""\[(.*?)]""").find(it.reason)?.groupValues?.get(1)
        }.distinct()

        val relevantOffers = offersRepository.findAllByMccCategories(categories)

        val prompt = buildBestOffersPrompt(categories, relevantOffers)

        val request = ChatRequest(
            model = "gpt-4o",
            messages = listOf(
                Message("system", "You are an NBK assistant recommending the best offers for a user based on their past recommendations and spending categories."),
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
            ?: "Explore NBK offers tailored to your spending habits."

        return OffersRecommendationResponse(message = content)
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
        offers: List<OffersEntity>
    ): String {
        val builder = StringBuilder()

        builder.appendLine("You are an assistant at NBK.")
        builder.appendLine("This user has recently shown spending activity in the following categories:")
        categories.forEach { builder.appendLine("- $it") }

        builder.appendLine("\nHere are the available NBK offers per category:")
        offers.groupBy { it.mccCategoryId }.forEach { (cat, list) ->
            builder.appendLine("Category: $cat")
            list.forEach { builder.appendLine("- ${it.description}") }
        }

        builder.appendLine("\nNow generate a single 1–2 sentence insight that highlights the most relevant NBK offers this user should check out.")
        builder.appendLine("Use a friendly but professional tone. Be very concise. Do not mention more than 3 offers. Focus on what best matches their habits.")

        return builder.toString()
    }

}

