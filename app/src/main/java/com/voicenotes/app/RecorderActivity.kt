package com.voicenotes.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicenotes.app.databinding.ActivityRecorderBinding
import com.voicenotes.app.record.RecorderSession
import com.voicenotes.app.record.RecordingService
import com.voicenotes.app.util.applySystemBarInsets
import java.util.Locale

/**
 * 录音转写界面：实时显示识别文字、计时、音量。
 */
class RecorderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecorderBinding
    private val listener: (RecorderSession.State) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        // 若服务未在运行（例如从主界面进入时服务刚被系统回收），先启动它
        if (!RecordingService.isRunning) {
            val intent = Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_START)
            startForegroundService(intent)
        }

        binding.btnStop.setOnClickListener {
            binding.btnStop.isEnabled = false
            binding.statusText.text = getString(R.string.recorder_status_stopping)
            val intent = Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_STOP)
            startService(intent)
            finish()
        }

        RecorderSession.addListener(listener)
        render(RecorderSession.state)
    }

    private fun render(s: RecorderSession.State) {
        binding.statusText.text =
            s.error ?: s.statusText.ifBlank { getString(R.string.recorder_status_idle) }
        binding.elapsedText.text = formatElapsed(s.elapsedMs)
        binding.levelBar.progress = (s.level * 100).toInt().coerceIn(0, 100)
        binding.transcriptText.text =
            s.transcript.ifBlank { getString(R.string.transcript_placeholder) }
        binding.btnStop.isEnabled = s.recording
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        RecorderSession.removeListener(listener)
    }
}
