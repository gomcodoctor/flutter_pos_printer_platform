package com.sersoluciones.flutter_pos_printer_platform

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.WindowManager
import android.widget.Toast

/**
 * Toast.show() that can't crash the app on Android 7.1 (API 25).
 *
 * There, a toast whose window token is gone by the time it is shown (the
 * activity finished, or the main thread was busy for a few seconds) throws
 * WindowManager.BadTokenException from Toast$TN's own handler - asynchronously,
 * so a try/catch around show() never sees it. Seen as a fatal crash on
 * rk3288 POS terminals. Android 8+ catches this itself.
 */
object SafeToast {
    private const val TAG = "SafeToast"

    fun show(context: Context?, text: CharSequence?, duration: Int) {
        if (context == null || text == null) return
        show(Toast.makeText(context, text, duration))
    }

    fun show(context: Context?, resId: Int, duration: Int) {
        if (context == null) return
        show(Toast.makeText(context, resId, duration))
    }

    private fun show(toast: Toast) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) wrapHandler(toast)
        try {
            toast.show()
        } catch (e: Exception) {
            Log.w(TAG, "toast not shown", e)
        }
    }

    /** Swaps Toast.mTN.mHandler for one that drops BadTokenException. */
    private fun wrapHandler(toast: Toast) {
        try {
            val tnField = Toast::class.java.getDeclaredField("mTN").apply { isAccessible = true }
            val tn = tnField.get(toast) ?: return
            val handlerField = tn.javaClass.getDeclaredField("mHandler").apply { isAccessible = true }
            val original = handlerField.get(tn) as? Handler ?: return
            handlerField.set(tn, object : Handler(original.looper) {
                override fun handleMessage(msg: Message) {
                    try {
                        original.handleMessage(msg)
                    } catch (e: WindowManager.BadTokenException) {
                        Log.w(TAG, "toast window gone", e)
                    }
                }
            })
        } catch (e: Exception) {
            // a vendor build with different Toast internals: show it unwrapped
            Log.w(TAG, "could not wrap toast handler", e)
        }
    }
}
