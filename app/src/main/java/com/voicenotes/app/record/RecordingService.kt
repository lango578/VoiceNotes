package com.voicenotes.app.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import com.voicenotes.app.MainActivity
import com.voicenotes.app.R
import com.voicenotes.app.data.NoteStore
import com.voicenotes.app.data.Prefs
import com.voicenotes.app.model.Note
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/**
 * 前台录音转写服务（foregroundServiceType="microphone"，符合 Android 14+/16 规范）。
 *
 * - 讯飞引擎：AudioRecord 采集 16kHz/16bit/单声道 PCM →
 *            一路写录音文件(AAC/.m4a 或 WAV 兜底)，一路实时上传讯飞识别。
 * - 系统引擎：MediaRecorder 录音 + 系统 SpeechRecognizer 连续识别。
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.voicenotes.app.action.START"
        const val ACTION_STOP = "com.voicenotes.app.action.STOP"
        const val ACTION_NOTE_SAVED = "com.voicenotes.app.action.NOTE_SAVED"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"

        const val SAMPLE_RATE = 16000
        private const val READ_SIZE = 1280 // 40ms @16kHz/16bit/单声道

        /** 是否正在录音（Activity 据此判断）。 */
        val isRunning: Boolean get() = RecorderSession.state.recording
    }

    @Volatile private var running = false
    private var audioThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioSink: AudioFileSink? = null
    private var systemAudioFile: File? = null
    private var xunfei: XunfeiIatClient? = null
    private var systemRecognizer: SystemRecognizerClient? = null
    private var mediaRecorder: MediaRecorder? = null

    private var sessionId: String = ""
    private var startedAt = 0L
    private var startedAtWall = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification(getString(R.string.notification_text_default))
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (running) return
        running = true
        sessionId = UUID.randomUUID().toString()
        startedAt = SystemClock.elapsedRealtime()
        startedAtWall = System.currentTimeMillis()

        RecorderSession.reset()
        RecorderSession.update {
            it.copy(recording = true, statusText = getString(R.string.recorder_status_recording))
        }

        val engine = Prefs.engine(this)
        when (engine) {
            Prefs.ENGINE_XUNFEI -> {
                val appId = Prefs.xunfeiAppId(this)
                val apiKey = Prefs.xunfeiApiKey(this)
                val apiSecret = Prefs.xunfeiApiSecret(this)
                if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
                    failStart(getString(R.string.toast_keys_missing))
                    return
                }
                startXunfeiEngine(appId, apiKey, apiSecret)
            }
            else -> {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    failStart(getString(R.string.toast_engine_unavailable))
                    return
                }
                startSystemEngine()
            }
        }
    }

    private fun failStart(message: String) {
        running = false
        RecorderSession.update {
            it.copy(recording = false, error = message, statusText = message)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------- 讯飞引擎 ----------------

    private fun startXunfeiEngine(appId: String, apiKey: String, apiSecret: String) {
        val aacSink = AacFileWriter(File(NoteStore.recordingsDir(this), "$sessionId.m4a"))
        audioSink = if (aacSink.isUsable) {
            aacSink
        } else {
            Log.w(TAG, "AAC 编码器不可用，使用 WAV 兜底")
            WavFileWriter(File(NoteStore.recordingsDir(this), "$sessionId.wav"))
        }

        xunfei = XunfeiIatClient(
            appId = appId,
            apiKey = apiKey,
            apiSecret = apiSecret,
            onResult = { isFinal, text -> handleTranscript(isFinal, text) },
            onError = { msg -> handleEngineError(msg) }
        )

        if (!startAudioRecord()) {
            failStart(getString(R.string.record_failed, "AudioRecord 初始化失败"))
            xunfei?.shutdown()
            xunfei = null
            return
        }

        xunfei?.start()
        audioThread = Thread({ audioLoop() }, "audio-capture").also { it.start() }
    }

    private fun startAudioRecord(): Boolean {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return false
            val bufSize = maxOf(minBuf, READ_SIZE * 4)
            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                return false
            }
            ar.startRecording()
            audioRecord = ar
            true
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 初始化失败", e)
            false
        }
    }

    private fun audioLoop() {
        val ar = audioRecord ?: return
        val buf = ByteArray(READ_SIZE)
        var lastUi = 0L
        while (running) {
            val n = try {
                ar.read(buf, 0, READ_SIZE)
            } catch (e: Exception) {
                Log.e(TAG, "读取音频失败", e)
                break
            }
            if (n <= 0) continue
            audioSink?.write(buf, n)
            xunfei?.feedPcm(buf.copyOf(n))
            val now = SystemClock.elapsedRealtime()
            if (now - lastUi >= 200) {
                lastUi = now
                val level = computeRms(buf, n)
                RecorderSession.update {
                    it.copy(elapsedMs = now - startedAt, level = level)
                }
                updateNotification(
                    RecorderSession.state.transcript.ifBlank {
                        getString(R.string.notification_text_default)
                    }
                )
            }
        }
    }


    // ---------------- 系统引擎 ----------------

    private fun startSystemEngine() {
        val audioFile = File(NoteStore.recordingsDir(this), "$sessionId.m4a")
        systemAudioFile = audioFile
        try {
            val mr = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(64000)
                setOutputFile(audioFile)
                prepare()
                start()
            }
            mediaRecorder = mr
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder 启动失败", e)
            mediaRecorder = null
            systemAudioFile = null
        }

        systemRecognizer = SystemRecognizerClient(
            context = this,
            onResult = { isFinal, text -> handleTranscript(isFinal, text) },
            onError = { msg -> handleEngineError(msg) }
        )
        systemRecognizer?.start()

        audioThread = Thread({
            while (running) {
                Thread.sleep(500)
                val now = SystemClock.elapsedRealtime()
                RecorderSession.update { it.copy(elapsedMs = now - startedAt) }
                updateNotification(
                    RecorderSession.state.transcript.ifBlank {
                        getString(R.string.notification_text_default)
                    }
                )
            }
        }, "sys-timer").also { it.start() }
    }

    // ---------------- 停止与保存 ----------------

    private fun stopRecording() {
        if (!running) return
        running = false

        val t = audioThread
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(3000)
            } catch (_: InterruptedException) {
            }
        }
        audioThread = null
        releaseAudioRecord()

        val engine = Prefs.engine(this)
        val audioFile = if (engine == Prefs.ENGINE_XUNFEI) {
            audioSink?.finish().also { audioSink = null }
        } else {
            releaseMediaRecorder()
            systemAudioFile?.takeIf { it.exists() && it.length() > 0 }.also { systemAudioFile = null }
        }

        RecorderSession.update {
            it.copy(statusText = getString(R.string.recorder_status_stopping))
        }

        if (engine == Prefs.ENGINE_XUNFEI) {
            val x = xunfei
            if (x != null) {
                x.stop {
                    x.shutdown()
                    xunfei = null
                    finalizeAndSave(audioFile)
                }
            } else {
                finalizeAndSave(audioFile)
            }
        } else {
            systemRecognizer?.stop()
            systemRecognizer?.destroy()
            systemRecognizer = null
            finalizeAndSave(audioFile)
        }
    }

    private fun finalizeAndSave(audioFile: File?) {
        val s = RecorderSession.state
        var transcript = s.committed
        val pending = s.pending.trim()
        if (pending.isNotEmpty()) transcript = (transcript + pending).trim()
        transcript = transcript.trim()

        val title = if (transcript.isNotEmpty()) {
            transcript.take(20) + if (transcript.length > 20) "…" else ""
        } else {
            getString(R.string.app_name)
        }

        val note = Note(
            id = sessionId,
            title = title,
            createdAt = startedAtWall,
            durationMs = s.elapsedMs.coerceAtLeast(1),
            transcript = transcript,
            audioFileName = audioFile?.name
        )
        NoteStore.save(this, note)

        RecorderSession.update {
            it.copy(
                recording = false,
                error = null,
                statusText = getString(R.string.toast_saved),
                committed = transcript,
                pending = ""
            )
        }
        sendBroadcast(Intent(ACTION_NOTE_SAVED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------- 转写结果处理 ----------------

    private fun handleTranscript(isFinal: Boolean, text: String) {
        RecorderSession.update { st ->
            if (isFinal) {
                st.copy(committed = st.committed + text, pending = "")
            } else {
                st.copy(pending = text)
            }
        }
        updateNotification(
            RecorderSession.state.transcript.ifBlank {
                getString(R.string.notification_text_default)
            }
        )
    }

    private fun handleEngineError(msg: String) {
        Log.e(TAG, "识别引擎错误: $msg")
        // 识别异常不打断录音，录音文件仍会正常保存
        RecorderSession.update { it.copy(error = msg, statusText = msg) }
    }

    private fun computeRms(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        val count = n / 2
        if (count == 0) return 0f
        return (sqrt(sum / count) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    // ---------------- 工具 ----------------

    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.notification_channel_desc)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_recording))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.e(TAG, "更新通知失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (running) {
            running = false
            try {
                audioThread?.join(3000)
            } catch (_: Exception) {
            }
            audioThread = null
            releaseAudioRecord()
            audioSink?.finish()
            audioSink = null
            xunfei?.shutdown()
            xunfei = null
            systemRecognizer?.destroy()
            systemRecognizer = null
            releaseMediaRecorder()
        }
    }
}

