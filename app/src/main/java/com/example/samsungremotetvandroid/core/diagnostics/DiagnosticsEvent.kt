package com.example.samsungremotetvandroid.core.diagnostics

data class DiagnosticsEvent(
    val category: DiagnosticsCategory,
    val message: String,
    val metadata: Map<String, String>
) {
    fun toDisplayString(): String {
        val metadataText = metadata
            .toSortedMap()
            .entries
            .joinToString(separator = " ") { (key, value) -> "$key=$value" }

        return if (metadataText.isBlank()) {
            "[${category.wireName}] $message"
        } else {
            "[${category.wireName}] $message $metadataText"
        }
    }
}
