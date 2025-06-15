package com.nbk.insights.service

import com.nbk.insights.repository.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(
    val accountRepository: AccountRepository
){

}