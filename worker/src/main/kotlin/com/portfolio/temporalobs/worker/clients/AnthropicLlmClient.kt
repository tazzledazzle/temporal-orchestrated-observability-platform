package com.portfolio.temporalobs.worker.clients

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.portfolio.temporalobs.worker.telemetry.Metrics
import com.portfolio.temporalobs.workflows.model.LlmCompletion
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicLlmClient(
    private val apiKey: String,
    private val callFactory: Call.Factory,
    private val openTelemetry: OpenTelemetry,
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule(),
) : LlmClient {

    override fun complete(
        question: String,
        chunks: List<String>,
    ): LlmCompletion {
        val tracer = openTelemetry.getTracer("ai-temporal-worker")
        val span =
            tracer
                .spanBuilder("llm.complete")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("llm.provider", "anthropic")
                .startSpan()

        val startNanos = System.nanoTime()
        var status = "ok"
        return span.makeCurrent().use {
            try {
                val systemText = chunks.joinToString("\n---\n")
                val payload =
                    mapOf(
                        "model" to MODEL,
                        "max_tokens" to 1024,
                        "system" to systemText,
                        "messages" to listOf(mapOf("role" to "user", "content" to question)),
                    )
                val body = mapper.writeValueAsString(payload).toRequestBody(JSON)
                val request =
                    Request.Builder()
                        .url(ENDPOINT)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .post(body)
                        .build()

                callFactory.newCall(request).execute().use { response ->
                    span.setAttribute("http.status_code", response.code.toLong())
                    if (!response.isSuccessful) {
                        status = "error"
                        span.setStatus(StatusCode.ERROR)
                        error("Anthropic returned HTTP ${response.code}: ${response.body?.string()}")
                    }
                    val raw = response.body?.string() ?: error("Empty Anthropic response")
                    val parsed: AnthropicResponse = mapper.readValue(raw)
                    span.setAttribute("llm.model", parsed.model)
                    span.setAttribute("llm.input_tokens", parsed.usage?.inputTokens?.toLong() ?: 0L)
                    span.setAttribute("llm.output_tokens", parsed.usage?.outputTokens?.toLong() ?: 0L)
                    val text = parsed.content.firstOrNull { it.type == "text" }?.text ?: ""
                    LlmCompletion(text = text, citation = "")
                }
            } catch (e: Exception) {
                status = "error"
                span.setStatus(StatusCode.ERROR, e.message ?: "unknown")
                throw e
            } finally {
                val durationSeconds =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) / 1000.0
                Metrics.recordLlmDuration(status, durationSeconds)
                span.end()
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AnthropicResponse(
        val model: String = "",
        val content: List<ContentBlock> = emptyList(),
        val usage: Usage? = null,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ContentBlock(
            val type: String = "",
            val text: String = "",
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Usage(
            @JsonProperty("input_tokens") val inputTokens: Int = 0,
            @JsonProperty("output_tokens") val outputTokens: Int = 0,
        )
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"
        private const val ANTHROPIC_VERSION = "2023-06-01"

        fun create(openTelemetry: OpenTelemetry): AnthropicLlmClient {
            val apiKey = System.getenv("ANTHROPIC_API_KEY") ?: error("ANTHROPIC_API_KEY not set")
            val baseClient =
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            val callFactory = OkHttpTelemetry.create(openTelemetry).newCallFactory(baseClient)
            return AnthropicLlmClient(apiKey = apiKey, callFactory = callFactory, openTelemetry = openTelemetry)
        }
    }
}
