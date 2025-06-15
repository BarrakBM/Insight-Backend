package com.nbk.insights.service

import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.OffersEntity
import org.springframework.stereotype.Service

@Service
class OffersService(
    val offersRepository: AccountRepository
){

}