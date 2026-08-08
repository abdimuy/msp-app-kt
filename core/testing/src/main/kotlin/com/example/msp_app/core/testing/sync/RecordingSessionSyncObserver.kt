package com.example.msp_app.core.testing.sync

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SessionSyncObserver
import java.util.Collections

/**
 * Recording fake of [SessionSyncObserver]. Collects every `(synchronizerName,
 * result)` notification in call order, thread-safely — [SessionSyncObserver]
 * is invoked once per synchronizer from concurrent coroutines in
 * `SyncAllPendingWorkUseCase.execute`.
 */
class RecordingSessionSyncObserver : SessionSyncObserver {

    data class Record(val synchronizerName: String, val result: SyncResult)

    private val mutableRecords: MutableList<Record> = Collections.synchronizedList(mutableListOf())

    val records: List<Record>
        get() = mutableRecords.toList()

    override fun onResult(synchronizerName: String, result: SyncResult) {
        mutableRecords += Record(synchronizerName, result)
    }
}
