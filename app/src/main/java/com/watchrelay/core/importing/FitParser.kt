package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.Split
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FitParser {
    private const val FIT_EPOCH_OFFSET_SECONDS = 631065600L
    private const val MSG_SESSION = 18
    private const val MSG_LAP = 19
    private const val MSG_RECORD = 20

    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        if (bytes.size < 12) return emptyList()
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize < 12 || bytes.size < headerSize) return emptyList()
        val dataSize = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val end = minOf(bytes.size, headerSize + dataSize)
        val definitions = mutableMapOf<Int, Definition>()
        val records = mutableListOf<TrackPoint>()
        val laps = mutableListOf<Split>()
        var startMillis = 0L
        var durationMillis = 0L
        var distanceMeters: Double? = null
        var calories: Double? = null
        var sport = com.watchrelay.core.model.Sport.OTHER
        var cursor = headerSize
        while (cursor < end) {
            val header = bytes[cursor].toInt() and 0xFF
            cursor += 1
            if (header and 0x80 != 0) {
                // Compressed timestamp headers are uncommon in exported sessions; skip payload if unknown.
                val local = header and 0x60 shr 5
                val def = definitions[local] ?: break
                cursor = readData(bytes, cursor, def, records, laps) { _, _, _ -> }
                continue
            }
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                val hasDev = header and 0x20 != 0
                val parsed = readDefinition(bytes, cursor, hasDev) ?: break
                cursor = parsed.second
                definitions[local] = parsed.first
            } else {
                val def = definitions[local] ?: break
                cursor = readData(bytes, cursor, def, records, laps) { global, field, value ->
                    if (global == MSG_SESSION) {
                        when (field) {
                            2 -> startMillis = fitTimestamp(value)
                            5 -> sport = SportMapper.fromFit(value.toInt())
                            7 -> if (value > 0) durationMillis = value
                            9 -> distanceMeters = value / 100.0
                            11 -> calories = value
                        }
                    }
                }
            }
        }
        if (records.isEmpty() && startMillis == 0L && durationMillis == 0L) return emptyList()
        return listOf(
            WorkoutFactory.build(
                name = SportMapper.displayName(sport),
                sport = sport,
                startMillis = startMillis,
                endMillis = 0L,
                durationMillis = durationMillis,
                distanceMeters = distanceMeters,
                caloriesKcal = calories,
                sourceFormat = FitnessFormat.FIT,
                sourceFileName = fileName,
                track = records,
                splits = laps,
                rawPreview = WorkoutFactory.previewBinaryLabel(fileName, bytes)
            )
        )
    }

    private fun readDefinition(bytes: ByteArray, start: Int, hasDev: Boolean): Pair<Definition, Int>? {
        if (start + 5 > bytes.size) return null
        val architecture = bytes[start + 1].toInt()
        val order = if (architecture == 1) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val global = ByteBuffer.wrap(bytes, start + 2, 2).order(order).short.toInt() and 0xFFFF
        val fieldCount = bytes[start + 4].toInt() and 0xFF
        var cursor = start + 5
        val fields = mutableListOf<FieldDef>()
        repeat(fieldCount) {
            if (cursor + 3 > bytes.size) return null
            fields += FieldDef(
                number = bytes[cursor].toInt() and 0xFF,
                size = bytes[cursor + 1].toInt() and 0xFF,
                baseType = bytes[cursor + 2].toInt() and 0xFF
            )
            cursor += 3
        }
        if (hasDev) {
            if (cursor >= bytes.size) return null
            val devCount = bytes[cursor].toInt() and 0xFF
            cursor += 1
            cursor += devCount * 3
        }
        return Definition(global, order, fields) to cursor
    }

    private fun readData(
        bytes: ByteArray,
        start: Int,
        def: Definition,
        records: MutableList<TrackPoint>,
        laps: MutableList<Split>,
        onField: (global: Int, field: Int, value: Long) -> Unit
    ): Int {
        var cursor = start
        val values = mutableMapOf<Int, Long>()
        for (field in def.fields) {
            if (cursor + field.size > bytes.size) return bytes.size
            val raw = decode(bytes, cursor, field, def.order)
            cursor += field.size
            if (raw != null) {
                values[field.number] = raw
                onField(def.global, field.number, raw)
            }
        }
        when (def.global) {
            MSG_RECORD -> {
                records += TrackPoint(
                    timeMillis = values[253]?.let { fitTimestamp(it) },
                    latitude = values[0]?.let { semicirclesToDegrees(it.toInt()) },
                    longitude = values[1]?.let { semicirclesToDegrees(it.toInt()) },
                    elevationMeters = values[2]?.let { (it / 5.0) - 500.0 },
                    heartRateBpm = values[3]?.toInt(),
                    cadenceSpm = values[4]?.toInt(),
                    distanceMeters = values[5]?.let { it / 100.0 }
                )
            }
            MSG_LAP -> {
                laps += Split(
                    index = laps.size + 1,
                    durationMillis = values[7] ?: values[2] ?: 0L,
                    distanceMeters = values[9]?.let { it / 100.0 },
                    avgHeartRateBpm = values[15]?.toInt()
                )
            }
        }
        return cursor
    }

    private fun decode(bytes: ByteArray, offset: Int, field: FieldDef, order: ByteOrder): Long? {
        val size = field.size
        if (size <= 0) return null
        val buffer = ByteBuffer.wrap(bytes, offset, size).order(order)
        val signed = field.baseType == 0x01 || field.baseType == 0x83 ||
            field.baseType == 0x85 || field.baseType == 0x8C
        return when (size) {
            1 -> if (signed) bytes[offset].toLong() else (bytes[offset].toInt() and 0xFF).toLong()
            2 -> if (signed) buffer.short.toLong() else buffer.short.toLong() and 0xFFFF
            4 -> if (signed) buffer.int.toLong() else buffer.int.toLong() and 0xFFFFFFFFL
            8 -> buffer.long
            else -> null
        }
    }

    private fun fitTimestamp(value: Long): Long {
        val seconds = if (value > 0x10000000) value else value + FIT_EPOCH_OFFSET_SECONDS
        return seconds * 1000L
    }

    private fun semicirclesToDegrees(semicircles: Int): Double =
        semicircles * (180.0 / 2147483648.0)

    private data class FieldDef(val number: Int, val size: Int, val baseType: Int)
    private data class Definition(val global: Int, val order: ByteOrder, val fields: List<FieldDef>)
}
