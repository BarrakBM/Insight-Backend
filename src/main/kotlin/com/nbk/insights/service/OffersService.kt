package com.nbk.insights.service

import com.nbk.insights.dto.OfferDTO
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.OffersEntity
import com.nbk.insights.repository.OffersRepository
import org.springframework.stereotype.Service

@Service
class OffersService(
    val offersRepository: OffersRepository
){
    fun getOffersByCategory(category: String): List<OfferDTO> {
        val offers = offersRepository.findAllByMccCategory(category)
        return offers.map {
            OfferDTO(it.id!!, it.description)
        }
    }
}