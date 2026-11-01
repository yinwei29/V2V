package com.yinwei.v2v.ui

import androidx.lifecycle.ViewModel

/** Explicit export snapshot survives rotation in memory, never saved to Bundle/log/disk. */
class HistoryExportState : ViewModel() {
    var pending: String? = null
    var skippedInvalid: Int = 0
    override fun onCleared() { pending = null; skippedInvalid = 0 }
}
