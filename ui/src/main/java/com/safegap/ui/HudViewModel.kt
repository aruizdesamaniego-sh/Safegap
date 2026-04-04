package com.safegap.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HudViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()
}
