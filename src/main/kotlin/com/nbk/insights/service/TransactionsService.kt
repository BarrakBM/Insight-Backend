package com.nbk.insights.service

import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.TransactionEntity
import com.nbk.insights.repository.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionsService(
    val transactionRepository: TransactionRepository
){

}