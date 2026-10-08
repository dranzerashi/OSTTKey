package com.localdictation.keyboard

import android.content.Context
import androidx.core.content.edit

object DictationModePreferences {
    private const val PREFERENCES_NAME = "dictation_settings"
    private const val KEY_PAUSE_AWARE_MODE_ENABLED = "pause_aware_mode_enabled"

    fun isPauseAwareEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAUSE_AWARE_MODE_ENABLED, false)

    fun setPauseAwareEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_PAUSE_AWARE_MODE_ENABLED, enabled)
            }
    }
}
