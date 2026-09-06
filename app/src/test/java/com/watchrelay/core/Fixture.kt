package com.watchrelay.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Fixture {
    fun bytes(name: String): ByteArray {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name")) {
            "Missing fixture fixtures/$name"
        }
        return stream.use { it.readBytes() }
    }

    fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
