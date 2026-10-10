package com.example.ttsnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ttsnote.data.Note
import com.example.ttsnote.data.NoteRepository
import com.example.ttsnote.data.PreferenceManager
import com.example.ttsnote.data.Section
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModel(
    private val repository: NoteRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSectionId = MutableStateFlow<Long?>(null) // null for "All" or "Default"
    val selectedSectionId: StateFlow<Long?> = _selectedSectionId.asStateFlow()

    val globalFontSize: StateFlow<Int> = preferenceManager.globalFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18)

    val themeMode: StateFlow<ThemeMode> = preferenceManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.Auto)

    val language: StateFlow<String> = preferenceManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.ttsnote.LocaleHelper.LANG_EN)

    val allSections: StateFlow<List<Section>> = repository.allSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: Flow<List<Note>> = combine(_searchQuery, _selectedSectionId) { query, sectionId ->
        query to sectionId
    }.flatMapLatest { (query, sectionId) ->
        if (query.isNotEmpty()) {
            repository.searchNotes(query)
        } else if (sectionId != null) {
            repository.getNotesBySection(sectionId)
        } else {
            repository.allNotes
        }
    }

    fun selectSection(id: Long?) {
        _selectedSectionId.value = id
    }

    fun updateFontSize(size: Int) = viewModelScope.launch {
        preferenceManager.updateFontSize(size)
    }

    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch {
        preferenceManager.updateThemeMode(mode)
    }

    fun updateLanguage(language: String) = viewModelScope.launch {
        preferenceManager.updateLanguage(language)
    }

    fun handleAutoTitle(note: Note, content: String) {
        val untitledEn = "Untitled note"
        val untitledZh = "無標題筆記"
        if (note.title.startsWith(untitledEn) || note.title.startsWith(untitledZh) || note.title.isEmpty()) {
            val detectedTitle = com.example.ttsnote.tts.SemanticParser.findTitle(content)
            if (detectedTitle != null && detectedTitle != note.title) {
                updateNote(note.copy(title = detectedTitle))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun insertNote(note: Note) = viewModelScope.launch {
        repository.insertNote(note)
    }

    fun updateNote(note: Note) = viewModelScope.launch {
        repository.updateNote(note)
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        repository.deleteNote(note)
    }

    suspend fun getNoteById(id: Long): Note? {
        return repository.getNoteById(id)
    }

    // Section actions
    fun insertSection(name: String) = viewModelScope.launch {
        repository.insertSection(Section(name = name))
    }

    fun updateSection(section: Section) = viewModelScope.launch {
        repository.updateSection(section)
    }

    fun deleteSection(section: Section) = viewModelScope.launch {
        repository.deleteSection(section)
    }
}

class NoteViewModelFactory(
    private val repository: NoteRepository,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
