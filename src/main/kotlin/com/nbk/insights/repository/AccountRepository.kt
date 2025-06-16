package com.nbk.insights.repository


import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface AccountRepository:JpaRepository<AccountEntity,Long>{
    fun findById(id: Long?): AccountEntity

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
    val accountNumber: String = "AN"+(10000000..99999999).random().toString(),
    val cardNumber: String = "CN"+(10000000..99999999).random().toString()
){
    constructor(): this(0, AccountType.MAIN, 0, "", "")
}

enum class AccountType{
    MAIN, SAVINGS
}