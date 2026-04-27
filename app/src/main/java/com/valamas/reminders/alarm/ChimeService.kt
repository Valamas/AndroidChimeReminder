package com.valamas.reminders.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.valamas.reminders.R
import com.valamas.reminders.data.AppDatabase
import com.valamas.reminders.data.Reminder
import com.valamas.reminders.data.ReminderLog
import com.valamas.reminders.data.RepeatType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChimeService : Service() {

    private val queue = ArrayDeque<Long>()
    private var isPlaying = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var savedAlarmVolume = -1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("DEPRECATION")
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildSilentNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId != -1L) {
            queue.addLast(reminderId)
            if (!isPlaying) playNext()
        }
        return START_NOT_STICKY
    }

    private fun playNext() {
        val reminderId = queue.removeFirstOrNull() ?: run { stopSelf(); return }
        isPlaying = true
        scope.launch {
            val db = AppDatabase.getInstance(this@ChimeService)
            val reminder = db.reminderDao().getById(reminderId)
            if (reminder == null) {
                isPlaying = false
                playNext()
                return@launch
            }
            // Reschedule BEFORE playing — if service crashes, alarm is already rescheduled
            rescheduleAfterFire(db, reminder)
            // Log the trigger
            db.reminderLogDao().insert(
                ReminderLog(reminderId = reminder.id, label = reminder.label)
            )
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            db.reminderLogDao().pruneOlderThan(sevenDaysAgo)
            withContext(Dispatchers.Main) { playChime(reminder) }
        }
    }

    private fun playChime(reminder: Reminder) {
        // AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK: ducks media, does not interrupt phone calls;
        // STREAM_ALARM plays through the speaker even during calls on most devices
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_ALARM,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val targetVol = (reminder.volume.coerceIn(0, 15) * maxVol / 15)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)

        val uri: Uri = when {
            reminder.soundUri.isEmpty() ->
                Uri.parse("android.resource://$packageName/${R.raw.chime}")
            reminder.soundUri.startsWith("raw:") -> {
                val resName = reminder.soundUri.removePrefix("raw:")
                val resId = resources.getIdentifier(resName, "raw", packageName)
                if (resId != 0) Uri.parse("android.resource://$packageName/$resId")
                else Uri.parse("android.resource://$packageName/${R.raw.chime}")
            }
            reminder.soundUri.startsWith("recording:") -> {
                val filename = reminder.soundUri.removePrefix("recording:")
                val file = java.io.File(filesDir, "recordings/$filename")
                if (file.exists()) Uri.fromFile(file)
                else Uri.parse("android.resource://$packageName/${R.raw.chime}")
            }
            else -> Uri.parse(reminder.soundUri)
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@ChimeService, uri)
            isLooping = false
            setOnCompletionListener { onChimeComplete() }
            setOnErrorListener { _, _, _ -> onChimeComplete(); true }
            prepareAsync()
            setOnPreparedListener { it.start() }
        }
    }

    private fun onChimeComplete() {
        mediaPlayer?.release()
        mediaPlayer = null

        if (savedAlarmVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            savedAlarmVolume = -1
        }

        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusListener)

        isPlaying = false
        playNext()
    }

    private suspend fun rescheduleAfterFire(db: AppDatabase, reminder: Reminder) {
        when (reminder.repeatType) {
            RepeatType.ONE_TIME -> {
                // Keep nextTriggerMs so the item stays in its sorted position in the list
                db.reminderDao().update(reminder.copy(isEnabled = false))
            }
            RepeatType.DAILY, RepeatType.CUSTOM_DAYS -> {
                val next = AlarmScheduler.computeNextTrigger(reminder)
                val updated = reminder.copy(nextTriggerMs = next)
                db.reminderDao().update(updated)
                AlarmScheduler.schedule(this@ChimeService, updated)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chime Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildSilentNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_playing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "chime_playback"
    }
}
