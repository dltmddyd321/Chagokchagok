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
            is LoadCheckIntent.ManualChanged -> updateManual {
                when (intent.field) {
                    ManualField.NAME -> it.copy(name = intent.value.take(MAX_NAME_LENGTH))
                    ManualField.WIDTH -> it.copy(width = intent.value.digits())
                    ManualField.DEPTH -> it.copy(depth = intent.value.digits())
                    ManualField.HEIGHT -> it.copy(height = intent.value.digits())
                }
            }
            is LoadCheckIntent.ManualQuantity -> _state.update { s ->
                val input = s.manualInput ?: return@update s
                // 남은 자리(최대 10개)를 넘지 않게
                val room = LoadCheckUiState.MAX_ITEMS - s.itemCount
                s.copy(manualInput = input.copy(quantity = (input.quantity + intent.delta).coerceIn(1, room.coerceAtLeast(1))))
            }
            LoadCheckIntent.ConfirmManualInput -> _state.update { s ->
                val item = s.manualInput?.toItem("manual-${UUID.randomUUID()}") ?: return@update s
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

    private fun String.digits() = filter(Char::isDigit).take(3)

    private companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
