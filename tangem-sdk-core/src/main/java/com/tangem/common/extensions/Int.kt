package com.tangem.common.extensions

import java.nio.ByteBuffer

fun Int.toByteArray(size: Int = Int.SIZE_BYTES): ByteArray = when (size) {
    Int.SIZE_BYTES -> {
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(this)
        buffer.array()
    }
    Short.SIZE_BYTES -> byteArrayOf((this ushr 8).toByte(), this.toByte())
    1 -> byteArrayOf(this.toByte())
    else -> byteArrayOf()
}