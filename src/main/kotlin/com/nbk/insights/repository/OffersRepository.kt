package com.nbk.insights.repository

import com.nbk.insights.dto.OfferBrief
import jakarta.persistence.*
import org.springframework.context.annotation.Description
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.Query


@Repository
interface OffersRepository : JpaRepository<OffersEntity, Long> {
    fun findById(id: Long?): OffersEntity

    @Query("""
    SELECT o FROM OffersEntity o 
    JOIN MccEntity m ON o.mccCategoryId = m.id 
    WHERE m.category = :category
""")
    fun findAllByMccCategory(@Param("category") category: String): List<OffersEntity>

    @Query("""
    SELECT o FROM OffersEntity o 
    JOIN MccEntity m ON o.mccCategoryId = m.id 
    WHERE m.category IN :categories
""")
    fun findAllByMccCategories(@Param("categories") categories: List<String>): List<OffersEntity>

    @Query("""
    SELECT new com.nbk.insights.dto.OfferBrief(
        o.id, o.description, m.subCategory
    ) 
    FROM OffersEntity o 
    JOIN MccEntity m ON o.mccCategoryId = m.id 
    WHERE m.category IN :categories
    """)
    fun findOfferBriefsByCategories(@Param("categories") categories: List<String>): List<OfferBrief>

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