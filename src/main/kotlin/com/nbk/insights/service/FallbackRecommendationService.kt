package com.nbk.insights.service

import com.nbk.insights.dto.*
import com.nbk.insights.repository.OffersEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import com.hazelcast.logging.Logger
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.OffersRepository
import java.time.LocalDateTime

private val logger = Logger.getLogger("fallback-recommendations")

@Service
class FallbackRecommendationService(
    private val mccRepository: MccRepository,
    private val offersRepository: OffersRepository
) {

    /**
     * Generates rule-based category recommendations without AI
     */
    fun generateCategoryRecommendations(
        adherences: List<CategoryAdherence>,
        offers: List<OffersEntity>
    ): List<CategoryRecommendationResponse> {
        logger.info("Generating fallback category recommendations")

        return adherences.map { adherence ->
            val recommendation = when {
                // Severely over budget (>120%)
                adherence.percentageUsed > 120.0 -> {
                    val overspentAmount = adherence.spentAmount - adherence.budgetAmount
                    val relevantOffer = findRelevantOffer(adherence.category, offers)

                    buildString {
                        append("You're ${adherence.percentageUsed.toInt()}% over budget. ")
                        append("Consider cutting back ${overspentAmount}KD next month. ")

                        if (relevantOffer != null) {
                            append("NBK offers ${relevantOffer.description} to help save.")
                        } else {
                            append("Review your ${adherence.category.lowercase()} expenses for savings opportunities.")
                        }
                    }
                }

                // Over budget (100-120%)
                adherence.percentageUsed > 100.0 -> {
                    buildString {
                        append("You exceeded your budget by ${(adherence.percentageUsed - 100).toInt()}%. ")
                        append("Try to reduce spending by ${adherence.spentAmount - adherence.budgetAmount}KD. ")

                        val tip = getCategoryTip(adherence.category, "overspent")
                        append(tip)
                    }
                }

                // Near limit (80-100%)
                adherence.percentageUsed > 80.0 -> {
                    buildString {
                        append("You've used ${adherence.percentageUsed.toInt()}% of your budget. ")
                        append("You have ${adherence.remainingAmount}KD left this period. ")
                        append("Stay mindful to finish within budget.")
                    }
                }

                // Good progress (50-80%)
                adherence.percentageUsed > 50.0 -> {
                    buildString {
                        append("Great progress! You've used ${adherence.percentageUsed.toInt()}% of your budget. ")
                        append("Keep up the balanced spending with ${adherence.remainingAmount}KD available.")
                    }
                }

                // Under budget (<50%)
                else -> {
                    val savedAmount = adherence.budgetAmount - adherence.spentAmount
                    buildString {
                        append("Excellent! Only ${adherence.percentageUsed.toInt()}% used. ")
                        append("You could save ${savedAmount}KD this month. ")
                        append("Consider NBK Savings Account for your surplus.")
                    }
                }
            }

            CategoryRecommendationResponse(
                category = adherence.category,
                recommendation = recommendation
            )
        }
    }

    /**
     * Generates rule-based offer recommendations
     */
    fun generateOffersRecommendation(
        categories: List<String>,
        offers: List<OfferBrief>
    ): OffersRecommendationResponse {
        logger.info("Generating fallback offer recommendations")

        if (offers.isEmpty()) {
            return OffersRecommendationResponse(
                message = "No offers available at the moment. Check back soon!",
                offers = emptyList()
            )
        }

        // Group offers by category and select top ones
        val topOffers = offers
            .groupBy { it.subCategory ?: "General" }
            .flatMap { it.value.take(1) } // Take 1 from each category
            .take(3) // Maximum 3 offers

        val message = when (topOffers.size) {
            0 -> "No personalized offers available right now."
            1 -> "Based on your spending, check out this NBK offer: ${topOffers[0].description}"
            2 -> "We found ${topOffers.size} NBK offers matching your spending patterns in ${categories.take(2).joinToString(" and ")}."
            else -> "Discover ${topOffers.size} personalized NBK offers across your top spending categories!"
        }

        return OffersRecommendationResponse(
            message = message,
            offers = topOffers
        )
    }

    /**
     * Generates rule-based quick insights
     */
    fun generateQuickInsights(
        thisMonth: CashFlowCategorizedResponse,
        lastMonth: CashFlowCategorizedResponse,
        adherence: BudgetAdherenceResponse
    ): QuickInsightsResponse {
        logger.info("Generating fallback quick insights")

        // Spending comparison
        val spendingDiff = thisMonth.moneyOut - lastMonth.moneyOut
        val spendingPercentChange = if (lastMonth.moneyOut > BigDecimal.ZERO) {
            ((spendingDiff / lastMonth.moneyOut) * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val spendingComparedToLastMonth = when {
            spendingDiff > BigDecimal.ZERO -> {
                "You spent ${spendingDiff.abs()}KD more this month (${spendingPercentChange.abs()}% increase)"
            }
            spendingDiff < BigDecimal.ZERO -> {
                "You spent ${spendingDiff.abs()}KD less this month (${spendingPercentChange.abs()}% decrease)"
            }
            else -> {
                "Your spending remained the same as last month at ${thisMonth.moneyOut}KD"
            }
        }

        // Budget warnings
        val criticalCategories = adherence.categoryAdherences
            .filter { it.adherenceLevel == AdherenceLevel.EXCEEDED || it.adherenceLevel == AdherenceLevel.CRITICAL }
            .take(2)

        val budgetLimitWarning = when {
            criticalCategories.isEmpty() -> {
                "Great job! All categories are within budget limits."
            }
            criticalCategories.size == 1 -> {
                val cat = criticalCategories[0]
                "${cat.category} is ${if (cat.percentageUsed > 100) "over" else "near"} budget at ${cat.percentageUsed.toInt()}%"
            }
            else -> {
                "${criticalCategories.size} categories need attention: ${criticalCategories.joinToString(", ") { it.category }}"
            }
        }

        // Savings insights
        val totalSurplus = adherence.categoryAdherences
            .filter { it.percentageUsed < 80 }
            .sumOf { it.remainingAmount }

        val netCashFlow = thisMonth.netCashFlow

        val savingInsights = when {
            netCashFlow > BigDecimal.ZERO && totalSurplus > BigDecimal.ZERO -> {
                "You have ${netCashFlow}KD net surplus and ${totalSurplus}KD unspent budget. Great savings opportunity!"
            }
            totalSurplus > BigDecimal(100) -> {
                "You have ${totalSurplus}KD available across underspent categories. Consider increasing savings."
            }
            netCashFlow < BigDecimal.ZERO -> {
                "You're spending more than earning. Review your largest expense categories."
            }
            else -> {
                "Maintain your current balanced approach between spending and saving."
            }
        }

        return QuickInsightsResponse(
            spendingComparedToLastMonth = spendingComparedToLastMonth,
            budgetLimitWarning = budgetLimitWarning,
            savingInsights = savingInsights
        )
    }

    private fun findRelevantOffer(category: String, offers: List<OffersEntity>): OffersEntity? {
        return offers.firstOrNull { offer ->
            val mcc = mccRepository.findById(offer.mccCategoryId).orElse(null)
            mcc?.category == category
        }
    }

    private fun getCategoryTip(category: String, situation: String): String {
        val tips = mapOf(
            "DINING" to mapOf(
                "overspent" to "Consider cooking at home more often or use NBK dining offers.",
                "underspent" to "Great control on dining expenses!"
            ),
            "SHOPPING" to mapOf(
                "overspent" to "Make a list before shopping and look for NBK retail partner discounts.",
                "underspent" to "Smart shopping habits! Keep it up."
            ),
            "ENTERTAINMENT" to mapOf(
                "overspent" to "Look for free events or NBK entertainment partner offers.",
                "underspent" to "Well balanced entertainment budget!"
            ),
            "TRANSPORT" to mapOf(
                "overspent" to "Consider carpooling or public transport options.",
                "underspent" to "Efficient transport spending!"
            )
        )

        return tips[category]?.get(situation)
            ?: when (situation) {
                "overspent" -> "Review your expenses and look for areas to cut back."
                else -> "Keep monitoring your spending patterns."
            }
    }
}