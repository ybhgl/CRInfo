package com.crinfo.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 主线程首帧预读的统一入口：正常情况 DataStore 快照几毫秒返回；
 * 若赶上「杀掉秒重开」这类文件锁未释放的病理场景，超时返回 null 让界面先吃兜底值，
 * 绝不让主线程挂着等出黑屏 ANR。后台 collect 随后会把真值补回来。
 */
internal fun <T> blockingSnapshot(timeoutMs: Long = 400, read: suspend () -> T): T? =
    runBlocking { withTimeoutOrNull(timeoutMs) { read() } }
