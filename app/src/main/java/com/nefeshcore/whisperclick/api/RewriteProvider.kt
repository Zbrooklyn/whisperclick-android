package com.nefeshcore.whisperclick.api

interface RewriteProvider {
    val name: String
    suspend fun rewriteText(apiKey: String, originalText: String, style: String): String
}
