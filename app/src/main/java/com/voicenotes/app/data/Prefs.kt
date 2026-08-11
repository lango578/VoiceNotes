package com.voicenotes.app.data

import android.content.Context

/**
 * 应用设置（SharedPreferences）。
 */
object Prefs {
    private const val FILE = "voicenotes_prefs"

    const val ENGINE_XUNFEI = "xunfei"
    const val ENGINE_TENCENT = "tencent"
    const val ENGINE_BAIDU = "baidu"
    const val ENGINE_BACKEND = "backend"
    const val ENGINE_SYSTEM = "system"

    const val LANG_MANDARIN = "mandarin"
    const val LANG_CANTONESE = "cantonese"
    const val LANG_ENGLISH = "english"
    const val LANG_SICHUAN = "sichuan"

    const val ANNOTATE_PROVIDER_BACKEND = "backend"
    const val ANNOTATE_PROVIDER_OPENAI = "openai"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun engine(c: Context): String =
        sp(c).getString("engine", ENGINE_XUNFEI) ?: ENGINE_XUNFEI

    fun setEngine(c: Context, v: String) {
        sp(c).edit().putString("engine", v).apply()
    }

    /** 识别语言（普通话/粤语/英语/四川话）。 */
    fun lang(c: Context): String =
        sp(c).getString("lang", LANG_MANDARIN) ?: LANG_MANDARIN

    fun setLang(c: Context, v: String) {
        sp(c).edit().putString("lang", v).apply()
    }

    fun xunfeiAppId(c: Context): String = sp(c).getString("xf_appid", "").orEmpty()
    fun setXunfeiAppId(c: Context, v: String) {
        sp(c).edit().putString("xf_appid", v.trim()).apply()
    }

    fun xunfeiApiKey(c: Context): String = sp(c).getString("xf_apikey", "").orEmpty()
    fun setXunfeiApiKey(c: Context, v: String) {
        sp(c).edit().putString("xf_apikey", v.trim()).apply()
    }

    fun xunfeiApiSecret(c: Context): String = sp(c).getString("xf_apisecret", "").orEmpty()
    fun setXunfeiApiSecret(c: Context, v: String) {
        sp(c).edit().putString("xf_apisecret", v.trim()).apply()
    }

    // ---- 腾讯云 ----
    fun tencentAppId(c: Context): String = sp(c).getString("tx_appid", "").orEmpty()
    fun setTencentAppId(c: Context, v: String) {
        sp(c).edit().putString("tx_appid", v.trim()).apply()
    }

    fun tencentSecretId(c: Context): String = sp(c).getString("tx_secretid", "").orEmpty()
    fun setTencentSecretId(c: Context, v: String) {
        sp(c).edit().putString("tx_secretid", v.trim()).apply()
    }

    fun tencentSecretKey(c: Context): String = sp(c).getString("tx_secretkey", "").orEmpty()
    fun setTencentSecretKey(c: Context, v: String) {
        sp(c).edit().putString("tx_secretkey", v.trim()).apply()
    }

    // ---- 百度智能云 ----
    fun baiduAppId(c: Context): String = sp(c).getString("bd_appid", "").orEmpty()
    fun setBaiduAppId(c: Context, v: String) {
        sp(c).edit().putString("bd_appid", v.trim()).apply()
    }

    fun baiduApiKey(c: Context): String = sp(c).getString("bd_apikey", "").orEmpty()
    fun setBaiduApiKey(c: Context, v: String) {
        sp(c).edit().putString("bd_apikey", v.trim()).apply()
    }

    fun baiduSecretKey(c: Context): String = sp(c).getString("bd_secretkey", "").orEmpty()
    fun setBaiduSecretKey(c: Context, v: String) {
        sp(c).edit().putString("bd_secretkey", v.trim()).apply()
    }

    // ---- 英文→中文注释 ----

    /** 识别为英文时，是否自动给每句加中文注释。 */
    fun annotateZhEnabled(c: Context): Boolean =
        sp(c).getBoolean("annotate_zh", false)

    fun setAnnotateZhEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("annotate_zh", v).apply()
    }

    /** backend = 自建后端；openai = 直连 OpenAI 兼容 API（DeepSeek 等）。 */
    fun annotateProvider(c: Context): String =
        sp(c).getString("annotate_provider", ANNOTATE_PROVIDER_BACKEND) ?: ANNOTATE_PROVIDER_BACKEND

    fun setAnnotateProvider(c: Context, v: String) {
        sp(c).edit().putString("annotate_provider", v).apply()
    }

    fun annotateBackendUrl(c: Context): String =
        sp(c).getString("annotate_backend_url", "http://192.168.1.100:8000").orEmpty()

    fun setAnnotateBackendUrl(c: Context, v: String) {
        sp(c).edit().putString("annotate_backend_url", v.trim()).apply()
    }

    fun annotateApiKey(c: Context): String = sp(c).getString("annotate_api_key", "").orEmpty()
    fun setAnnotateApiKey(c: Context, v: String) {
        sp(c).edit().putString("annotate_api_key", v.trim()).apply()
    }

    fun annotateBaseUrl(c: Context): String =
        sp(c).getString("annotate_base_url", "https://api.deepseek.com").orEmpty()

    fun setAnnotateBaseUrl(c: Context, v: String) {
        sp(c).edit().putString("annotate_base_url", v.trim()).apply()
    }

    fun annotateModel(c: Context): String =
        sp(c).getString("annotate_model", "deepseek-chat").orEmpty()

    fun setAnnotateModel(c: Context, v: String) {
        sp(c).edit().putString("annotate_model", v.trim()).apply()
    }
}
