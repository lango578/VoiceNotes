package com.voicenotes.app.record

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 录音会话的全局状态：在前台服务（录制/识别线程）与 Activity（UI）之间共享。
 * 所有变更都通过 [update] 发起，并在主线程通知监听者，避免跨线程 UI 问题。
 */
object RecorderSession {

    data class State(
        val recording: Boolean = false,
        val elapsedMs: Long = 0L,
        val level: Float = 0f,
        val committed: String = "",
        val pending: String = "",
        val statusText: String = "",
        val error: String? = null
    ) {
        /** 当前显示在界面上的完整文字 = 已定稿文本 + 实时修正中的当前句。 */
        val transcript: String get() = committed + pending
    }

    private val lock = Any()
    @Volatile private var current = State()
    val state: State get() = current

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (State) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners.remove(listener)
    }

    fun update(transform: (State) -> State) {
        synchronized(lock) {
            current = transform(current)
        }
        val snapshot = current
        main.post { listeners.forEach { it(snapshot) } }
    }

    fun reset() {
        synchronized(lock) { current = State() }
        val snapshot = current
        main.post { listeners.forEach { it(snapshot) } }
    }
}
