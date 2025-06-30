package com.nbk.insights.service

import com.nbk.insights.dto.OfferResponse
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.OffersEntity
import com.nbk.insights.repository.OffersRepository
import org.springframework.stereotype.Service

@Service
class OffersService(
    val offersRepository: OffersRepository
){
    fun getOffersByCategory(category: String): List<OfferResponse> {
        return offersRepository.findOfferResponsesByCategory(category)
    }
}