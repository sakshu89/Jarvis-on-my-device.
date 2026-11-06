package com.jarvis.security

import android.util.Log
import com.jarvis.BuildConfig

/** Logging helper. Never pass API keys, tokens or message contents to it. */
object JLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.VERBOSE_LOGS) Log.d("JARVIS/$tag", msg)
    }

    fun e(tag: String, msg: String) {
        Log.e("JARVIS/$tag", msg)
    }
}
