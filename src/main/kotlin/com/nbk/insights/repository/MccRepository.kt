package com.nbk.insights.repository


import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MccRepository:JpaRepository<MccEntity,Long>{
    fun findById(id: Long?): MccEntity

}

@Entity
@Table(name = "mcc")
data class MccEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val code: String,
    val category: String,
    val subCategory: String?
    ){
    constructor(): this(0, "", "", "")
}