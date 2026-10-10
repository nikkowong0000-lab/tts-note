package com.example.ttsnote.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    // Notes
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    
    fun getNotesBySection(sectionId: Long): Flow<List<Note>> = noteDao.getNotesBySection(sectionId)

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    // Sections
    val allSections: Flow<List<Section>> = noteDao.getAllSections()
    
    suspend fun getSectionById(id: Long): Section? = noteDao.getSectionById(id)

    suspend fun insertSection(section: Section): Long = noteDao.insertSection(section)

    suspend fun updateSection(section: Section) = noteDao.updateSection(section)

    suspend fun deleteSection(section: Section) = noteDao.deleteSection(section)
}
