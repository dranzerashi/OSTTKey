package com.localdictation.keyboard.ime

sealed interface DictationKeyboardState {
    data object Idle : DictationKeyboardState
    data class Recording(val elapsedMillis: Long) : DictationKeyboardState
    data object Processing : DictationKeyboardState
    data object ModelRequired : DictationKeyboardState
    data object PermissionRequired : DictationKeyboardState
    data class Error(val message: String) : DictationKeyboardState
    data object Inserted : DictationKeyboardState
}
