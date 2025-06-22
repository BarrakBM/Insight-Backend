package com.nbk.insights.controller

import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.RecurringPaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/insights")

class InsightsController(
    private val recurringPaymentService: RecurringPaymentService,
    private val userRepository: UserRepository

) {

}