package com.nbk.insights.service

import com.nbk.insights.repository.LimitsRepository
import org.springframework.stereotype.Service

@Service
class LimitsService(
    val limitsRepository: LimitsRepository
){

}