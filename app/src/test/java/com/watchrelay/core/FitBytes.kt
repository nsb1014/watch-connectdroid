package com.watchrelay.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FitBytes {
    private const val FIT_EPOCH = 631065600L

    fun sessionWithRecord(): ByteArray {
        val start = (1_735_732_800L - FIT_EPOCH).toInt() // 2025-01-01T10:00:00Z-ish unix 1735732800? 
        // Use a known unix: 1767261600 = 2026-01-01T10:00:00Z
        val startFit = (1_767_261_600L - FIT_EPOCH).toInt()
        val lat = degreesToSemicircles(37.7749)
        val lon = degreesToSemicircles(-122.4194)

        val payload = ByteArrayOutput()
        // definition local 0 = session (18)
        payload.u8(0x40)
        payload.u8(0)
        payload.u8(0)
        payload.u16(18)
        payload.u8(4)
        payload.field(2, 4, 0x86) // start_time
        payload.field(5, 1, 0x00) // sport
        payload.field(7, 4, 0x86) // total_elapsed_time ms
        payload.field(9, 4, 0x86) // total_distance cm
        // data local 0
        payload.u8(0x00)
        payload.u32(startFit)
        payload.u8(1) // running
        payload.u32(1_800_000) // 30 min
        payload.u32(520_000) // 5.2 km in cm

        // definition local 1 = record (20)
        payload.u8(0x41)
        payload.u8(0)
        payload.u8(0)
        payload.u16(20)
        payload.u8(5)
        payload.field(253, 4, 0x86)
        payload.field(0, 4, 0x85)
        payload.field(1, 4, 0x85)
        payload.field(3, 1, 0x02)
        payload.field(5, 4, 0x86)
        // data local 1
        payload.u8(0x01)
        payload.u32(startFit)
        payload.i32(lat)
        payload.i32(lon)
        payload.u8(150)
        payload.u32(0)

        val data = payload.toByteArray()
        val header = ByteArray(14)
        header[0] = 14
        header[1] = 0x10
        header[2] = 0x20
        header[3] = 0x08
        ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size)
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        return header + data
    }

    private fun degreesToSemicircles(degrees: Double): Int =
        (degrees * (2147483648.0 / 180.0)).toInt()

    private class ByteArrayOutput {
        private val buffer = ArrayList<Byte>()
        fun u8(value: Int) {
            buffer += (value and 0xFF).toByte()
        }
        fun u16(value: Int) {
            val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
            buffer += bb.array().toList()
        }
        fun u32(value: Int) {
            val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
            buffer += bb.array().toList()
        }
        fun i32(value: Int) = u32(value)
        fun field(number: Int, size: Int, type: Int) {
            u8(number)
            u8(size)
            u8(type)
        }
        fun toByteArray(): ByteArray = buffer.toByteArray()
    }
}
