package com.voicenotes.app.record

import java.io.File

/**
 * 音频落盘接口：接收 16kHz / 16bit / 单声道 PCM，编码写入音频文件。
 */
interface AudioFileSink {
    /** 写入一段 PCM 数据。 */
    fun write(data: ByteArray, size: Int)

    /** 结束写入，返回生成的音频文件；失败返回 null。 */
    fun finish(): File?

    /** 初始化是否成功（失败时上层可换用 WAV 兜底）。 */
    val isUsable: Boolean
}
