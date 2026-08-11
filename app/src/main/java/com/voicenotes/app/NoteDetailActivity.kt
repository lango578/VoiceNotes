package com.voicenotes.app

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voicenotes.app.data.NoteStore
import com.voicenotes.app.databinding.ActivityNoteDetailBinding
import com.voicenotes.app.model.Note
import com.voicenotes.app.trans.TranslationClient
import com.voicenotes.app.util.applySystemBarInsets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔记详情：查看/编辑转写文字，播放录音，分享，删除。
 */
class NoteDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    private lateinit var binding: ActivityNoteDetailBinding
    private var note: Note? = null
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        val id = intent.getStringExtra(EXTRA_NOTE_ID)
        val n = id?.let { nid -> NoteStore.listNotes(this).firstOrNull { it.id == nid } }
        if (n == null) {
            finish()
            return
        }
        note = n

        binding.etTitle.setText(n.title)
        binding.etTranscript.setText(n.transcript)
        binding.metaText.text =
            "时长 ${formatDuration(n.durationMs)} · 创建于 ${formatDate(n.createdAt)}"
        binding.zhText.movementMethod = ScrollingMovementMethod()
        renderZh(n)

        binding.btnPlay.setOnClickListener { togglePlay(n) }
        binding.btnShareText.setOnClickListener { shareText(n) }
        binding.btnShareAudio.setOnClickListener { shareAudio(n) }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnAnnotate.setOnClickListener { annotateNote(n) }
    }

    /** 展示中文注释（若有）。 */
    private fun renderZh(n: Note) {
        val zh = n.zhTranslation
        if (zh.isNullOrBlank()) {
            binding.zhLabel.visibility = android.view.View.GONE
            binding.zhText.visibility = android.view.View.GONE
        } else {
            binding.zhLabel.visibility = android.view.View.VISIBLE
            binding.zhText.visibility = android.view.View.VISIBLE
            binding.zhText.text = zh
        }
    }

    /** 调用后端/第三方 API 为英文笔记添加逐句中文注释。 */
    private fun annotateNote(n: Note) {
        if (!TranslationClient.isMostlyEnglish(n.transcript)) {
            Toast.makeText(this, R.string.toast_annotate_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnAnnotate.isEnabled = false
        Toast.makeText(this, R.string.toast_annotating, Toast.LENGTH_SHORT).show()
        TranslationClient.annotate(
            context = this,
            englishText = n.transcript,
            onDone = { zh ->
                runOnUiThread {
                    binding.btnAnnotate.isEnabled = true
                    if (zh.isNullOrBlank()) {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_annotate_failed, "返回为空"),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }
                    val updated = n.copy(zhTranslation = zh)
                    NoteStore.save(this, updated)
                    note = updated
                    renderZh(updated)
                    Toast.makeText(this, R.string.toast_annotate_done, Toast.LENGTH_SHORT).show()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    binding.btnAnnotate.isEnabled = true
                    Toast.makeText(
                        this,
                        getString(R.string.toast_annotate_failed, msg),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun togglePlay(n: Note) {
        val audioFile = audioFileOf(n)
        if (audioFile == null) {
            Toast.makeText(this, R.string.no_audio_file, Toast.LENGTH_SHORT).show()
            return
        }
        if (player != null) {
            stopPlayback()
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            binding.btnPlay.text = getString(R.string.btn_stop_play)
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        binding.btnPlay.text = getString(R.string.btn_play)
    }

    private fun audioFileOf(n: Note): File? {
        val name = n.audioFileName ?: return null
        val f = File(NoteStore.recordingsDir(this), name)
        return f.takeIf { it.exists() }
    }

    private fun shareText(n: Note) {
        val text = if (n.title.isNotBlank()) {
            "【${n.title}】\n\n${n.transcript}"
        } else {
            n.transcript
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
    }

    private fun shareAudio(n: Note) {
        val f = audioFileOf(n)
        if (f == null) {
            Toast.makeText(this, R.string.no_audio_file, Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_audio_title)))
    }

    private fun save() {
        val n = note ?: return
        val updated = n.copy(
            title = binding.etTitle.text?.toString()?.trim().orEmpty(),
            transcript = binding.etTranscript.text?.toString().orEmpty()
        )
        NoteStore.save(this, updated)
        note = updated
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        val n = note ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_delete))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                stopPlayback()
                NoteStore.delete(this, n)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)
    }

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}
