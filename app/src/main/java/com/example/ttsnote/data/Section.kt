package com.example.ttsnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sections")
data class Section(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = 0, // Color Int for visual differentiation
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
