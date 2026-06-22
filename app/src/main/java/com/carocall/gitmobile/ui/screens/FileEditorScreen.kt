package com.carocall.gitmobile.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.carocall.gitmobile.R
import com.carocall.gitmobile.data.SettingsManager
import com.carocall.gitmobile.data.model.RecentFile
import com.carocall.gitmobile.utils.EditorConfig
import com.carocall.gitmobile.utils.isBinaryFile
import com.carocall.gitmobile.utils.openFileExternally
import io.github.rosemoe.sora.widget.CodeEditor as SoraEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import org.eclipse.tm4e.core.registry.IGrammarSource
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 文件查看器/编辑器分发器 (工作区模式)
 */
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    // 记录最近访问
    LaunchedEffect(file.absolutePath) {
        val repoName = file.parentFile?.name ?: "Unknown"
        settingsManager.addRecentFile(
            RecentFile(
                path = file.absolutePath,
                name = file.name,
                repoName = repoName
            )
        )
    }

    val ext = file.extension.lowercase()
    
    when {
        // 1. 小说文本 (.txt)
        ext == "txt" -> NovelEditor(file, onBack)
        
        // 2. 图片预览
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> 
            ImageViewer(file, onBack)
            
        // 3. 音视频预览
        ext in listOf("mp4", "mkv", "mov", "webm", "mp3", "wav", "flac") ->
            MediaViewer(file, onBack)
            
        // 4. 代码/通用文本 (根据 EditorConfig 判断)
        EditorConfig.isCodeFile(file) -> SoraCodeEditor(file, onBack)

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
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(file.readText()) }
    var original by remember { mutableStateOf(text) }
    var showExitDialog by remember { mutableStateOf(false) }
    val hasChanges = text != original

    val handleBack = {
        if (hasChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true, onBack = handleBack)
    
    val savedFontSize by settingsManager.novelFontSizeFlow.collectAsState(initial = 18f)
    val savedIsSerif by settingsManager.novelIsSerifFlow.collectAsState(initial = true)
    val savedBgColorInt by settingsManager.novelBgColorFlow.collectAsState(initial = null)

    var fontSize by remember(savedFontSize) { mutableFloatStateOf(savedFontSize) }
    var isSerif by remember(savedIsSerif) { mutableStateOf(savedIsSerif) }
    var bgColor by remember(savedBgColorInt) { 
        mutableStateOf(savedBgColorInt?.let { Color(it) } ?: Color(0xFFF5F2E9)) 
    } 

    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val themes = listOf(Color(0xFFFFFFFF), Color(0xFFF5F2E9), Color(0xFFE8F5E9), Color(0xFFE3F2FD), Color(0xFF1A1A1A))

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(file.name, fontSize = 14.sp, color = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black)
                        Text(
                            text = stringResource(R.string.word_count, text.length),
                            fontSize = 10.sp,
                            color = if (bgColor == Color(0xFF1A1A1A)) Color.Gray else Color.DarkGray
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) } },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null, tint = if (bgColor == Color(0xFF1A1A1A)) Color.White else Color.Black) }
                    IconButton(
                        onClick = {
                            try {
                                file.writeText(text)
                                original = text
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Icon(
                            Icons.Default.Save, 
                            contentDescription = stringResource(R.string.save), 
                            tint = if (bgColor == Color(0xFF1A1A1A)) {
                                if (hasChanges) Color.White else Color.Gray
                            } else {
                                if (hasChanges) Color.Black else Color.LightGray
                            }
                        )
                    }
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 2.0f).sp,
                    fontFamily = if (isSerif) FontFamily.Serif else FontFamily.SansSerif,
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
                    Text(stringResource(R.string.editor_settings), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    
                    // 字体设置
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.serif_font), modifier = Modifier.weight(1f))
                        Switch(
                            checked = isSerif,
                            onCheckedChange = { 
                                isSerif = it 
                                scope.launch { settingsManager.saveNovelIsSerif(it) }
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.font_size), modifier = Modifier.width(48.dp))
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            onValueChangeFinished = {
                                scope.launch { settingsManager.saveNovelFontSize(fontSize) }
                            },
                            valueRange = 12f..32f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${fontSize.toInt()}", modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.bg_color))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                        themes.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        if (bgColor == color) 2.dp else 1.dp,
                                        if (bgColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        CircleShape
                                    )
                                    .clickable { 
                                        bgColor = color 
                                        scope.launch { settingsManager.saveNovelBgColor(color.toArgb()) }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.unsaved_content)) },
            text = { Text(stringResource(R.string.unsaved_exit_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.discard_changes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
    var showExitDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    // 初始化 TextMate 引擎
    LaunchedEffect(file.extension) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 加载主题
                val themePath = "themes/one-dark-pro.json"
                val themeSource = IThemeSource.fromInputStream(
                    context.assets.open(themePath),
                    themePath,
                    Charsets.UTF_8
                )
                ThemeRegistry.getInstance().loadTheme(themeSource)
                
                // 2. 注册当前文件需要的语法 (按需加载)
                val langInfo = EditorConfig.getLanguageInfo(file.extension)
                if (langInfo != null) {
                    val grammarPath = "grammars/${langInfo.grammarFile}"
                    try {
                        val grammarSource = IGrammarSource.fromInputStream(
                            context.assets.open(grammarPath),
                            grammarPath,
                            Charsets.UTF_8
                        )
                        val grammarDef = DefaultGrammarDefinition.withGrammarSource(
                            grammarSource, file.extension, langInfo.scopeName
                        )
                        GrammarRegistry.getInstance().loadGrammar(grammarDef)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 通知编辑器更新语言和配色
        editorInstance?.let { editor ->
            val langInfo = EditorConfig.getLanguageInfo(file.extension)
            if (langInfo != null) {
                try {
                    editor.setEditorLanguage(TextMateLanguage.create(langInfo.scopeName, true))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // 使用 TextMate 的配色方案
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
    }

    val handleBack = {
        if (hasChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true, onBack = handleBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { editorInstance?.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    IconButton(onClick = { editorInstance?.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
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
                    ) { Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save)) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SoraEditor(ctx).apply {
                        setText(originalText)
                        isLineNumberEnabled = true
                        
                        // 应用初始配色
                        colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                        
                        // 设置语言
                        val langInfo = EditorConfig.getLanguageInfo(file.extension)
                        if (langInfo != null) {
                            try {
                                setEditorLanguage(TextMateLanguage.create(langInfo.scopeName, true))
                            } catch (e: Exception) {}
                        }
                        
                        // 监听内容变化
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
                update = { _ -> }
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.unsaved_content)) },
            text = { Text(stringResource(R.string.unsaved_exit_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.discard_changes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
                    Text(stringResource(R.string.file_info), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.path_label, file.absolutePath), fontSize = 12.sp, color = Color.Gray)
                    Text(stringResource(R.string.size_label, "${file.length() / 1024} KB"), fontSize = 12.sp, color = Color.Gray)
                    Text(stringResource(R.string.last_modified_label, java.util.Date(file.lastModified()).toString()), fontSize = 12.sp, color = Color.Gray)
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
                Text(stringResource(R.string.preview_not_supported), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.extension_label, file.extension), color = Color.Gray)
                Text(stringResource(R.string.size_label, "${file.length() / 1024} KB"), color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { openFileExternally(context, file) }) {
                    Text(stringResource(R.string.open_externally))
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
                    Text(stringResource(R.string.media_info), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.type_label, file.extension.uppercase()), fontSize = 12.sp, color = Color.Gray)
                    Text(stringResource(R.string.size_label, "${file.length() / 1024 / 1024} MB"), fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}
