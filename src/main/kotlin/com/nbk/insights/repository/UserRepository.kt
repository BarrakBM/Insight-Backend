package com.nbk.insights.repository

import jakarta.inject.Named
import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Named
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByUsername(username: String): UserEntity?
}

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var username: String,
    var password: String,
    var fullName: String,
    var fcm: String? = null

    ){
    constructor() : this(null, "", "", "", "")
}