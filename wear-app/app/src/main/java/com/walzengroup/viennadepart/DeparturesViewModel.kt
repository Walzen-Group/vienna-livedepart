package com.walzengroup.viennadepart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walzengroup.viennadepart.data.DeparturesRepository
import com.walzengroup.viennadepart.data.DeparturesUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads departures on open and exposes a simple Loading / Success / Error state. */
class DeparturesViewModel(
    private val repository: DeparturesRepository = DeparturesRepository(),
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Success(val ui: DeparturesUi) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            _state.value = try {
                State.Success(repository.loadHardcodedStop())
            } catch (e: Exception) {
                State.Error(e.message ?: "Could not load departures")
            }
        }
    }
}
