package com.windrr.hyundae.speech

import ai.pleos.playground.stt.constant.Mode
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val selectableModes = listOf(Mode.ON_DEVICE, Mode.SERVER, Mode.HYBRID)

@Composable
fun SpeechInputScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(Mode.ON_DEVICE) }
    val controller = remember(mode) { SpeechInputController(context, mode) }
    val state by controller.state.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) controller.start()
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    LaunchedEffect(state.finalResultCount) {
        if (state.finalResultCount > 0) inputText = state.finalText
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "음성으로 입력하기 (임시)", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectableModes.forEach { candidate ->
                if (candidate == mode) {
                    Button(onClick = {}) { Text(candidate.name) }
                } else {
                    OutlinedButton(onClick = { mode = candidate }) { Text(candidate.name) }
                }
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("인식된 텍스트") },
            minLines = 3,
        )

        if (state.partialText.isNotBlank()) {
            Text(text = state.partialText, style = MaterialTheme.typography.bodyLarge)
        }

        Text(text = statusMessage(state), style = MaterialTheme.typography.bodyMedium)

        val isListening = state.status == SpeechInputStatus.LISTENING
        Button(
            onClick = {
                when {
                    isListening -> controller.stop()
                    hasMicPermission -> controller.start()
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = state.status != SpeechInputStatus.PREPARING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isListening) "듣기 멈추기" else "말하기")
        }
    }
}

private fun statusMessage(state: SpeechInputState): String = when (state.status) {
    SpeechInputStatus.IDLE -> "말하기를 누르면 음성 인식을 시작해요"
    SpeechInputStatus.PREPARING -> "음성 인식을 준비하고 있어요"
    SpeechInputStatus.READY -> "말하기를 누르고 말씀해 주세요"
    SpeechInputStatus.LISTENING -> "듣고 있어요"
    SpeechInputStatus.ERROR -> state.errorMessage ?: "음성 인식에 실패했어요"
}
