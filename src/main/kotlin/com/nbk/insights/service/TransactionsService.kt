package com.nbk.insights.service

import com.hazelcast.logging.Logger
import com.nbk.insights.dto.CashFlowCategorizedResponse
import com.nbk.insights.dto.MCC
import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.helper.buildCacheKey
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.TransactionRepository
import com.nbk.insights.repository.TransactionType
import com.nbk.insights.serverInsightsCache
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private val loggerUserTransaction = Logger.getLogger("transactions-user")
private val loggerAccountTransaction = Logger.getLogger("transactions-account")

@Service
class TransactionsService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val mccRepository: MccRepository,
    private val accountService: AccountService
) {

    fun fetchUserTransactions(
        userId: Long?,
        mccId: Long?,
        period: String,
        category: String?,
        year: Int?,
        month: Int?
    ): List<TransactionResponse> {

        val transactionCache = serverInsightsCache.getMap<String, List<TransactionResponse>>("transactions-user")

        val cacheKey = buildCacheKey(userId, category, mccId, period, year, month)

        transactionCache[cacheKey]?.let {
            loggerUserTransaction.info("Returning cached user transactions for key=$cacheKey")
            return it
        }

        val accounts = accountRepository.findByUserId(userId)
        if (accounts.isEmpty()) {
            throw EntityNotFoundException("No accounts found for userId: $userId")
        }

        val accountIds = accounts.mapNotNull { it.id }

        val now = LocalDateTime.now()
        val startDate = when {
            year != null && month != null -> LocalDateTime.of(year, month, 1, 0, 0)
            year != null && month == null -> LocalDateTime.of(year, 1, 1, 0, 0)
            period.equals("monthly", ignoreCase = true) -> now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            period.equals("yearly", ignoreCase = true) -> now.withDayOfYear(1).toLocalDate().atStartOfDay()
            period.equals("none", ignoreCase = true) -> null
            else -> throw IllegalArgumentException("Invalid period: $period")
        }

        val endDate = when {
            year != null && month != null -> startDate?.plusMonths(1)?.minusSeconds(1)
            year != null && month == null -> startDate?.plusYears(1)?.minusSeconds(1)
            else -> null
        }

        val transactions = transactionRepository.findFilteredUserTransactionsInRange(
            accountIds = accountIds,
            category = category,
            mccId = mccId,
            startDate = startDate,
            endDate = endDate
        )

        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        val response = transactions.map { tx ->
            val mccEntity = tx.mccId?.let { mccMap[it] }
            TransactionResponse(
                id = tx.id,
                sourceAccountId = tx.sourceAccountId,
                destinationAccountId = tx.destinationAccountId,
                amount = tx.amount,
                transactionType = tx.transactionType,
                mcc = MCC(
                    category = mccEntity?.category ?: "Transfer",
                    subCategory = mccEntity?.subCategory ?: "Account"
                ),
                createdAt = tx.createdAt
            )
        }

        loggerUserTransaction.info("No transaction(s) found, caching new data...")
        transactionCache[cacheKey] = response
        return response
    }


    fun fetchAccountTransactions(
        accountId: Long?,
        userId: Long?,
        mccId: Long?,
        period: String,
        category: String?,
        year: Int?,
        month: Int?
    ): List<TransactionResponse> {

        val transactionCache = serverInsightsCache.getMap<String, List<TransactionResponse>>("transactions-account")

        val cacheKey = buildCacheKey(accountId, category, mccId, period, year, month)

        transactionCache[cacheKey]?.let {
            loggerAccountTransaction.info("Returning cached transactions for key=$cacheKey")
            return it
        }

        val userAccounts = accountRepository.findByUserId(userId)
        if (userAccounts.none { it.id == accountId }) {
            throw IllegalArgumentException("Account $accountId not found or does not belong to the user")
        }

        val now = LocalDateTime.now()
        val startDate = when {
            year != null && month != null -> LocalDateTime.of(year, month, 1, 0, 0)
            year != null && month == null -> LocalDateTime.of(year, 1, 1, 0, 0)
            period.equals("monthly", ignoreCase = true) -> now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            period.equals("yearly", ignoreCase = true) -> now.withDayOfYear(1).toLocalDate().atStartOfDay()
            period.equals("none", ignoreCase = true) -> null
            else -> throw IllegalArgumentException("Invalid period: $period")
        }

        val endDate = when {
            year != null && month != null -> startDate?.plusMonths(1)?.minusSeconds(1)
            year != null && month == null -> startDate?.plusYears(1)?.minusSeconds(1)
            else -> null
        }

        val transactions = transactionRepository.findFilteredTransactionsInRange(
            accountId = accountId,
            category = category,
            mccId = mccId,
            startDate = startDate,
            endDate = endDate
        )

        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        val response = transactions.map { tx ->
            val mccEntity = tx.mccId?.let { mccMap[it] }
            TransactionResponse(
                id = tx.id,
                sourceAccountId = tx.sourceAccountId,
                destinationAccountId = tx.destinationAccountId,
                amount = tx.amount,
                transactionType = tx.transactionType,
                mcc = MCC(
                    category = mccEntity?.category ?: "Transfer",
                    subCategory = mccEntity?.subCategory ?: "Account"
                ),
                createdAt = tx.createdAt
            )
        }

        loggerAccountTransaction.info("No transaction(s) found, caching new data...")
        transactionCache[cacheKey] = response
        return response
    }


    // Last month
    /**
     * Calculates cash flow for the previous month
     * For example, if today is June 25th, it returns cash flow for May 1st to May 31st
     * @param userId User ID
     * @return Categorized cash flow for the previous month
     */
    fun getUserPreviousMonthCashFlow(
        userId: Long,
    ): CashFlowCategorizedResponse {
        // TODO: here we assume each user has two accounts
        val mainAccountId = accountService.retrieveUserAccounts(userId).Accounts[0]?.accountId
            ?: throw IllegalStateException("No account found: User must have two accounts")
        val savingsAccountId =
            accountService.retrieveUserAccounts(userId).Accounts[1]?.accountId
                ?: throw IllegalStateException("No account: User must have two accounts")

        val now = LocalDateTime.now()
        val previousMonth = now.minusMonths(1)
        val year = previousMonth.year
        val month = previousMonth.monthValue

        // Fetch transactions for the specific month using year and month parameters
        val transactions = fetchUserTransactions(
            userId = userId,
            mccId = null,
            period = "none", // We're using year/month params, so period should be "none"
            category = null,
            year = year,
            month = month
        )

        // Calculate the date range for the previous month
        val firstDayOfPreviousMonth = LocalDate.of(year, month, 1)
        val lastDayOfPreviousMonth = firstDayOfPreviousMonth.plusMonths(1).minusDays(1)

        return calculateCashFlowWithCategories(
            mainAccountId = mainAccountId,
            savingsAccountId = savingsAccountId,
            transactions = transactions,
            from = firstDayOfPreviousMonth,
            to = lastDayOfPreviousMonth
        )
    }

    // This month
    fun getUserCurrentMonthCashFlow(
        userId: Long,
    ): CashFlowCategorizedResponse {

        // TODO: here we assume each user has two accounts
        val mainAccountId = accountService.retrieveUserAccounts(userId).Accounts[0]?.accountId
            ?: throw IllegalStateException("No account found: User must have two accounts")
        val savingsAccountId =
            accountService.retrieveUserAccounts(userId).Accounts[1]?.accountId
                ?: throw IllegalStateException("No account: User must have two accounts")

        val today = LocalDate.now()
        val firstDayOfCurrentMonth = today.withDayOfMonth(1)
        val currentYear = today.year
        val currentMonth = today.monthValue

        // Fetch transactions for the current month using year and month parameters
        val transactions = fetchUserTransactions(
            userId = userId,
            mccId = null,
            period = "none", // Using year/month params instead
            category = null,
            year = currentYear,
            month = currentMonth
        )

        // Filter transactions to only include those up to and including today
        val filteredTransactions = transactions.filter { transaction ->
            val transactionDate = transaction.createdAt.toLocalDate()
            !transactionDate.isBefore(firstDayOfCurrentMonth) && !transactionDate.isAfter(today)
        }

        return calculateCashFlowWithCategories(
            mainAccountId = mainAccountId,
            savingsAccountId = savingsAccountId,
            transactions = filteredTransactions,
            from = firstDayOfCurrentMonth,
            to = today
        )
    }

    private fun calculateCashFlowWithCategories(
        mainAccountId: Long,
        savingsAccountId: Long,
        transactions: List<TransactionResponse>,
        from: LocalDate,
        to: LocalDate
    ): CashFlowCategorizedResponse {
        var moneyIn = BigDecimal.ZERO
        var moneyOut = BigDecimal.ZERO
        val moneyInByCategory = mutableMapOf<String, BigDecimal>()
        val moneyOutByCategory = mutableMapOf<String, BigDecimal>()

        val userAccountIds = setOf(mainAccountId, savingsAccountId)

        transactions.forEach { transaction ->
            val category = transaction.mcc.category

            when (transaction.transactionType) {
                TransactionType.CREDIT -> {
                    moneyIn = moneyIn.add(transaction.amount)
                    moneyInByCategory[category] = moneyInByCategory.getOrDefault(category, BigDecimal.ZERO)
                        .add(transaction.amount)
                }

                TransactionType.DEBIT -> {
                    moneyOut = moneyOut.add(transaction.amount)
                    moneyOutByCategory[category] = moneyOutByCategory.getOrDefault(category, BigDecimal.ZERO)
                        .add(transaction.amount)
                }

                TransactionType.TRANSFER -> {
                    val isSourceUserAccount = transaction.sourceAccountId in userAccountIds
                    val isDestinationUserAccount = transaction.destinationAccountId in userAccountIds

                    when {
                        // skip internal transfers
                        isSourceUserAccount && isDestinationUserAccount -> {
                            // do nothing
                        }

                        isSourceUserAccount -> {
                            moneyOut = moneyOut.add(transaction.amount)
                            moneyOutByCategory[category] = moneyOutByCategory.getOrDefault(category, BigDecimal.ZERO)
                                .add(transaction.amount)
                        }

                        isDestinationUserAccount -> {
                            moneyIn = moneyIn.add(transaction.amount)
                            moneyInByCategory[category] = moneyInByCategory.getOrDefault(category, BigDecimal.ZERO)
                                .add(transaction.amount)
                        }
                    }
                }

                else -> {
                }
            }
        }

        return CashFlowCategorizedResponse(
            moneyIn = moneyIn,
            moneyOut = moneyOut,
            moneyInByCategory = moneyInByCategory,
            moneyOutByCategory = moneyOutByCategory,
            from = from,
            to = to
        )
    }

    // Alternative
    /**
     * Calculates cash flow for the last 30 days from today
     * @param userId User ID
     * @return Categorized cash flow for the last 30 days
     */
    fun getUserLast30DaysCashFlow(
        userId: Long,
        mainAccountId: Long,
        savingsAccountId: Long
    ): CashFlowCategorizedResponse {
        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)

        // Fetch all transactions using "none" period to get all transactions
        val transactions = fetchUserTransactions(
            userId = userId,
            mccId = null,
            period = "none",
            category = null,
            year = null,
            month = null
        )

        // Filter transactions within the last 30 days
        val filteredTransactions = transactions.filter { transaction ->
            val transactionDate = transaction.createdAt.toLocalDate()
            !transactionDate.isBefore(thirtyDaysAgo) && !transactionDate.isAfter(today)
        }

        return calculateCashFlowWithCategories(
            mainAccountId = mainAccountId,
            savingsAccountId = savingsAccountId,
            transactions = filteredTransactions,
            from = thirtyDaysAgo,
            to = today
        )
    }

}