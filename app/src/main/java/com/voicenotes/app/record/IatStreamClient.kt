package com.voicenotes.app.record

/**
 * 云端流式语音识别客户端统一接口。
 * 采集线程把 16kHz/16bit/单声道 PCM 通过 [feedPcm] 喂给实现类；
 * 识别结果通过构造时传入的 onResult(isFinal, text) 回调返回。
 */
interface IatStreamClient {
    /** 开始识别（打开会话/获取令牌等）。 */
    fun start()

    /** 持续调用：上传一段 PCM。 */
    fun feedPcm(data: ByteArray)

    /** 停止识别，等待服务端最后的最终结果后回调 [onStopped]。 */
    fun stop(onStopped: () -> Unit)

    /** 释放网络资源（应在 onStopped 之后调用）。 */
    fun shutdown()
}
