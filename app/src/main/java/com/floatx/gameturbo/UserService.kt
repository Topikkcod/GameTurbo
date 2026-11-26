package com.floatx.gameturbo

import android.content.Context
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.system.exitProcess

/** Berjalan di proses Shizuku (hak shell): menyuntik sentuhan dan ketikan ke layar virtual. */
class UserService : IUserService.Stub {
    constructor() : super()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    private val im: Any by lazy {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "input") as IBinder
        Class.forName("android.hardware.input.IInputManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun inject(ev: InputEvent, displayId: Int) {
        try { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(ev, displayId) } catch (_: Throwable) { }
        val m = im.javaClass.methods.first { it.name == "injectInputEvent" }
        if (m.parameterTypes.size >= 3) m.invoke(im, ev, 0, -1) else m.invoke(im, ev, 0)
    }

    override fun destroy() { exitProcess(0) }

    override fun injectMotion(displayId: Int, action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        try {
            val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 1f, 1f, 0, 1f, 1f, 0, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            inject(ev, displayId)
            ev.recycle()
        } catch (_: Throwable) { }
    }

    override fun injectKey(displayId: Int, keyCode: Int, action: Int) {
        try {
            val now = SystemClock.uptimeMillis()
            inject(KeyEvent(now, now, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD), displayId)
        } catch (_: Throwable) { }
    }

    override fun injectText(displayId: Int, text: String) {
        try {
            val evs = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return
            val now = SystemClock.uptimeMillis()
            for (e in evs) {
                inject(KeyEvent(now, now, e.action, e.keyCode, 0, e.metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD), displayId)
            }
        } catch (_: Throwable) { }
    }

    override fun exec(cmd: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val o = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor(); o.trim()
    } catch (e: Throwable) { "ERR: ${e.message}" }
}
