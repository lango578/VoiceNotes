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
    const val ENGINE_SYSTEM = "system"

    const val LANG_MANDARIN = "mandarin"
    const val LANG_CANTONESE = "cantonese"
    const val LANG_ENGLISH = "english"
    const val LANG_SICHUAN = "sichuan"

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
}
