package com.carocall.gitmobile.data.model

data class GitProgress(
    val taskName: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val indeterminate: Boolean = true
) {
    val progress: Float
        get() = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    
    val displayString: String
        get() = buildString {
            append(taskName)
            if (!indeterminate && total > 0) {
                append(" ($completed/$total)")
            }
        }
}
