package com.nbk.insights.dto


data class ChatRequest(
    val model: String = "gpt-4o",
    val messages: List<Message>,
    val temperature: Double = 0.7
)

data class CategoryRecommendationResponse(
    val category: String,
    val recommendation: String
)

data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

data class OffersRecommendationResponse(
    val message: String,
    val offers: List<OfferBrief>
)

data class OfferBrief(
    val id: Long?,
    val description: String,
    val subCategory: String?
)

data class OfferResponse(
    val id: Long? = null,
    val description: String
)

data class QuickInsightsResponse(
    val spendingComparedToLastMonth: String,
    val budgetLimitWarning: String,
    val savingInsights: String
)


