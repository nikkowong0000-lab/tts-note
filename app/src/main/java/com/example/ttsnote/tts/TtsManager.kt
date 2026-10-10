package com.example.ttsnote.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _currentSentence = MutableStateFlow("")
    val currentSentence: StateFlow<String> = _currentSentence.asStateFlow()

    private val _currentSentenceRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val currentSentenceRange: StateFlow<Pair<Int, Int>?> = _currentSentenceRange.asStateFlow()

    private val _activeBlockIndex = MutableStateFlow(-1)
    val activeBlockIndex: StateFlow<Int> = _activeBlockIndex.asStateFlow()

    private var currentText: String = ""
    private var semanticBlocks: List<SemanticBlock> = emptyList()
    private var internalBlockIndex = 0
    var currentSentenceInBlockStart = 0
    private var charOffset = 0

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.TAIWAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.CHINESE
                }
                isInitialized = true
                setupListener()
            } else {
                Log.e("TtsManager", "Initialization failed")
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
                if (utteranceId?.startsWith("BLOCK_") == true) {
                    val parts = utteranceId.substringAfter("BLOCK_").split("_")
                    val blockIndex = parts[0].toIntOrNull() ?: 0
                    internalBlockIndex = blockIndex
                    _activeBlockIndex.value = blockIndex
                    
                    val sStart = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    currentSentenceInBlockStart = sStart
                    
                    val block = semanticBlocks.getOrNull(blockIndex)
                    charOffset = block?.startIndex ?: 0
                    
                    if (parts.size > 2) {
                        val sEnd = parts[2].toIntOrNull() ?: 0
                        _currentSentenceRange.value = (charOffset + sStart) to (charOffset + sEnd)
                        
                        block?.content?.let { content ->
                            val sentence = if (sStart < sEnd && sEnd <= content.length) {
                                content.substring(sStart, sEnd).trim()
                            } else ""
                            if (sentence.length > 1) {
                                _currentSentence.value = sentence
                            }
                        }
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("BLOCK_") == true) {
                    val index = utteranceId.substringAfter("BLOCK_").split("_")[0].toIntOrNull() ?: 0
                    if (index == semanticBlocks.size - 1) {
                        _isPlaying.value = false
                        _currentPosition.value = currentText.length
                        _currentSentence.value = ""
                        _currentSentenceRange.value = null
                        _activeBlockIndex.value = -1
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
                _currentSentence.value = ""
                _currentSentenceRange.value = null
                _activeBlockIndex.value = -1
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                _currentPosition.value = charOffset + currentSentenceInBlockStart + start
            }
        })
    }

    fun speak(text: String, startBlockIndex: Int = 0, charOffsetInBlock: Int = 0) {
        if (!isInitialized || text.isBlank()) return
        
        currentText = text
        semanticBlocks = SemanticParser.parse(text)
        internalBlockIndex = startBlockIndex.coerceIn(0, semanticBlocks.size - 1)
        _activeBlockIndex.value = internalBlockIndex
        _isPlaying.value = true
        
        tts?.stop()

        for (i in internalBlockIndex until semanticBlocks.size) {
            val block = semanticBlocks[i]
            val blockText = block.content
            
            val sentences = splitIntoSentences(blockText)
            var internalOffset = 0
            
            for (sentence in sentences) {
                val sStart = internalOffset
                val sEnd = sStart + sentence.length
                internalOffset = sEnd
                
                if (i == internalBlockIndex && sEnd <= charOffsetInBlock) continue
                
                // Simple Language Detection
                val detectedLocale = detectLanguage(sentence)
                tts?.setLanguage(detectedLocale)
                
                val utteranceId = "BLOCK_${i}_${sStart}_$sEnd"
                val params = android.os.Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                
                val speechText = sanitizeText(sentence)
                if (speechText.isNotBlank()) {
                    tts?.speak(speechText, TextToSpeech.QUEUE_ADD, params, utteranceId)
                    tts?.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, null)
                }
            }
            tts?.playSilentUtterance(if (block.type == BlockType.Header) 800L else 500L, TextToSpeech.QUEUE_ADD, null)
        }
    }

    fun resumePlayback() {
        if (currentText.isNotEmpty()) {
            speak(currentText, internalBlockIndex, currentSentenceInBlockStart)
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val delimiters = "(?<=[。！？.!?\\n])"
        return text.split(Regex(delimiters)).filter { it.isNotBlank() }
    }

    private fun detectLanguage(text: String): Locale {
        val cjkCount = text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF }
        val latinCount = text.count { it.lowercaseChar() in 'a'..'z' }
        
        return when {
            cjkCount > latinCount -> Locale.TAIWAN
            latinCount > 0 -> Locale.US
            else -> Locale.getDefault()
        }
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    private fun sanitizeText(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), "") // Remove URLs completely
            .replace(Regex("(?m)^#+\\s+"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("(?m)^>\\s*"), "")
            .replace(Regex("(?m)^[-*]\\s+"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("`"), "")
            .replace("*", "") // Remove any remaining asterisks
    }

    fun nextParagraph() {
        if (internalBlockIndex < semanticBlocks.size - 1) {
            speak(currentText, internalBlockIndex + 1)
        }
    }

    fun previousParagraph() {
        if (internalBlockIndex > 0) {
            speak(currentText, internalBlockIndex - 1)
        }
    }

    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun getVoices(): List<android.speech.tts.Voice> {
        val allVoices = tts?.voices?.toList() ?: emptyList()
        return allVoices.sortedWith(compareByDescending<android.speech.tts.Voice> { 
            it.quality >= android.speech.tts.Voice.QUALITY_HIGH 
        }.thenBy { it.isNetworkConnectionRequired })
    }

    fun setVoice(voice: android.speech.tts.Voice) {
        tts?.voice = voice
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        _currentPosition.value = 0
        _currentSentence.value = ""
        _activeBlockIndex.value = -1
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun getBlockIndexForOffset(offset: Int): Int {
        return semanticBlocks.indexOfFirst { offset >= it.startIndex && offset < it.endIndex }
    }

    fun getCharOffsetInBlock(offset: Int, blockIndex: Int): Int {
        val block = semanticBlocks.getOrNull(blockIndex) ?: return 0
        return (offset - block.startIndex).coerceAtLeast(0)
    }
}
