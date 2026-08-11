package com.voicenotes.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.voicenotes.app.data.NoteStore
import com.voicenotes.app.databinding.ActivityMainBinding
import com.voicenotes.app.record.RecordingService
import com.voicenotes.app.ui.NotesAdapter
import com.voicenotes.app.util.applySystemBarInsets

/**
 * 主界面：笔记列表 + 开始录音。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NotesAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                startRecording()
            } else {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val savedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        adapter = NotesAdapter { note ->
            startActivity(
                Intent(this, NoteDetailActivity::class.java)
                    .putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
            )
        }
        binding.noteList.layoutManager = LinearLayoutManager(this)
        binding.noteList.adapter = adapter

        binding.btnRecord.setOnClickListener {
            if (RecordingService.isRunning) {
                startActivity(Intent(this, RecorderActivity::class.java))
            } else {
                requestPermissionsAndStart()
            }
        }

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        ContextCompat.registerReceiver(
            this,
            savedReceiver,
            IntentFilter(RecordingService.ACTION_NOTE_SAVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        reload()
    }

    private fun requestPermissionsAndStart() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) {
            startRecording()
        } else {
            permissionLauncher.launch(need.toTypedArray())
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
        startForegroundService(intent)
        startActivity(Intent(this, RecorderActivity::class.java))
    }

    private fun reload() {
        val notes = NoteStore.listNotes(this)
        adapter.submit(notes)
        binding.emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(savedReceiver)
    }
}
