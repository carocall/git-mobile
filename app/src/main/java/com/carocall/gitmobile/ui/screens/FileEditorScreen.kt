package com.carocall.gitmobile.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.carocall.gitmobile.utils.isBinaryFile
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(file: File, onBack: () -> Unit) {
    val isBinary = remember(file) { isBinaryFile(file) }
    var text by remember { mutableStateOf(if (!isBinary) file.readText() else "") }
    var original by remember { mutableStateOf(text) }
    val undoStack = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(file.name, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (!isBinary) {
                        IconButton(onClick = { file.writeText(text); original = text }, enabled = text != original) { Icon(Icons.Default.Save, null) }
                    }
                }
            )
        }
    ) { padding ->
        if (isBinary) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("不支持二进制") }
        else TextField(text, { if (it != text) { undoStack.add(text); text = it } }, Modifier.fillMaxSize().padding(padding))
    }
}