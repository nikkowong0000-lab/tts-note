package com.example.ttsnote

import android.app.Application
import android.content.Context
import com.example.ttsnote.data.NoteDatabase
import com.example.ttsnote.data.NoteRepository
import com.example.ttsnote.data.PreferenceManager

class TTSNoteApplication : Application() {
    val database by lazy { NoteDatabase.getDatabase(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }
    val preferenceManager by lazy { PreferenceManager(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }
}
