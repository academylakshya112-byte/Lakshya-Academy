package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "system_instruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inline_data") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mime_type") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class ListModelsResponse(
    @Json(name = "models") val models: List<GeminiModel>
)

@JsonClass(generateAdapter = true)
data class GeminiModel(
    @Json(name = "name") val name: String,
    @Json(name = "version") val version: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "description") val description: String,
    @Json(name = "supportedGenerationMethods") val supportedGenerationMethods: List<String>? = null
)
