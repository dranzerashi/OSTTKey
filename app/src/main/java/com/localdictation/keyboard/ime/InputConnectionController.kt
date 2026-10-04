package com.localdictation.keyboard.ime

import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest

class InputConnectionController(
    private val connectionProvider: () -> InputConnection?,
) {
    private var wordSelectionSession: WordSelectionSession? = null

    fun insertAtCursor(text: String): Boolean {
        if (text.isEmpty()) return false
        val connection = connectionProvider() ?: return false
        return connection.commitText(text, 1)
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

    fun beginWordSelection(): Boolean {
        val connection = connectionProvider() ?: return false
        val request = ExtractedTextRequest().apply {
            hintMaxChars = MAX_WORD_CONTEXT
            hintMaxLines = 1
        }
        val extractedText = connection.getExtractedText(request, 0) ?: return false
        if (extractedText.startOffset < 0 || extractedText.selectionStart < 0 ||
            extractedText.selectionStart != extractedText.selectionEnd
        ) {
            return false
        }

        val cursor = extractedText.startOffset + extractedText.selectionEnd
        if (cursor < 0) return false
        val textBeforeCursor = connection.getTextBeforeCursor(MAX_WORD_CONTEXT, 0)?.toString()
            ?: return false
        if (textBeforeCursor.isEmpty()) return false

        val wordStarts = findWordStarts(textBeforeCursor)
        if (wordStarts.isEmpty()) return false
        cancelWordSelection()
        wordSelectionSession = WordSelectionSession(
            connection = connection,
            cursor = cursor,
            textBeforeCursor = textBeforeCursor,
            wordStarts = wordStarts,
        )
        return true
    }

    fun updateWordSelection(wordCount: Int): Int {
        val session = wordSelectionSession ?: return 0
        val count = wordCount.coerceIn(0, session.wordStarts.size)
        val selectionStart = if (count == 0) {
            session.cursor
        } else {
            session.cursor - session.textBeforeCursor.length + session.wordStarts[count - 1]
        }
        val applied = session.connection.setSelection(selectionStart, session.cursor)
        if (applied) {
            session.selectedWordCount = count
        }
        return session.selectedWordCount
    }

    fun finishWordSelection(deleteSelectedWords: Boolean): Boolean {
        val session = wordSelectionSession ?: return false
        wordSelectionSession = null
        if (deleteSelectedWords && session.selectedWordCount > 0) {
            if (session.connection.commitText("", 1)) return true
        }
        return session.connection.setSelection(session.cursor, session.cursor)
    }

    fun cancelWordSelection() {
        finishWordSelection(deleteSelectedWords = false)
    }

    private fun deleteBefore(connection: InputConnection, text: String, start: Int): Boolean {
        val utf16Length = text.length - start
        val codePointLength = text.codePointCount(start, text.length)
        return connection.deleteSurroundingTextInCodePoints(codePointLength, 0) ||
            connection.deleteSurroundingText(utf16Length, 0)
    }

    private fun findWordStarts(text: String): List<Int> {
        val starts = mutableListOf<Int>()
        var index = text.length
        while (index > 0) {
            while (index > 0 && text[index - 1].isWhitespace()) index--
            if (index == 0) break
            while (index > 0 && !text[index - 1].isWhitespace()) index--
            starts += index
        }
        return starts
    }

    private data class WordSelectionSession(
        val connection: InputConnection,
        val cursor: Int,
        val textBeforeCursor: String,
        val wordStarts: List<Int>,
        var selectedWordCount: Int = 0,
    )

    private companion object {
        const val MAX_WORD_CONTEXT = 4_096
    }
}
