package com.nbk.insights.repository


import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.security.Principal

@Repository
interface AccountRepository:JpaRepository<AccountEntity,Long>{
    fun findById(id: Long?): AccountEntity
<<<<<<< feature-retrieve-transactions
    fun findByUserId(userId: Long?): List<AccountEntity>
=======
    fun findAllByUserId(userId: Long): List<AccountEntity>
>>>>>>> main

}

@Entity
@Table(name = "accounts")
data class AccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Enumerated(EnumType.STRING)
    val accountType: AccountType,
    val userId: Long,
    val accountNumber: String? = "AN"+(10000000..99999999).random().toString(),
    var balance: BigDecimal,
    val cardNumber: String? = "CN"+(10000000..99999999).random().toString()
){
    constructor(): this(id = null, accountType= AccountType.MAIN, userId = 0, accountNumber = "", balance = BigDecimal.ZERO, cardNumber = "")
}

enum class AccountType{
    MAIN, SAVINGS
}