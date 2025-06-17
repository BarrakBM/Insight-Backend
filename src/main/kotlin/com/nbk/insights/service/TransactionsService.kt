    package com.nbk.insights.service

    import com.nbk.insights.dto.MCC
    import com.nbk.insights.dto.TransactionResponse
    import com.nbk.insights.repository.AccountRepository
    import com.nbk.insights.repository.MccRepository
    import com.nbk.insights.repository.TransactionRepository
    import org.springframework.http.HttpStatus
    import org.springframework.http.HttpStatusCode
    import org.springframework.http.ResponseEntity
    import org.springframework.stereotype.Service

    @Service
    class TransactionsService(
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val mccRepository: MccRepository
    ) {

        fun fetchUserTransactions(userId: Long?): ResponseEntity<Any> {

            // retrieve all accounts related to user from DB
            val accounts = accountRepository.findByUserId(userId)
            if (accounts.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "account was not found for userId $userId"))
            }

            // collect only their IDs
            val accountIds = accounts.mapNotNull { it.id }

            // retrieve all transactions relative to the account IDs whether as a source or destinations
            val transactions = transactionRepository
                .findAllBySourceAccountIdInOrDestinationAccountIdIn(accountIds, accountIds)

            // retrieve all MCCs relative to the transactions we retrieved
            val mccIds = transactions.mapNotNull { it.mccId }.toSet()
            val mccMap = mccRepository.findAllById(mccIds)
                .associateBy { it.id }

            // map the MCCs and transactions to our response DTOs
            val response = transactions.map { tx ->
                val mccEntity = tx.mccId?.let { mccMap[it] }
                val mccDto = MCC(
                    category    = mccEntity?.category ?: "unknown",
                    subCategory = mccEntity?.subCategory ?: "unknown"
                )

                TransactionResponse(
                    id                   = tx.id,
                    sourceAccountId      = tx.sourceAccountId,
                    destinationAccountId = tx.destinationAccountId,
                    amount               = tx.amount,
                    transactionType      = tx.transactionType,
                    mcc                  = mccDto,
                    createdAt            = tx.createdAt
                )
            }

            return ResponseEntity.ok(response)
        }

        fun fetchAccountTransactions(accountId: Long?, userId: Long?): ResponseEntity<Any> {

            // retrieving all accounts related to the user
            val userAccounts = accountRepository.findByUserId(userId)

            // check if accounts belongs to the user
            if (userAccounts.none { it.id == accountId }) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "account not found"))
            }

            // retrieve all transactions relative to the account ID
            val transactions = transactionRepository
                .findAllBySourceAccountIdOrDestinationAccountId(accountId, accountId)

            // retrieve all MCCs relative to the transactions we retrieved
            val mccIds = transactions.mapNotNull { it.mccId }.toSet()
            val mccMap = mccRepository.findAllById(mccIds)
                .associateBy { it.id }

            // map the MCCs and transactions to our response DTOs
            val response = transactions.map { tx ->
                val mccEntity = tx.mccId?.let { mccMap[it] }
                val mccDto = MCC(
                    category    = mccEntity?.category ?: "unknown",
                    subCategory = mccEntity?.subCategory ?: "unknown"
                )

                TransactionResponse(
                    id                   = tx.id,
                    sourceAccountId      = tx.sourceAccountId,
                    destinationAccountId = tx.destinationAccountId,
                    amount               = tx.amount,
                    transactionType      = tx.transactionType,
                    mcc                  = mccDto,
                    createdAt            = tx.createdAt
                )
            }

            return ResponseEntity.ok(response)
        }
    }
