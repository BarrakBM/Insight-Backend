package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.context.annotation.Description
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OffersRepository:JpaRepository<OffersEntity,Long>{
    fun findById(id: Long?): OffersEntity

}

@Entity
@Table(name = "offers")
data class OffersEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val mccCategoryId: Long,
    val description: String
){
    constructor(): this(0, 0, "")
}