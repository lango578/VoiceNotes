package com.voicenotes.app.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * 使用 MediaCodec(AAC) + MediaMuxer 将 PCM 编码为 .m4a 文件。
 * 与讯飞识别共用同一路 AudioRecord 数据，文件小、适合长录音。
 */
class AacFileWriter(private val file: File) : AudioFileSink {

    companion object {
        private const val TAG = "AacFileWriter"
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 64000
    }

    override val isUsable: Boolean get() = codec != null

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamples = 0L
    private var finished = false

    init {
        file.parentFile?.mkdirs()
        try {
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1)
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            f.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            c.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            codec = c
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            releaseCodec()
        }
    }

    override fun write(data: ByteArray, size: Int) {
        val c = codec ?: return
        totalSamples += size / 2
        val ptsUs = totalSamples * 1_000_000L / SAMPLE_RATE
        feedEncoder(c, data, size, ptsUs)
        drainEncoder(c, endOfStream = false)
    }

    override fun finish(): File? {
        if (finished) return file.takeIf { it.exists() && it.length() > 0 }
        finished = true
        val c = codec
        if (c != null) {
            try {
                c.signalEndOfInputStream()
                drainEncoder(c, endOfStream = true)
            } catch (e: Exception) {
                Log.e(TAG, "结束编码失败", e)
            }
        }
        releaseCodec()
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    private fun feedEncoder(c: MediaCodec, data: ByteArray, size: Int, ptsUs: Long) {
        var offset = 0
        while (offset < size) {
            val idx = c.dequeueInputBuffer(10_000)
            if (idx < 0) return
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear()
            val n = minOf(buf.capacity(), size - offset)
            buf.put(data, offset, n)
            c.queueInputBuffer(idx, 0, n, ptsUs, 0)
            offset += n
        }
    }

    private fun drainEncoder(c: MediaCodec, endOfStream: Boolean) {
        while (true) {
            val info = MediaCodec.BufferInfo()
            val idx = c.dequeueOutputBuffer(info, 10_000)
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val m = muxer
                    if (m != null && !muxerStarted) {
                        trackIndex = m.addTrack(c.outputFormat)
                        m.start()
                        muxerStarted = true
                    }
                }
                idx >= 0 -> {
                    val out = c.getOutputBuffer(idx)
                    val m = muxer
                    if (out != null && m != null && muxerStarted && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        m.writeSampleData(trackIndex, out, info)
                    }
                    c.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
                else -> break
            }
        }
    }

    private fun releaseCodec() {
        try {
            val m = muxer
            if (m != null) {
                if (muxerStarted) m.stop()
                m.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放 muxer 失败", e)
        }
        muxer = null
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
    }
}
