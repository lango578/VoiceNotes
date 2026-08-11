package com.voicenotes.app.record

import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * WAV 文件写入器（兜底方案）：把 PCM 直接写入 .wav（16kHz/16bit/单声道）。
 * 若 AAC 编码器不可用时使用。
 */
class WavFileWriter(private val file: File) : AudioFileSink {

    companion object {
        private const val TAG = "WavFileWriter"
        private const val SAMPLE_RATE = 16000
    }

    override val isUsable: Boolean = true

    private var out: DataOutputStream? = null
    private var dataLength = 0L

    init {
        file.parentFile?.mkdirs()
        try {
            val o = DataOutputStream(BufferedOutputStream(FileOutputStream(file)))
            writeHeader(o, 0L)
            out = o
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            out = null
        }
    }

    override fun write(data: ByteArray, size: Int) {
        val o = out ?: return
        try {
            o.write(data, 0, size)
            dataLength += size
        } catch (e: Exception) {
            Log.e(TAG, "写入失败", e)
        }
    }

    override fun finish(): File? {
        try {
            out?.flush()
            out?.close()
            out = null
            // 回填 RIFF/data 长度字段
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4)
                raf.write(leInt((36 + dataLength).toInt()))
                raf.seek(40)
                raf.write(leInt(dataLength.toInt()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "结束写入失败", e)
        }
        return file.takeIf { it.exists() && it.length() > 44 }
    }

    private fun writeHeader(o: DataOutputStream, dataLen: Long) {
        o.writeBytes("RIFF")
        o.write(leInt((36 + dataLen).toInt()))
        o.writeBytes("WAVE")
        o.writeBytes("fmt ")
        o.write(leInt(16))                 // fmt chunk size
        o.write(leShort(1))                // PCM
        o.write(leShort(1))                // 单声道
        o.write(leInt(SAMPLE_RATE))
        o.write(leInt(SAMPLE_RATE * 2))    // byte rate
        o.write(leShort(2))                // block align
        o.write(leShort(16))               // bits per sample
        o.writeBytes("data")
        o.write(leInt(dataLen.toInt()))
    }

    private fun leShort(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun leInt(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )
}
