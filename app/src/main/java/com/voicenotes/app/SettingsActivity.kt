package com.voicenotes.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.voicenotes.app.data.Prefs
import com.voicenotes.app.databinding.ActivitySettingsBinding
import com.voicenotes.app.util.applySystemBarInsets

/**
 * 设置：选择识别引擎 + 填写各家密钥。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        // 载入当前设置
        when (Prefs.engine(this)) {
            Prefs.ENGINE_SYSTEM -> binding.rbSystem.isChecked = true
            Prefs.ENGINE_TENCENT -> binding.rbTencent.isChecked = true
            Prefs.ENGINE_BAIDU -> binding.rbBaidu.isChecked = true
            Prefs.ENGINE_BACKEND -> binding.rbBackend.isChecked = true
            else -> binding.rbXunfei.isChecked = true
        }
        // 载入当前识别语言
        when (Prefs.lang(this)) {
            Prefs.LANG_CANTONESE -> binding.rbLangCantonese.isChecked = true
            Prefs.LANG_ENGLISH -> binding.rbLangEnglish.isChecked = true
            Prefs.LANG_SICHUAN -> binding.rbLangSichuan.isChecked = true
            else -> binding.rbLangMandarin.isChecked = true
        }
        // 讯飞
        binding.etAppId.setText(Prefs.xunfeiAppId(this))
        binding.etApiKey.setText(Prefs.xunfeiApiKey(this))
        binding.etApiSecret.setText(Prefs.xunfeiApiSecret(this))
        // 腾讯云
        binding.etTencentAppId.setText(Prefs.tencentAppId(this))
        binding.etTencentSecretId.setText(Prefs.tencentSecretId(this))
        binding.etTencentSecretKey.setText(Prefs.tencentSecretKey(this))
        // 百度智能云
        binding.etBaiduAppId.setText(Prefs.baiduAppId(this))
        binding.etBaiduApiKey.setText(Prefs.baiduApiKey(this))
        binding.etBaiduSecretKey.setText(Prefs.baiduSecretKey(this))
        // 英文→中文注释
        binding.switchAnnotate.isChecked = Prefs.annotateZhEnabled(this)
        if (Prefs.annotateProvider(this) == Prefs.ANNOTATE_PROVIDER_OPENAI) {
            binding.rbAnnotateOpenai.isChecked = true
        } else {
            binding.rbAnnotateBackend.isChecked = true
        }
        binding.etAnnotateBackendUrl.setText(Prefs.annotateBackendUrl(this))
        binding.etAnnotateApiKey.setText(Prefs.annotateApiKey(this))
        binding.etAnnotateBaseUrl.setText(Prefs.annotateBaseUrl(this))
        binding.etAnnotateModel.setText(Prefs.annotateModel(this))

        binding.btnSaveSettings.setOnClickListener {
            val selected = when {
                binding.rbSystem.isChecked -> Prefs.ENGINE_SYSTEM
                binding.rbTencent.isChecked -> Prefs.ENGINE_TENCENT
                binding.rbBaidu.isChecked -> Prefs.ENGINE_BAIDU
                binding.rbBackend.isChecked -> Prefs.ENGINE_BACKEND
                else -> Prefs.ENGINE_XUNFEI
            }
            Prefs.setEngine(this, selected)
            val lang = when {
                binding.rbLangCantonese.isChecked -> Prefs.LANG_CANTONESE
                binding.rbLangEnglish.isChecked -> Prefs.LANG_ENGLISH
                binding.rbLangSichuan.isChecked -> Prefs.LANG_SICHUAN
                else -> Prefs.LANG_MANDARIN
            }
            Prefs.setLang(this, lang)
            // 讯飞
            Prefs.setXunfeiAppId(this, binding.etAppId.text?.toString().orEmpty())
            Prefs.setXunfeiApiKey(this, binding.etApiKey.text?.toString().orEmpty())
            Prefs.setXunfeiApiSecret(this, binding.etApiSecret.text?.toString().orEmpty())
            // 腾讯云
            Prefs.setTencentAppId(this, binding.etTencentAppId.text?.toString().orEmpty())
            Prefs.setTencentSecretId(this, binding.etTencentSecretId.text?.toString().orEmpty())
            Prefs.setTencentSecretKey(this, binding.etTencentSecretKey.text?.toString().orEmpty())
            // 百度智能云
            Prefs.setBaiduAppId(this, binding.etBaiduAppId.text?.toString().orEmpty())
            Prefs.setBaiduApiKey(this, binding.etBaiduApiKey.text?.toString().orEmpty())
            Prefs.setBaiduSecretKey(this, binding.etBaiduSecretKey.text?.toString().orEmpty())
            // 英文→中文注释
            Prefs.setAnnotateZhEnabled(this, binding.switchAnnotate.isChecked)
            val annotateProvider =
                if (binding.rbAnnotateOpenai.isChecked) {
                    Prefs.ANNOTATE_PROVIDER_OPENAI
                } else {
                    Prefs.ANNOTATE_PROVIDER_BACKEND
                }
            Prefs.setAnnotateProvider(this, annotateProvider)
            Prefs.setAnnotateBackendUrl(this, binding.etAnnotateBackendUrl.text?.toString().orEmpty())
            Prefs.setAnnotateApiKey(this, binding.etAnnotateApiKey.text?.toString().orEmpty())
            Prefs.setAnnotateBaseUrl(this, binding.etAnnotateBaseUrl.text?.toString().orEmpty())
            Prefs.setAnnotateModel(this, binding.etAnnotateModel.text?.toString().orEmpty())

            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

