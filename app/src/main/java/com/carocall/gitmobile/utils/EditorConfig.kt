package com.carocall.gitmobile.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

object EditorConfig {

    private val extensionToLanguage = mapOf(
        // C-family
        "c" to LanguageInfo("source.c", "c.json"),
        "cpp" to LanguageInfo("source.cpp", "cpp.json"),
        "cc" to LanguageInfo("source.cpp", "cpp.json"),
        "h" to LanguageInfo("source.cpp", "cpp.json"),
        "hpp" to LanguageInfo("source.cpp", "cpp.json"),
        "cs" to LanguageInfo("source.cs", "csharp.json"),
        "csharp" to LanguageInfo("source.cs", "csharp.json"),

        // Java/Kotlin/JVM
        "java" to LanguageInfo("source.java", "java.json"),
        "kt" to LanguageInfo("source.kotlin", "kotlin.json"),
        "kts" to LanguageInfo("source.kotlin", "kotlin.json"),
        "gradle" to LanguageInfo("source.groovy", "groovy.json"),
        "groovy" to LanguageInfo("source.groovy", "groovy.json"),
        "scala" to LanguageInfo("source.scala", "scala.json"),
        "clj" to LanguageInfo("source.clojure", "clojure.json"),
        "clojure" to LanguageInfo("source.clojure", "clojure.json"),

        // Web
        "html" to LanguageInfo("text.html.basic", "html.json"),
        "htm" to LanguageInfo("text.html.basic", "html.json"),
        "css" to LanguageInfo("source.css", "css.json"),
        "scss" to LanguageInfo("source.css.scss", "scss.json"),
        "js" to LanguageInfo("source.js", "javascript.json"),
        "javascript" to LanguageInfo("source.js", "javascript.json"),
        "jsx" to LanguageInfo("source.js.jsx", "jsx.json"),
        "ts" to LanguageInfo("source.ts", "typescript.json"),
        "typescript" to LanguageInfo("source.ts", "typescript.json"),
        "tsx" to LanguageInfo("source.tsx", "tsx.json"),
        "vue" to LanguageInfo("text.html.vue", "vue.json"),
        "php" to LanguageInfo("source.php", "php.json"),

        // Scripting/Others
        "py" to LanguageInfo("source.python", "python.json"),
        "python" to LanguageInfo("source.python", "python.json"),
        "rb" to LanguageInfo("source.ruby", "ruby.json"),
        "ruby" to LanguageInfo("source.ruby", "ruby.json"),
        "go" to LanguageInfo("source.go", "go.json"),
        "rs" to LanguageInfo("source.rust", "rust.json"),
        "rust" to LanguageInfo("source.rust", "rust.json"),
        "swift" to LanguageInfo("source.swift", "swift.json"),
        "lua" to LanguageInfo("source.lua", "lua.json"),
        "dart" to LanguageInfo("source.dart", "dart.json"),
        "sh" to LanguageInfo("source.shell", "shell.json"), // Note: some might use bash.json if available
        "sql" to LanguageInfo("source.sql", "sql.json"),
        "zig" to LanguageInfo("source.zig", "zig.json"),
        "nim" to LanguageInfo("source.nim", "nim.json"),
        "jl" to LanguageInfo("source.julia", "julia.json"),
        "julia" to LanguageInfo("source.julia", "julia.json"),
        "ex" to LanguageInfo("source.elixir", "elixir.json"),
        "exs" to LanguageInfo("source.elixir", "elixir.json"),
        "elixir" to LanguageInfo("source.elixir", "elixir.json"),
        "fs" to LanguageInfo("source.fsharp", "fsharp.json"),
        "fsharp" to LanguageInfo("source.fsharp", "fsharp.json"),
        "hs" to LanguageInfo("source.haskell", "haskell.json"),
        "haskell" to LanguageInfo("source.haskell", "haskell.json"),
        "ml" to LanguageInfo("source.ocaml", "ocaml.json"),
        "ocaml" to LanguageInfo("source.ocaml", "ocaml.json"),
        "pl" to LanguageInfo("source.perl", "perl.json"),
        "perl" to LanguageInfo("source.perl", "perl.json"),
        "r" to LanguageInfo("source.r", "r.json"),

        // Config/Data
        "json" to LanguageInfo("source.json", "json.json"),
        "jsonc" to LanguageInfo("source.json.comments", "jsonc.json"),
        "xml" to LanguageInfo("text.xml", "xml.json"),
        "yaml" to LanguageInfo("source.yaml", "yaml.json"),
        "yml" to LanguageInfo("source.yaml", "yaml.json"),
        "toml" to LanguageInfo("source.toml", "toml.json"),
        "md" to LanguageInfo("text.html.markdown", "markdown.json"),
        "markdown" to LanguageInfo("text.html.markdown", "markdown.json"),
        "properties" to LanguageInfo("source.properties", "properties.json")
    )

    data class LanguageInfo(val scopeName: String, val grammarFile: String)

    fun getLanguageInfo(extension: String): LanguageInfo? = extensionToLanguage[extension.lowercase()]

    fun isCodeFile(file: File): Boolean {
        return getLanguageInfo(file.extension) != null || 
               file.extension.lowercase() in listOf("sh", "bash", "properties", "gradle", "kts")
    }

    fun getFileIcon(file: File): ImageVector {
        if (file.isDirectory) return Icons.Default.Folder
        val ext = file.extension.lowercase()
        return when {
            ext == "txt" -> Icons.AutoMirrored.Filled.Article
            ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image
            ext in listOf("mp4", "mkv", "mov", "webm") -> Icons.Default.Movie
            ext in listOf("mp3", "wav", "flac") -> Icons.Default.Audiotrack
            ext == "pdf" -> Icons.Default.PictureAsPdf
            isCodeFile(file) -> Icons.Default.Code
            else -> Icons.Default.Description
        }
    }

    fun getIconTint(file: File): Color {
        if (file.isDirectory) return Color(0xFF2196F3) // Folder Blue
        val ext = file.extension.lowercase()
        return when {
            isCodeFile(file) -> Color(0xFF673AB7) // Purple for code
            ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Color(0xFFE91E63) // Pink for images
            ext in listOf("mp4", "mkv", "mov", "webm") -> Color(0xFFFF9800) // Orange for movie
            ext in listOf("mp3", "wav", "flac") -> Color(0xFF00BCD4) // Cyan for audio
            ext == "txt" -> Color(0xFF4CAF50) // Green for text
            else -> Color.Gray
        }
    }
}
