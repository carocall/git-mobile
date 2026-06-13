package com.carocall.gitmobile.utils

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

fun openFileExternally(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun isBinaryFile(file: File): Boolean = try {
    file.inputStream().use { i ->
        val b = ByteArray(1024);
        val r = i.read(b); (0 until r).any { b[it] == 0.toByte() }
    }
} catch (e: Exception) {
    true
}