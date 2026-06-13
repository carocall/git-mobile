package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.utils.isBinaryFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val isBinary = remember(file) { isBinaryFile(file) }
    var text by remember { mutableStateOf(if (!isBinary) file.readText() else "") }
    var original by remember { mutableStateOf(text) }
    
    // 编辑器设置状态
    var fontSize by remember { mutableFloatStateOf(18f) }
    var bgColor by remember { mutableStateOf(Color(0xFFF5F2E9)) } // 默认米色/羊皮纸色
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val themes = listOf(
        Color(0xFFFFFFFF), // 纯白
        Color(0xFFF5F2E9), // 米黄 (护眼)
        Color(0xFFE8F5E9), // 淡绿
        Color(0xFFE3F2FD), // 淡蓝
        Color(0xFF1A1A1A)  // 深色
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (!isBinary) {
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null) }
                        IconButton(onClick = { file.writeText(text); original = text }, enabled = text != original) { Icon(Icons.Default.Save, null) }
                    }
                }
            )
        }
    ) { padding ->
        if (isBinary) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("不支持二进制") }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize().padding(padding),
                color = bgColor
            ) {
                TextField(
                    value = text,
                    onValueChange = { 
                        // 简单的自动缩进逻辑：如果输入换行符，自动补全两个全角空格
                        if (it.length > text.length && it.endsWith("\n")) {
                            text = it + "\u3000\u3000"
                        } else {
                            text = it 
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.8f).sp, // 较大的行间距
                        color = if (bgColor == Color(0xFF1A1A1A)) Color.LightGray else Color(0xFF2C2C2C),
                        textIndent = TextIndent(firstLine = fontSize.sp * 2) // 首行缩进
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
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                    Text("编辑器设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    
                    // 字号调节
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", modifier = Modifier.width(48.dp))
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 12f..32f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${fontSize.toInt()}", modifier = Modifier.padding(start = 8.dp))
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // 背景颜色切换
                    Text("背景颜色")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        themes.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .border(if (bgColor == color) 2.dp else 1.dp, if (bgColor == color) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                                    .clickable { bgColor = color }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    // 其他选项 (参考图片)
                    ListItem(
                        headlineContent = { Text("历史记录") },
                        trailingContent = { Icon(Icons.Default.Save, null, Modifier.size(18.dp)) },
                        modifier = Modifier.clickable { /* TODO */ }
                    )
                    ListItem(
                        headlineContent = { Text("屏幕常亮") },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}) }
                    )
                }
            }
        }
    }
}
