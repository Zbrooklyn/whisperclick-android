package com.nefeshcore.whisperclick.api

data class RewriteVariants(
    val clean: String,
    val professional: String,
    val casual: String,
    val concise: String,
    val emojify: String
) {
    fun toList(): List<Pair<String, String>> = listOf(
        "Clean" to clean,
        "Professional" to professional,
        "Casual" to casual,
        "Concise" to concise,
        "Emojify" to emojify
    )
}

interface RewriteProvider {
    val name: String
    suspend fun rewriteText(apiKey: String, originalText: String, style: String): String
    suspend fun rewriteAll(apiKey: String, originalText: String): RewriteVariants
}
