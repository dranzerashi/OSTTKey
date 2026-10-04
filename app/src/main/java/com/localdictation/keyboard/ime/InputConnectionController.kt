package com.localdictation.keyboard.ime

import android.view.inputmethod.InputConnection

class InputConnectionController(
    private val connectionProvider: () -> InputConnection?,
) {
    fun insertAtCursor(text: String): Boolean {
        if (text.isEmpty()) return false
        val connection = connectionProvider() ?: return false
        return connection.commitText(text, 1)
    }

    fun deleteBackward(): Boolean {
        val connection = connectionProvider() ?: return false
        return connection.deleteSurroundingTextInCodePoints(1, 0) ||
            connection.deleteSurroundingText(1, 0)
    }
}
