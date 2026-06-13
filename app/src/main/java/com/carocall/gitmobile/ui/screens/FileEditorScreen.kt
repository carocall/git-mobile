package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.carocall.gitmobile.utils.isBinaryFile
import com.carocall.gitmobile.utils.openFileExternally
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
            CodeEditor(file, onBack)
        
        // 3. 图片预览
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> 
            ImageViewer(file, onBack)
            
        // 4. 音视频预览 (架构占位)
        ext in listOf("mp4", "mkv", "mov", "mp3", "wav", "flac") ->
            MediaViewer(file, onBack)
            
        // 5. 其他
        else -> {
            if (isBinaryFile(file)) {
                BinaryInfoViewer(file, onBack)
            } else {
                CodeEditor(file, onBack)
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
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = bgColor) {
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

// --- 2. 通用代码/文本编辑器 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditor(file: File, onBack: () -> Unit) {
    var text by remember { mutableStateOf(file.readText()) }
    var original by remember { mutableStateOf(text) }
    val lines = text.lines()
    val horizontalScrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { file.writeText(text); original = text }, enabled = text != original) { Icon(Icons.Default.Save, null) }
                }
            )
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // 简单的行号列
            Column(Modifier.width(40.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(top = 16.dp), horizontalAlignment = Alignment.End) {
                for (i in 1..lines.size) {
                    Text("$i ", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace, modifier = Modifier.height(20.dp))
                }
            }
            
            Box(Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxHeight().width(2000.dp), // 使用较大宽度模拟不换行
                    textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
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

// --- 5. 音视频查看器 (占位) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewer(file: File, onBack: () -> Unit) {
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
                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("媒体播放器", style = MaterialTheme.typography.titleLarge)
                Text("即将支持：${file.extension.uppercase()} 格式播放", color = Color.Gray)
                Spacer(Modifier.height(32.dp))
                Card(Modifier.padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("文件信息", style = MaterialTheme.typography.titleSmall)
                        Text("路径: ${file.absolutePath}", fontSize = 12.sp, color = Color.Gray)
                        Text("大小: ${file.length() / 1024 / 1024} MB", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
