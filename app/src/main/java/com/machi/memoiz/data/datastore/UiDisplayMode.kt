package com.machi.memoiz.data.datastore

enum class UiDisplayMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromString(value: String?): UiDisplayMode {
            return try {
                if (value == null) SYSTEM else valueOf(value)
            } catch (_: Exception) {
                SYSTEM
            }
        }
    }
}

