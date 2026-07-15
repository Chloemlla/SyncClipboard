package com.chloemlla.syncclipboard.mobile.sync

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridge between [SyncEngine] (background IO) and [ImageDownloadConfirmActivity].
 *
 * A pull request parks on a [CompletableDeferred] until the user accepts/declines,
 * or until the pending request is cancelled by a newer image.
 */
object ImageDownloadConfirmBridge {
    data class Request(
        val id: Long,
        val fileName: String,
        val sizeBytes: Long,
        val deferred: CompletableDeferred<Boolean>,
    )

    private val seq = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, Request>()

    fun create(fileName: String, sizeBytes: Long): Request {
        val id = seq.getAndIncrement()
        val request = Request(
            id = id,
            fileName = fileName,
            sizeBytes = sizeBytes,
            deferred = CompletableDeferred(),
        )
        pending[id] = request
        return request
    }

    fun get(id: Long): Request? = pending[id]

    fun complete(id: Long, accepted: Boolean): Boolean {
        val request = pending.remove(id) ?: return false
        return request.deferred.complete(accepted)
    }

    fun cancel(id: Long) {
        val request = pending.remove(id) ?: return
        request.deferred.complete(false)
    }
}
