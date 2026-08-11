package com.voicenotes.app.trans

import android.content.Context
import android.util.Log
import com.voicenotes.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 英文→中文注释翻译客户端。
 * 支持两种来源（设置中切换）：
 * - [Prefs.ANNOTATE_PROVIDER_BACKEND]：自建后端 POST /api/annotate（推荐）
 * - [Prefs.ANNOTATE_PROVIDER_OPENAI]：直连 OpenAI 兼容 API（DeepSeek 等）逐句翻译
 */
object TranslationClient {

    private const val TAG = "TranslationClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 在后台线程把英文文本转成"逐句中文注释"的文本；完成后回调 [onDone]。 */
    fun annotate(
        context: Context,
        englishText: String,
        onDone: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (Prefs.annotateProvider(context) == Prefs.ANNOTATE_PROVIDER_OPENAI) {
                        annotateViaOpenAI(context, englishText)
                    } else {
                        annotateViaBackend(context, englishText)
                    }
                }
                onDone(result)
            } catch (e: Exception) {
                Log.e(TAG, "annotate 失败", e)
                onError(e.message ?: "翻译失败")
            }
        }
    }

    // ---------------- 自建后端 ----------------

    private fun annotateViaBackend(context: Context, text: String): String {
        val base = Prefs.annotateBackendUrl(context).trimEnd('/')
        val url = "$base/api/annotate"
        val body = JSONObject().put("text", text).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(url).post(body)
        val apiKey = Prefs.annotateApiKey(context)
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("后端返回 ${resp.code}")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.optString("annotated", "")
        }
    }

    // ---------------- OpenAI 兼容 API（DeepSeek 等） ----------------

    private fun annotateViaOpenAI(context: Context, text: String): String {
        val sentences = splitSentences(text)
        val sb = StringBuilder()
        for (s in sentences) {
            val zh = translateOpenAI(context, s)
            if (zh.isNotBlank()) {
                sb.append(s).append('\n').append(zh).append("\n\n")
            }
        }
        return sb.toString().trim()
    }

    private fun translateOpenAI(context: Context, text: String): String {
        val base = Prefs.annotateBaseUrl(context).trimEnd('/')
        val apiKey = Prefs.annotateApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("未填写 API Key")
        }
        val model = Prefs.annotateModel(context).ifBlank { "deepseek-chat" }
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "You are a professional translator. Translate the user's English " +
                                    "text into natural Simplified Chinese. Output ONLY the Chinese " +
                                    "translation, with no explanations."
                            )
                    )
                    .put(JSONObject().put("role", "user").put("content", text))
            )
            .put("temperature", 0.3)

        val req = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("API 返回 ${resp.code}: ${resp.body?.string()}")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            val choices = json.optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            return content?.trim().orEmpty()
        }
    }

    /** 按句子结束标点切分（与后端保持一致）。 */
    fun splitSentences(text: String): List<String> =
        Regex("(?<=[.!?。！？])\\s+")
            .split(text.trim())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 粗略判断文本是否以英文为主。 */
    fun isMostlyEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return latin.toDouble() / text.length > 0.5
    }
}
