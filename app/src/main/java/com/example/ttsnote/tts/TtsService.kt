package com.example.ttsnote.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.example.ttsnote.MainActivity
import com.example.ttsnote.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

class TtsService : Service() {

    private lateinit var ttsManager: TtsManager
    private var mediaSession: MediaSessionCompat? = null
    private val binder = TtsBinder()
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    val currentPosition: StateFlow<Int> get() = ttsManager.currentPosition
    val isPlaying: StateFlow<Boolean> get() = ttsManager.isPlaying
    val currentSentence: StateFlow<String> get() = ttsManager.currentSentence
    val currentSentenceRange: StateFlow<Pair<Int, Int>?> get() = ttsManager.currentSentenceRange
    val activeBlockIndex: StateFlow<Int> get() = ttsManager.activeBlockIndex
    val currentSentenceInBlockStart: Int get() = ttsManager.currentSentenceInBlockStart
    val currentBlockIndex: Int get() = ttsManager.getBlockIndexForOffset(ttsManager.currentPosition.value)

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                stopPlaying()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(this)
        createNotificationChannel()
        
        setupMediaSession()

        serviceScope.launch {
            ttsManager.currentSentence.collect { sentence ->
                if (sentence.isNotBlank()) {
                    updateNotification(sentence)
                }
            }
        }
        
        serviceScope.launch {
            ttsManager.isPlaying.collect { playing ->
                updatePlaybackState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "EchoNotesSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    ttsManager.resumePlayback()
                }
                override fun onPause() {
                    ttsManager.pause()
                }
                override fun onStop() {
                    stopPlaying()
                }
                override fun onSkipToNext() {
                    nextParagraph()
                }
                override fun onSkipToPrevious() {
                    previousParagraph()
                }
            })
            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateNotification(sentence: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(sentence))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        val notification = createNotification(getString(R.string.notification_playing))
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_tts),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_display_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    fun startPlaying(text: String, startParagraphIndex: Int = 0, charOffsetInParagraph: Int = 0) {
        ttsManager.speak(text, startParagraphIndex, charOffsetInParagraph)
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.notification_playing_short)))
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    fun nextParagraph() = ttsManager.nextParagraph()
    fun previousParagraph() = ttsManager.previousParagraph()

    fun setSpeed(speed: Float) = ttsManager.setSpeed(speed)
    fun setPitch(pitch: Float) = ttsManager.setPitch(pitch)
    fun getVoices() = ttsManager.getVoices()
    fun setVoice(voice: android.speech.tts.Voice) = ttsManager.setVoice(voice)

    fun getBlockIndexForOffset(offset: Int) = ttsManager.getBlockIndexForOffset(offset)
    fun getCharOffsetInBlock(offset: Int, blockIndex: Int) = ttsManager.getCharOffsetInBlock(offset, blockIndex)

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) return

        sleepTimerJob = serviceScope.launch {
            delay(minutes * 60 * 1000L)
            stopPlaying()
        }
    }

    fun resumePlayback() = ttsManager.resumePlayback()
    fun pausePlayback() = ttsManager.pause()

    fun stopPlaying() {
        ttsManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    override fun onDestroy() {
        ttsManager.release()
        mediaSession?.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tts_channel"
        private const val NOTIFICATION_ID = 1
    }
}
