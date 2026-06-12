package com.carocall.gitmobile.utils

import java.io.File


fun isBinaryFile(file: File): Boolean = try {
    file.inputStream().use { i ->
        val b = ByteArray(1024);
        val r = i.read(b); (0 until r).any { b[it] == 0.toByte() }
    }
} catch (e: Exception) {
    true
}