package com.voicenotes.app.data

import android.content.Context
import android.util.Log
import com.voicenotes.app.model.Note
import org.json.JSONObject
import java.io.File

/**
 * 基于文件系统的简易笔记仓库（无需数据库依赖）。
 * - 笔记元数据: filesDir/notes/<id>.json
 * - 录音文件:   filesDir/recordings/<id>.m4a|.wav
 */
object NoteStore {
    private const val TAG = "NoteStore"
    private const val NOTES_DIR = "notes"
    private const val RECORDINGS_DIR = "recordings"

    fun notesDir(context: Context): File =
        File(context.filesDir, NOTES_DIR).apply { if (!exists()) mkdirs() }

    fun recordingsDir(context: Context): File =
        File(context.filesDir, RECORDINGS_DIR).apply { if (!exists()) mkdirs() }

    fun listNotes(context: Context): List<Note> {
        val dir = notesDir(context)
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: emptyArray()
        return files.mapNotNull { f ->
            try {
                Note.fromJson(JSONObject(f.readText()))
            } catch (e: Exception) {
                Log.e(TAG, "读取笔记失败: ${f.name}", e)
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    fun save(context: Context, note: Note) {
        try {
            File(notesDir(context), "${note.id}.json").writeText(note.toJson())
        } catch (e: Exception) {
            Log.e(TAG, "保存笔记失败", e)
        }
    }

    fun delete(context: Context, note: Note) {
        File(notesDir(context), "${note.id}.json").delete()
        note.audioFileName?.let { name ->
            File(recordingsDir(context), name).delete()
        }
    }
}
