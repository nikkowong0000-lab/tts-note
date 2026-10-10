package com.example.ttsnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sectionId: Long = 0, // 0 for "Uncategorized" or "Default"
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPlayedParagraphIndex: Int = -1,
    val lastPlayedCharOffset: Int = 0,
    val lastScrollIndex: Int = 0,
    val speechRate: Float = 1.0f,
    val isPinned: Boolean = false,
    val aiSummary: String? = null,
    val aiTags: String? = null // Comma separated tags
)
