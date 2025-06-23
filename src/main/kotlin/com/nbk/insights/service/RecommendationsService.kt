package com.nbk.insights.service

import com.nbk.insights.dto.BudgetAdherenceResponse
import com.nbk.insights.dto.CategoryAdherence
import com.nbk.insights.dto.ChatRequest
import com.nbk.insights.dto.ChatResponse
import com.nbk.insights.dto.Message
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
    private val userRepository: UserRepository
) {

    fun getCategoryRecommendations(userId: Long): String {

        // Get full adherence report
        val budgetAdherence: BudgetAdherenceResponse = limitsService.checkBudgetAdherence(userId)

        // Build rich prompt from all CategoryAdherence data
        val prompt = buildFullPrompt(budgetAdherence.categoryAdherences)

        // Call OpenAI API
        val request = ChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                Message("system", "You are an assistant providing budgeting recommendations tailored to user's spending habits and available offers at NBK"),
                Message("user", prompt)
            ),
            temperature = 0.7)

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

    private fun buildFullPrompt(adherences: List<CategoryAdherence>): String {
        val builder = StringBuilder()
        builder.appendLine("You are a budgeting assistant for National Bank of Kuwait (NBK).")
        builder.appendLine("A user wants category-wise recommendations based on their monthly spending and budget adherence.")
        builder.appendLine("Instructions:")
        builder.appendLine("- Use the data for each category.")
        builder.appendLine("- Give JSON output: category as key, short friendly recommendation as value.")
        builder.appendLine("- Mention NBK offers or suggest practical ways to save if overspending.")
        builder.appendLine("- If they overspent, gently inform them, suggest how to improve next month, and encourage usage of specific NBK offers to save more.\n")
        builder.appendLine("- If they underspent significantly, positively reinforce their good financial management, and suggest how they can benefit even further from NBK's offers.\n")
        builder.appendLine("- if they are within budget, congratulate them positively for following their budget")
        builder.appendLine("- Friendly, concise, supportive, consider the user's needs and budget. And see if there are relevant offers from NBK related to their budget category type and give it to them as recommendations.")
        builder.appendLine("- Do not only give them recommendations related to NBK offers, also give them general recommendations on how someone can lessen overspending and underspending based on the severity")
        builder.appendLine("Here’s the user's data:")

        adherences.forEach {
            builder.appendLine("""
            {
              "Category": "${it.category}",
              "BudgetAmount": "${it.budgetAmount} KWD",
              "SpentAmount": "${it.spentAmount} KWD",
              "PercentageUsed": "${String.format("%.2f", it.percentageUsed)}%",
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
              "SpendingChangePercentage": "${String.format("%.2f", it.spendingChangePercentage)}%"
            },
            """.trimIndent())
        }

        builder.appendLine()
        builder.appendLine("Now reply in a JSON array format. Each element should have:")
        builder.appendLine("- category: the name of the spending category")
        builder.appendLine("- recommendation: a helpful, friendly budgeting tip (with NBK offers if possible)")
        builder.appendLine("Example:")
        builder.appendLine("""
        [
          {
            "category": "Groceries",
            "recommendation": "Use NBK’s grocery cashback offers and plan weekly meals to reduce waste."
          }
        ]
        """.trimIndent())

        return builder.toString()
    }
}
