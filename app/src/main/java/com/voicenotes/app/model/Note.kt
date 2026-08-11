package com.voicenotes.app.model

import org.json.JSONObject

/**
 * 一条语音笔记：转写文字 + 可选录音文件。
 */
data class Note(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val transcript: String,
    val audioFileName: String?,
    /** 英文识别时的逐句中文注释（可空）。 */
    val zhTranslation: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("durationMs", durationMs)
        put("transcript", transcript)
        put("audioFileName", audioFileName ?: "")
        put("zhTranslation", zhTranslation ?: "")
    }.toString()

    companion object {
        fun fromJson(json: JSONObject): Note = Note(
            id = json.optString("id", ""),
            title = json.optString("title", ""),
            createdAt = json.optLong("createdAt", 0L),
            durationMs = json.optLong("durationMs", 0L),
            transcript = json.optString("transcript", ""),
            audioFileName = json.optString("audioFileName", "").takeIf { it.isNotBlank() },
            zhTranslation = json.optString("zhTranslation", "").takeIf { it.isNotBlank() }
        )
    }
}
