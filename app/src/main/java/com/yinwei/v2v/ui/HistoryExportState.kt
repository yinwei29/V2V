package com.yinwei.v2v.ui

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/** Explicit export snapshot survives rotation in memory, never saved to Bundle/log/disk. */
class HistoryExportState : ViewModel() {
    var pending: String? by mutableStateOf(null)
    var skippedInvalid: Int = 0
    override fun onCleared() { pending = null; skippedInvalid = 0 }
}
