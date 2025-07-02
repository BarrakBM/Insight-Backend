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
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import java.lang.RuntimeException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hazelcast.logging.Logger
import com.nbk.insights.dto.CashFlowCategorizedResponse
import com.nbk.insights.dto.CategoryRecommendationResponse
import com.nbk.insights.dto.OfferBrief
import com.nbk.insights.dto.OffersRecommendationResponse
import com.nbk.insights.dto.QuickInsightsResponse
import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.RecommendationScheduleEntity
import com.nbk.insights.repository.RecommendationScheduleRepository
import com.nbk.insights.repository.RecommendationType
import com.nbk.insights.serverInsightsCache
import java.time.temporal.ChronoUnit
import com.nbk.insights.dto.*
import com.nbk.insights.repository.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nbk.insights.serverInsightsCache
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime

private val loggerRecommendationOffers = LoggerFactory.getLogger("recommendation-offers")
private val loggerRecommendationCategory = LoggerFactory.getLogger("recommendation-category")
private val loggerQuickInsights = LoggerFactory.getLogger("quick-insights")

@Service
class RecommendationsService(
    private val openAIWebClient: WebClient,
    private val limitsService: LimitsService,
    private val userRepository: UserRepository,
    private val offersRepository: OffersRepository,
    private val recommendationRepository: RecommendationRepository,
    private val transactionsService: TransactionsService,
    private val mccRepository: MccRepository,
    private val recommendationScheduleRepository: RecommendationScheduleRepository,
    private val fallbackRecommendationService: FallbackRecommendationService,
    private val apiHealthService: ApiHealthService
) {

    companion object {
        private const val OPENAI_SERVICE = "openai"
        private const val API_TIMEOUT_SECONDS = 30L
    }

    fun getCategoryRecommendations(userId: Long): List<CategoryRecommendationResponse> {
        val schedule = getOrCreateSchedule(userId, RecommendationType.CATEGORY)

        if (!shouldGenerateCategoryRecommendations(userId, schedule)) {
            loggerRecommendationCategory.info("Not time yet for category recommendations for userId=$userId")

            val categoryCache = serverInsightsCache.getMap<Long, List<CategoryRecommendationResponse>>("recommendation-category")
            categoryCache[userId]?.let {
                loggerRecommendationCategory.info("Returning cached category recommendations for userId=$userId")
                return it
            }

            return emptyList()
        }

        // Generate new recommendations
        val budgetAdherence = limitsService.checkBudgetAdherence(userId)
        val categories = budgetAdherence.categoryAdherences.map { it.category }.distinct()
        val relevantOffers = offersRepository.findAllByMccCategories(categories)

        // Check circuit breaker
        val circuitBreaker = apiHealthService.getCircuitBreaker(OPENAI_SERVICE)

        val result = if (circuitBreaker.isAvailable()) {
            try {
                // Try OpenAI API with timeout
                val openAIResult = callOpenAIForCategories(budgetAdherence.categoryAdherences, relevantOffers)
                circuitBreaker.recordSuccess()

                // Save to database
                openAIResult.forEach { recommendation ->
                    recommendationRepository.save(
                        RecommendationEntity(
                            userId = userId,
                            reason = "[${recommendation.category}] ${recommendation.recommendation}",
                            createdAt = LocalDateTime.now()
                        )
                    )
                }

                openAIResult
            } catch (e: Exception) {
                loggerRecommendationCategory.error("OpenAI API failed for userId=$userId: ${e.message}")
                circuitBreaker.recordFailure(e)

                // Use fallback
                val fallbackResult = fallbackRecommendationService.generateCategoryRecommendations(
                    budgetAdherence.categoryAdherences,
                    relevantOffers
                )

                // Save fallback recommendations with a marker
                fallbackResult.forEach { recommendation ->
                    recommendationRepository.save(
                        RecommendationEntity(
                            userId = userId,
                            reason = "[${recommendation.category}] [FALLBACK] ${recommendation.recommendation}",
                            createdAt = LocalDateTime.now()
                        )
                    )
                }

                fallbackResult
            }
        } else {
            loggerRecommendationCategory.warn("Circuit breaker OPEN for OpenAI, using fallback for userId=$userId")

            // Use fallback directly
            fallbackRecommendationService.generateCategoryRecommendations(
                budgetAdherence.categoryAdherences,
                relevantOffers
            )
        }

        // Update schedule
        updateCategorySchedule(userId, schedule, budgetAdherence)

        // Cache the result
        val categoryCache = serverInsightsCache.getMap<Long, List<CategoryRecommendationResponse>>("recommendation-category")
        categoryCache[userId] = result

        loggerRecommendationCategory.info("Generated recommendations for userId=$userId (fallback=${!circuitBreaker.isAvailable()})")
        return result
    }

    fun getOffersRecommendation(userId: Long): OffersRecommendationResponse {
        val schedule = getOrCreateSchedule(userId, RecommendationType.OFFERS)

        if (!shouldGenerateRecommendations(schedule)) {
            loggerRecommendationOffers.info("Not time yet for offer recommendations for userId=$userId")

            val recommendationCache = serverInsightsCache.getMap<Long, OffersRecommendationResponse>("recommendation-offers")
            recommendationCache[userId]?.let {
                loggerRecommendationOffers.info("Returning cached offer recommendations for userId=$userId")
                return it
            }

            return OffersRecommendationResponse(
                message = "Check back next week for personalized NBK offers tailored to your spending!",
                offers = emptyList()
            )
        }

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

        val relevantOffers = offersRepository.findOfferBriefsByCategories(categories)

        // Check circuit breaker
        val circuitBreaker = apiHealthService.getCircuitBreaker(OPENAI_SERVICE)

        val offerResponse = if (circuitBreaker.isAvailable()) {
            try {
                val result = callOpenAIForOffers(categories, relevantOffers)
                circuitBreaker.recordSuccess()
                result
            } catch (e: Exception) {
                loggerRecommendationOffers.error("OpenAI API failed for offers: ${e.message}")
                circuitBreaker.recordFailure(e)
                fallbackRecommendationService.generateOffersRecommendation(categories, relevantOffers)
            }
        } else {
            loggerRecommendationOffers.warn("Circuit breaker OPEN, using fallback for offers")
            fallbackRecommendationService.generateOffersRecommendation(categories, relevantOffers)
        }

        // Update schedule
        schedule.lastGeneratedAt = LocalDateTime.now()
        schedule.nextScheduledAt = LocalDateTime.now().plusDays(7)
        recommendationScheduleRepository.save(schedule)

        // Cache the result
        val recommendationCache = serverInsightsCache.getMap<Long, OffersRecommendationResponse>("recommendation-offers")
        recommendationCache[userId] = offerResponse

        return offerResponse
    }

    fun getQuickInsights(userId: Long): QuickInsightsResponse {
        val schedule = getOrCreateSchedule(userId, RecommendationType.QUICK_INSIGHTS)

        if (!shouldGenerateRecommendations(schedule)) {
            loggerQuickInsights.info("Not time yet for quick insights for userId=$userId")

            val insightsCache = serverInsightsCache.getMap<Long, QuickInsightsResponse>("quick-insights")
            insightsCache[userId]?.let {
                loggerQuickInsights.info("Returning cached quick insights for userId=$userId")
                return it
            }

            return QuickInsightsResponse(
                spendingComparedToLastMonth = "Check back tomorrow for updated spending comparisons",
                budgetLimitWarning = "Your budget insights will refresh daily",
                savingInsights = "New savings insights available tomorrow"
            )
        }

        val thisMonth = transactionsService.getUserCurrentMonthCashFlow(userId)
        val lastMonth = transactionsService.getUserPreviousMonthCashFlow(userId)
        val adherence = limitsService.checkBudgetAdherence(userId)

        // Check circuit breaker
        val circuitBreaker = apiHealthService.getCircuitBreaker(OPENAI_SERVICE)

        val insights = if (circuitBreaker.isAvailable()) {
            try {
                val result = callOpenAIForQuickInsights(thisMonth, lastMonth, adherence)
                circuitBreaker.recordSuccess()
                result
            } catch (e: Exception) {
                loggerQuickInsights.error("OpenAI API failed for quick insights: ${e.message}")
                circuitBreaker.recordFailure(e)
                fallbackRecommendationService.generateQuickInsights(thisMonth, lastMonth, adherence)
            }
        } else {
            loggerQuickInsights.warn("Circuit breaker OPEN, using fallback for quick insights")
            fallbackRecommendationService.generateQuickInsights(thisMonth, lastMonth, adherence)
        }

        // Update schedule
        schedule.lastGeneratedAt = LocalDateTime.now()
        schedule.nextScheduledAt = LocalDateTime.now().plusDays(1)
        recommendationScheduleRepository.save(schedule)

        // Cache the result
        val insightsCache = serverInsightsCache.getMap<Long, QuickInsightsResponse>("quick-insights")
        insightsCache[userId] = insights

        return insights
    }

    // Extracted OpenAI API calls with proper error handling
    private fun callOpenAIForCategories(
        adherences: List<CategoryAdherence>,
        offers: List<OffersEntity>
    ): List<CategoryRecommendationResponse> {
        val prompt = buildFullPrompt(adherences, offers)
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
            .onStatus({ it.isError }) { response ->
                Mono.error(WebClientResponseException(
                    response.statusCode().value(),
                    "OpenAI API error",
                    null, null, null
                ))
            }
            .bodyToMono(ChatResponse::class.java)
            .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .block() ?: throw RuntimeException("No response from OpenAI")

        val cleanedJson = response.choices.firstOrNull()?.message?.content
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw IllegalStateException("ChatGPT returned no valid content")

        val mapper = jacksonObjectMapper()
        val parsedJson: List<Map<String, String>> = mapper.readValue(cleanedJson)

        return parsedJson.mapNotNull { entry ->
            val category = entry["category"]
            val recommendation = entry["recommendation"]
            if (category != null && recommendation != null) {
                CategoryRecommendationResponse(category, recommendation)
            } else null
        }
    }

    private fun callOpenAIForOffers(
        categories: List<String>,
        offers: List<OfferBrief>
    ): OffersRecommendationResponse {
        val prompt = buildBestOffersPrompt(categories, offers)
        val request = ChatRequest(
            model = "gpt-4.1-nano",
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
            .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .block() ?: throw RuntimeException("No response from OpenAI")

        val content = response.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("ChatGPT returned no content")

        val cleanedJson = content.replace("```json", "").replace("```", "").trim()
        val mapper = jacksonObjectMapper()
        val parsed = mapper.readValue<Map<String, Any>>(cleanedJson)

        val message = parsed["message"] as? String
            ?: throw IllegalStateException("Missing message in ChatGPT response")

        val offerIdsFromChatGPT = (parsed["offerIds"] as? List<*>)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()

        val selectedOffers = offers.filter { it.id in offerIdsFromChatGPT }

        return OffersRecommendationResponse(
            message = message,
            offers = selectedOffers
        )
    }

    private fun callOpenAIForQuickInsights(
        thisMonth: CashFlowCategorizedResponse,
        lastMonth: CashFlowCategorizedResponse,
        adherence: BudgetAdherenceResponse
    ): QuickInsightsResponse {
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
            .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .block() ?: throw RuntimeException("No response from ChatGPT")

        val jsonContent = chatResponse.choices.firstOrNull()?.message?.content
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw RuntimeException("No valid content from GPT")

        return jacksonObjectMapper().readValue(jsonContent, QuickInsightsResponse::class.java)
    }

    // [Keep all the existing private methods like getOrCreateSchedule, shouldGenerateRecommendations, etc.]
    // [Keep all the buildPrompt methods unchanged]

    private fun getOrCreateSchedule(userId: Long, type: RecommendationType): RecommendationScheduleEntity {
        return recommendationScheduleRepository.findByUserIdAndRecommendationType(userId, type)
            ?: RecommendationScheduleEntity(
                userId = userId,
                recommendationType = type,
                nextScheduledAt = when (type) {
                    RecommendationType.CATEGORY -> {
                        val adherence = limitsService.checkBudgetAdherence(userId)
                        val earliestPeriodEnd = adherence.categoryAdherences
                            .minByOrNull { it.periodEnd }
                            ?.periodEnd?.atTime(23, 59, 59)
                            ?: LocalDateTime.now().plusDays(30)
                        earliestPeriodEnd
                    }
                    RecommendationType.OFFERS -> LocalDateTime.now().plusDays(7)
                    RecommendationType.QUICK_INSIGHTS -> LocalDateTime.now().plusDays(1)
                }
            ).let { recommendationScheduleRepository.save(it) }
    }

    private fun shouldGenerateRecommendations(schedule: RecommendationScheduleEntity): Boolean {
        val now = LocalDateTime.now()
        return now.isAfter(schedule.nextScheduledAt) || now.isEqual(schedule.nextScheduledAt)
    }

    private fun shouldGenerateCategoryRecommendations(userId: Long, schedule: RecommendationScheduleEntity): Boolean {
        val now = LocalDateTime.now()

        if (!shouldGenerateRecommendations(schedule)) {
            return false
        }

        val adherence = limitsService.checkBudgetAdherence(userId)
        if (adherence.categoryAdherences.isEmpty()) {
            return false
        }

        val hasEndedPeriod = adherence.categoryAdherences.any { category ->
            val periodEnd = category.periodEnd.atTime(23, 59, 59)
            now.isAfter(periodEnd) &&
                    (schedule.lastBudgetPeriodEnd == null || periodEnd.isAfter(schedule.lastBudgetPeriodEnd))
        }

        return hasEndedPeriod
    }

    private fun updateCategorySchedule(
        userId: Long,
        schedule: RecommendationScheduleEntity,
        adherence: BudgetAdherenceResponse
    ) {
        val now = LocalDateTime.now()

        val mostRecentEndedPeriod = adherence.categoryAdherences
            .filter { now.isAfter(it.periodEnd.atTime(23, 59, 59)) }
            .maxByOrNull { it.periodEnd }
            ?.periodEnd?.atTime(23, 59, 59)

        schedule.lastGeneratedAt = now
        schedule.lastBudgetPeriodEnd = mostRecentEndedPeriod

        val nextPeriodEnd = adherence.categoryAdherences
            .filter { now.isBefore(it.periodEnd.atTime(23, 59, 59)) }
            .minByOrNull { it.periodEnd }
            ?.periodEnd?.atTime(23, 59, 59)
            ?: now.plusDays(30)

        schedule.nextScheduledAt = nextPeriodEnd
        recommendationScheduleRepository.save(schedule)
    }

    // Keep all the existing buildPrompt methods unchanged
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
        builder.appendLine("- If they overspent, gently inform them that they have underspent and suggest NBK offers to help reduce next month's spending. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights, and if you recommend them something in the form of investment or saving their money look up any account types NBK would support doing that")
        builder.appendLine("- If they underspent, inform them and praise them and recommend how to leverage NBK's offers. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights, and if you recommend them something in the form of investment or saving their money look up any account types NBK would support doing that")
        builder.appendLine("- If they are within budget, positively reinforce and suggest further optimization.")
        builder.appendLine("- Use a friendly, concise tone. Prioritize personalization and empathy.")
        builder.appendLine("- Use the following list of NBK offers if they are relevant.\n")
        builder.appendLine("- when mentioning money make sure to add KD before the number \"KD 17.89\"")
        builder.appendLine("- when you want to mention how much they overspent or underspent use percentages and thats it no using terms like out of")
        builder.appendLine("- Decision Logic:\n" +
                " • Overspent (PercentageUsed > 100):\n" +
                "   – Inform user of \"X% over budget.\"\n" +
                "   – Offer one tip to reduce next month's spending.\n" +
                "   – Suggest relevant NBK offer.\n" +
                " • Underspent (PercentageUsed < 50):\n" +
                "   – Praise user for \"X% under budget.\"\n" +
                "   – Offer one practical tip to stay on track.\n" +
                "   – Suggest relevant NBK offer and optionally mention NBK Savings or Fixed Deposit.\n" +
                "• On track (50 ≤ PercentageUsed ≤ 100):\n" +
                "   – Reinforce \"You're X% used.\"\n" +
                "   – Offer one optimization tip.\n" +
                "   – Suggest relevant NBK offer.")

        // Include only offers the user might care about
        offers.groupBy { it.mccCategoryId }.forEach { (mccId, offerGroup) ->
            val mcc = offerGroup.first().mccCategoryId
            val descriptions = offerGroup.joinToString(separator = "\n- ") { it.description }
            builder.appendLine("Offers for MCC ID $mcc:\n- $descriptions\n")
        }

        builder.appendLine("\nHere's the user's budget data and make sure when returning the Category variable they MUST be one of the following and make sure they are exactly the same its case sensitive if not then change it to what it should be here they are:")
        builder.appendLine("Dining (if you see DINING change it to this" +
                "Entertainment (if you see ENTERTAINMENT change it to this))" +
                "Shopping (if you see SHOPPING change it to this)" +
                "Food & Groceries (if you see FOOD_AND_GROCERIES change it to this)" +
                "Other (if you see OTHER change it to this)")
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
        builder.appendLine("You must write an extremely short, friendly, and informative message suggesting the best offers based on the user's interests and prior recommendations.")
        builder.appendLine("you must give some form of good insight for the user as well not just relevant offers")

        builder.appendLine()
        builder.appendLine("Instructions:")
        builder.appendLine("- Write a clear and concise message (1–2 sentences) summarizing which offers they should explore based on their spending and recommendations.")
        builder.appendLine("- DO NOT list every offer. Pick only the most relevant ones across all categories.")
        builder.appendLine("- Keep the tone helpful, positive, and brief.")
        builder.appendLine("- Do NOT mention IDs of offers in the description")
        builder.appendLine("- Only recommend actual NBK offers provided below.")
        builder.appendLine("- When referencing money, write values like this: 'KD 10'.")
        builder.appendLine("- Avoid using special characters, emojis, or HTML tags.")
        builder.appendLine("- Prioritize relevance over quantity — the goal is to highlight the *best-fit* offers. And try to pick one or two of each category if possible and does not return too many offers no more than 6-8 offers")
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
            appendLine("1. spendingComparedToLastMonth - a brief summary comparing this month's vs last month's total spending. Mention actual KD values (e.g., You spent KD 124 less than last month), and optionally percentage (e.g., 40% less). Do NOT use confusing expressions like '0.6 times less'. Avoid using 'times' language completely.")
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
            appendLine("Total Money In: ${thisMonth.moneyIn}")
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