package com.jarvis.agent

import android.content.Context
import com.jarvis.automation.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Asks the user yes/no before consequential actions. The question shows in the app,
 * is spoken, and is posted as a notification with Send / Cancel buttons so it still works
 * while another app (e.g. Instagram) is on screen.
 */
class ConfirmationManager(private val ctx: Context) {
    val pending = MutableStateFlow<String?>(null)
    @Volatile
    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun ask(prompt: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        deferred = d
        pending.value = prompt
        Notifier.showConfirm(ctx, prompt)
        try {
            return d.await()
        } finally {
            pending.value = null
            deferred = null
            Notifier.clear(ctx, Notifier.NID_CONFIRM)
        }
    }

    fun hasPending(): Boolean = deferred != null
    fun answer(yes: Boolean) {
        deferred?.complete(yes)
    }
}
