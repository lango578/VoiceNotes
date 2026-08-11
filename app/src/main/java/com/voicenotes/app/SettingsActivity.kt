package com.voicenotes.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.voicenotes.app.data.Prefs
import com.voicenotes.app.databinding.ActivitySettingsBinding
import com.voicenotes.app.util.applySystemBarInsets

/**
 * 设置：选择识别引擎 + 填写讯飞密钥。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        // 载入当前设置
        if (Prefs.engine(this) == Prefs.ENGINE_SYSTEM) {
            binding.rbSystem.isChecked = true
        } else {
            binding.rbXunfei.isChecked = true
        }
        binding.etAppId.setText(Prefs.xunfeiAppId(this))
        binding.etApiKey.setText(Prefs.xunfeiApiKey(this))
        binding.etApiSecret.setText(Prefs.xunfeiApiSecret(this))

        binding.btnSaveSettings.setOnClickListener {
            val selected = if (binding.rbSystem.isChecked) {
                Prefs.ENGINE_SYSTEM
            } else {
                Prefs.ENGINE_XUNFEI
            }
            Prefs.setEngine(this, selected)
            Prefs.setXunfeiAppId(this, binding.etAppId.text?.toString().orEmpty())
            Prefs.setXunfeiApiKey(this, binding.etApiKey.text?.toString().orEmpty())
            Prefs.setXunfeiApiSecret(this, binding.etApiSecret.text?.toString().orEmpty())
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
