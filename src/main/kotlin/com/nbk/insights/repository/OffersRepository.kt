package com.nbk.insights.repository

import com.nbk.insights.dto.OfferBrief
import com.nbk.insights.dto.OfferResponse
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

    // UPDATE THIS QUERY to include imageUrl
    @Query("""
    SELECT new com.nbk.insights.dto.OfferBrief(
        o.id, o.description, m.subCategory, o.imageUrl
    ) 
    FROM OffersEntity o 
    JOIN MccEntity m ON o.mccCategoryId = m.id 
    WHERE m.category IN :categories
    """)
    fun findOfferBriefsByCategories(@Param("categories") categories: List<String>): List<OfferBrief>

    // ADD THIS NEW QUERY for getting offers with subcategory
    @Query("""
    SELECT new com.nbk.insights.dto.OfferResponse(
        o.id, o.description, m.subCategory, o.imageUrl
    ) 
    FROM OffersEntity o 
    JOIN MccEntity m ON o.mccCategoryId = m.id 
    WHERE m.category = :category
    """)
    fun findOfferResponsesByCategory(@Param("category") category: String): List<OfferResponse>
}

@Entity
@Table(name = "offers")
data class OffersEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val mccCategoryId: Long,
    val description: String,
    val imageUrl: String? = null  // ADD THIS
){
    constructor(): this(0, 0, "", null)
}