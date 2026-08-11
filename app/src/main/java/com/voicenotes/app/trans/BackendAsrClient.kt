package com.voicenotes.app.trans

import android.content.Context
import android.util.Log
import com.voicenotes.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 自建后端语音识别客户端：把录音文件上传到后端 /api/transcribe
 * （后端基于 faster-whisper，思路参考 WhisperLiveKit），返回转写文本与语言。
 */
object BackendAsrClient {

    private const val TAG = "BackendAsrClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // Whisper 转写可能需要较久
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun transcribe(
        context: Context,
        audioFile: File,
        onDone: (text: String, language: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val base = Prefs.annotateBackendUrl(context).trimEnd('/')
                    val body = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "file",
                            audioFile.name,
                            audioFile.asRequestBody("application/octet-stream".toMediaType())
                        )
                        .build()
                    val builder = Request.Builder()
                        .url("$base/api/transcribe")
                        .post(body)
                    val apiKey = Prefs.annotateApiKey(context)
                    if (apiKey.isNotBlank()) {
                        builder.header("Authorization", "Bearer $apiKey")
                    }
                    client.newCall(builder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("后端返回 ${resp.code}: ${resp.body?.string()}")
                        }
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val text = json.optString("text", "")
                        val language = json.optString("language", "").takeIf { it.isNotBlank() }
                        text to language
                    }
                }
                onDone(result.first, result.second)
            } catch (e: Exception) {
                Log.e(TAG, "后端转写失败", e)
                onError(e.message ?: "后端转写失败")
            }
        }
    }
}
