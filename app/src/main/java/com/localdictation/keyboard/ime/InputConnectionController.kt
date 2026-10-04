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

    fun deleteLastWord(): Boolean {
        val connection = connectionProvider() ?: return false
        val textBeforeCursor = connection.getTextBeforeCursor(MAX_WORD_CONTEXT, 0)?.toString()
            ?: return false
        if (textBeforeCursor.isEmpty()) return false

        var wordEnd = textBeforeCursor.length
        while (wordEnd > 0 && textBeforeCursor[wordEnd - 1].isWhitespace()) {
            wordEnd--
        }
        if (wordEnd == 0) {
            return deleteBefore(connection, textBeforeCursor, 0)
        }

        var wordStart = wordEnd
        while (wordStart > 0 && !textBeforeCursor[wordStart - 1].isWhitespace()) {
            wordStart--
        }
        return deleteBefore(connection, textBeforeCursor, wordStart)
    }

    private fun deleteBefore(connection: InputConnection, text: String, start: Int): Boolean {
        val utf16Length = text.length - start
        val codePointLength = text.codePointCount(start, text.length)
        return connection.deleteSurroundingTextInCodePoints(codePointLength, 0) ||
            connection.deleteSurroundingText(utf16Length, 0)
    }

    private companion object {
        const val MAX_WORD_CONTEXT = 4_096
    }
}
