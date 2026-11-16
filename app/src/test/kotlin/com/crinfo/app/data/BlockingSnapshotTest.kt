package com.crinfo.app.data

import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockingSnapshotTest {
    @Test
    fun `returns value when read is fast`() {
        assertEquals("v", blockingSnapshot { "v" })
    }

    @Test
    fun `returns null when read exceeds timeout`() {
        // 模拟「秒重开抢不到 DataStore 文件锁」：预读不能把主线程挂死
        assertNull(blockingSnapshot(timeoutMs = 50) {
            delay(500)
            "late"
        })
    }
}
