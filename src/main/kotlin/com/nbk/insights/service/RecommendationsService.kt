package com.nbk.insights.service

import com.nbk.insights.dto.BudgetAdherenceResponse
import com.nbk.insights.dto.CategoryAdherence
import com.nbk.insights.dto.ChatRequest
import com.nbk.insights.dto.ChatResponse
import com.nbk.insights.dto.Message
import com.nbk.insights.repository.OffersEntity
import com.nbk.insights.repository.OffersRepository
import com.nbk.insights.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.lang.RuntimeException

@Service
class RecommendationsService(
    private val openAIWebClient: WebClient,
    private val limitsService: LimitsService,
    private val userRepository: UserRepository,
    private val offersRepository: OffersRepository
) {

    fun getCategoryRecommendations(userId: Long): String {
        val budgetAdherence = limitsService.checkBudgetAdherence(userId)

        // Extract category names from user's limits
        val categories = budgetAdherence.categoryAdherences.map { it.category }.distinct()

        // Fetch only the relevant offers
        val relevantOffers = offersRepository.findAllByMccCategories(categories)

        // Build prompt
        val prompt = buildFullPrompt(budgetAdherence.categoryAdherences, relevantOffers)

        // Call OpenAI
        val request = ChatRequest(
            model = "gpt-4o-mini",
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

        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("ChatGPT returned no valid content")
    }

    private fun buildFullPrompt(
        adherences: List<CategoryAdherence>,
        offers: List<OffersEntity>
    ): String {
        val builder = StringBuilder()
        builder.appendLine("You are a budgeting assistant for National Bank of Kuwait (NBK).")
        builder.appendLine("A user wants category-wise recommendations based on their monthly spending and budget adherence.")
        builder.appendLine("You also have access to exclusive NBK offers related to each spending category.")
        builder.appendLine("Instructions:")
        builder.appendLine("- Use the data for each category.")
        builder.appendLine("- Give JSON output: category as key, short friendly recommendation as value.")
        builder.appendLine("- If they overspent, gently inform them and suggest NBK offers to help reduce next month’s spending. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights")
        builder.appendLine("- If they underspent significantly, praise them and recommend how to leverage NBK’s offers. Do not just recommend to improve spending by offers only, give them generally good ways for them to stay within budget based on their insights")
        builder.appendLine("- If they are within budget, positively reinforce and suggest further optimization.")
        builder.appendLine("- Use a friendly, concise tone. Prioritize personalization and empathy. Try to add emojis but nothing controversial or inappropriate in arabic culture")
        builder.appendLine("- Use the following list of NBK offers if they are relevant.\n")

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
}

