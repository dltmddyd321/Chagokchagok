package com.windrr.hyundae.ui.loadcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class LoadCheckViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoadCheckUiState())
    val state: StateFlow<LoadCheckUiState> = _state.asStateFlow()

    private val _effect = Channel<LoadCheckEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: LoadCheckIntent) {
        when (intent) {
            LoadCheckIntent.IncreaseCompanions -> _state.update {
                it.copy(companions = (it.companions + 1).coerceAtMost(LoadCheckUiState.MAX_COMPANIONS))
            }
            LoadCheckIntent.DecreaseCompanions -> _state.update { it.copy(companions = (it.companions - 1).coerceAtLeast(0)) }
            is LoadCheckIntent.RemoveItem -> _state.update { s -> s.copy(items = s.items.filterNot { it.key == intent.key }) }
            LoadCheckIntent.OpenManualInput -> _state.update { if (it.canAddItem) it.copy(manualInput = ManualInputState()) else it }
            is LoadCheckIntent.OpenManualInputWithName -> _state.update {
                if (it.canAddItem) it.copy(manualInput = ManualInputState(name = intent.name.take(MAX_NAME_LENGTH))) else it
            }
            LoadCheckIntent.CloseManualInput -> _state.update { it.copy(manualInput = null) }
            is LoadCheckIntent.SelectField -> updateManual { it.copy(selected = intent.field) }
            is LoadCheckIntent.StepField -> updateManual {
                val current = it.valueOf(intent.field).toIntOrNull() ?: 0
                val next = (current + intent.delta).coerceIn(0, LoadCheckUiState.MAX_CM)
                it.with(intent.field, if (next == 0) "" else next.toString()).copy(selected = intent.field)
            }
            is LoadCheckIntent.KeypadDigit -> updateManual {
                val current = it.valueOf(it.selected)
                val next = (current + intent.digit).trimStart('0')
                // 최대 3자리, 300cm 이하
                if (next.length > 3 || (next.toIntOrNull() ?: 0) > LoadCheckUiState.MAX_CM) it else it.with(it.selected, next)
            }
            LoadCheckIntent.KeypadClear -> updateManual { it.with(it.selected, "") }
            LoadCheckIntent.KeypadBackspace -> updateManual { it.with(it.selected, it.valueOf(it.selected).dropLast(1)) }
            LoadCheckIntent.ConfirmManualInput -> _state.update { s ->
                val item = s.manualInput?.toItem("manual-${UUID.randomUUID()}", "짐 ${s.items.size + 1}") ?: return@update s
                if (s.itemCount + item.quantity > LoadCheckUiState.MAX_ITEMS) return@update s
                s.copy(items = s.items + item, manualInput = null)
            }
            LoadCheckIntent.Check -> {
                val s = _state.value
                if (!s.canCheck) return
                viewModelScope.launch { _effect.send(LoadCheckEffect.RequestCheck(s.items, s.totalPassengers)) }
            }
        }
    }

    private fun updateManual(transform: (ManualInputState) -> ManualInputState) {
        _state.update { s -> s.manualInput?.let { s.copy(manualInput = transform(it)) } ?: s }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
