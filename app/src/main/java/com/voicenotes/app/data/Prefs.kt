package com.voicenotes.app.data

import android.content.Context

/**
 * 应用设置（SharedPreferences）。
 */
object Prefs {
    private const val FILE = "voicenotes_prefs"

    const val ENGINE_XUNFEI = "xunfei"
    const val ENGINE_SYSTEM = "system"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun engine(c: Context): String =
        sp(c).getString("engine", ENGINE_XUNFEI) ?: ENGINE_XUNFEI

    fun setEngine(c: Context, v: String) {
        sp(c).edit().putString("engine", v).apply()
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
}
