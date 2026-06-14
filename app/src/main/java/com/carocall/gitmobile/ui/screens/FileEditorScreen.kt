package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.carocall.gitmobile.utils.isBinaryFile
import com.carocall.gitmobile.utils.openFileExternally
import io.github.rosemoe.sora.widget.CodeEditor as SoraEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.Content
import java.io.File

/**
 * 文件查看器/编辑器分发器 (工作区模式)
 */
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val ext = file.extension.lowercase()
    
    when {
        // 1. 小说文本 (.txt)
        ext == "txt" -> NovelEditor(file, onBack)
        
        // 2. 通用文本 (代码, 配置, Markdown 等)
        ext in listOf("java", "kt", "py", "md", "xml", "json", "yaml", "toml", "properties", "gradle", "kts", "c", "cpp", "h", "js", "ts", "sh") -> 
            SoraCodeEditor(file, onBack)
        
        // 3. 图片预览
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> 
            ImageViewer(file, onBack)
            
        // 4. 音视频预览 (架构占位)
        ext in listOf("mp4", "mkv", "mov", "webm", "mp3", "wav", "flac") ->
            MediaViewer(file, onBack)
            
        // 5. 其他
        else -> {
            if (isBinaryFile(file)) {
                BinaryInfoViewer(file, onBack)
            } else {
                SoraCodeEditor(file, onBack)
            }
        }
    }
}

// --- 1. 小说编辑器 (特色功能) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelEditor(file: File, onBack: () -> Unit) {
    var text by remember { mutableStateOf(file.readText()) }
    var original by remember { mutableStateOf(text) }
    
    var fontSize by remember { mutableFloatStateOf(18f) }
    var bgColor by remember { mutableStateOf(Color(0xFFF5F2E9)) } 
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val themes = listOf(Color(0xFFFFFFFF), Color(0xFFF5F2E9), Color(0xFFE8F5E9), Color(0xFFE3F2FD), Color(0xFF1A1A1A))

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp, color = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) } },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null, tint = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) }
                    IconButton(onClick = { file.writeText(text); original = text }, enabled = text != original) { Icon(Icons.Default.Save, null, tint = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgColor)
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding).imePadding(), color = bgColor) {
            TextField(
                value = text,
                onValueChange = { 
                    if (it.length > text.length && it.endsWith("\n")) text = it + "\u3000\u3000" else text = it 
                },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.8f).sp,
                    color = if (bgColor == Color(0xFF1A1A1A)) Color.LightGray else Color(0xFF2C2C2C),
                    textIndent = TextIndent(firstLine = fontSize.sp * 2)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black
                )
            )
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = sheetState) {
                Column(Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                    Text("编辑器设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", modifier = Modifier.width(48.dp))
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..32f, modifier = Modifier.weight(1f))
                        Text("${fontSize.toInt()}", modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("背景颜色")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                        themes.forEach { color ->
                            Box(modifier = Modifier.size(40.dp).background(color, CircleShape).border(if (bgColor == color) 2.dp else 1.dp, if (bgColor == color) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape).clickable { bgColor = color })
                        }
                    }
                }
            }
        }
    }
}

// --- 2. Sora 代码/文本编辑器 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoraCodeEditor(file: File, onBack: () -> Unit) {
    var originalText by remember { mutableStateOf(file.readText()) }
    var editorInstance by remember { mutableStateOf<SoraEditor?>(null) }
    var hasChanges by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { editorInstance?.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = { editorInstance?.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                    }
                    IconButton(
                        onClick = {
                            editorInstance?.let { editor ->
                                val text = editor.text.toString()
                                try {
                                    file.writeText(text)
                                    originalText = text
                                    hasChanges = false
                                } catch (e: Exception) {}
                            }
                        },
                        enabled = hasChanges
                    ) { Icon(Icons.Default.Save, contentDescription = "保存") }
                }
            )
        }
    ) { padding ->
        // 使用 Column 并设置 weight 以便键盘弹出时自动避让
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SoraEditor(ctx).apply {
                        setText(originalText)
                        isLineNumberEnabled = true
                        
                        // 不设置任何语言包，仅作为纯文本编辑器使用
                        
                        // 主题适配 (仅基础配色)
                        colorScheme = if (isDark) {
                            EditorColorScheme().apply {
                                setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFF1E1E1E).toArgb())
                                setColor(EditorColorScheme.TEXT_NORMAL, Color.LightGray.toArgb())
                                setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFF252526).toArgb())
                                setColor(EditorColorScheme.LINE_NUMBER, Color(0xFF858585).toArgb())
                            }
                        } else {
                            EditorColorScheme()
                        }
                        
                        // 监听内容变化及撤销栈状态
                        text.addContentListener(object : ContentListener {
                            override fun beforeReplace(content: Content) {}
                            override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, insertedContent: CharSequence) {
                                updateUIState(content)
                            }
                            override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deletedContent: CharSequence) {
                                updateUIState(content)
                            }
                            
                            private fun updateUIState(content: Content) {
                                hasChanges = content.toString() != originalText
                                canUndo = canUndo()
                                canRedo = canRedo()
                            }
                        })
                        
                        editorInstance = this
                    }
                },
                update = { view ->
                    view.colorScheme = if (isDark) {
                        EditorColorScheme().apply {
                            setColor(EditorColorScheme.WHOLE_BACKGROUND, Color(0xFF1E1E1E).toArgb())
                            setColor(EditorColorScheme.TEXT_NORMAL, Color.LightGray.toArgb())
                            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color(0xFF252526).toArgb())
                            setColor(EditorColorScheme.LINE_NUMBER, Color(0xFF858585).toArgb())
                        }
                    } else {
                        EditorColorScheme()
                    }
                }
            )
        }
    }
}

// --- 3. 图片查看器 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewer(file: File, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("文件信息", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("路径: ${file.absolutePath}", fontSize = 12.sp, color = Color.Gray)
                    Text("大小: ${file.length() / 1024} KB", fontSize = 12.sp, color = Color.Gray)
                    Text("最后修改: ${java.util.Date(file.lastModified())}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// --- 4. 二进制/不支持的文件查看器 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinaryInfoViewer(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(Modifier.height(16.dp))
                Text("不支持直接预览此文件类型", style = MaterialTheme.typography.titleMedium)
                Text("后缀: ${file.extension}", color = Color.Gray)
                Text("大小: ${file.length() / 1024} KB", color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { openFileExternally(context, file) }) {
                    Text("使用系统应用打开")
                }
            }
        }
    }
}

// --- 5. 音视频查看器 (真实播放器) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewer(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(file.absolutePath)
            setMediaItem(mediaItem)
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("媒体信息", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("类型: ${file.extension.uppercase()}", fontSize = 12.sp, color = Color.Gray)
                    Text("大小: ${file.length() / 1024 / 1024} MB", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}
