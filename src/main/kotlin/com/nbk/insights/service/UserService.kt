package com.nbk.insights.service

import com.nbk.insights.repository.UserEntity
import com.nbk.insights.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository) {
    fun create(user: UserEntity): UserEntity {
        return userRepository.save(user)
    }

    fun getAll(): List<UserEntity> {
        return userRepository.findAll()
    }
}