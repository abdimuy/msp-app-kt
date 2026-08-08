package com.example.msp_app.core.sync.pendingwork.data.observers

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncObserver
import com.example.msp_app.core.logging.RemoteLogger

class RemoteLoggerSessionSyncObserver(
    private val context: Context
) : SessionSyncObserver {

    override fun onResult(synchronizerName: String, result: SyncResult) {
        val logger = RemoteLogger.getInstance(context)
        val message = summarize(result)
        val data: Map<String, Any?> = when (result) {
            is SyncResult.Enqueued -> mapOf(
                "type" to "Enqueued",
                "itemCount" to result.itemCount,
                "workRequestCount" to result.workRequestCount
            )
            is SyncResult.Failed -> mapOf(
                "type" to "Failed",
                "error" to (result.cause.message ?: result.cause::class.java.simpleName)
            )
            is SyncResult.NothingPending -> mapOf("type" to "NothingPending")
            is SyncResult.Skipped -> mapOf("type" to "Skipped")
        }

        logger.info(
            module = MODULE,
            action = "SYNC_RESULT_$synchronizerName",
            message = message,
            data = data
        )
    }

    private fun summarize(result: SyncResult): String = when (result) {
        is SyncResult.Enqueued ->
            "Enqueued ${result.workRequestCount}/${result.itemCount}"
        is SyncResult.Failed ->
            "Failed: ${result.cause.message ?: result.cause::class.java.simpleName}"
        is SyncResult.NothingPending -> "Nothing pending"
        is SyncResult.Skipped -> "Skipped"
    }

    companion object {
        const val MODULE: String = "SESSION_SYNC"
    }
}
