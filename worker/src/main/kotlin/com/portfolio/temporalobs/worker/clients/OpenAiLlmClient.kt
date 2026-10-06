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

class OpenAiLlmClient(
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
                .setAttribute("llm.provider", "openai")
                .startSpan()

        val startNanos = System.nanoTime()
        var status = "ok"
        return span.makeCurrent().use {
            try {
                val payload =
                    mapOf(
                        "model" to MODEL,
                        "messages" to
                            listOf(
                                mapOf("role" to "system", "content" to chunks.joinToString("\n---\n")),
                                mapOf("role" to "user", "content" to question),
                            ),
                    )
                val body = mapper.writeValueAsString(payload).toRequestBody(JSON)
                val request =
                    Request.Builder()
                        .url(ENDPOINT)
                        .header("Authorization", "Bearer $apiKey")
                        .post(body)
                        .build()

                callFactory.newCall(request).execute().use { response ->
                    span.setAttribute("http.status_code", response.code.toLong())
                    if (!response.isSuccessful) {
                        status = "error"
                        span.setStatus(StatusCode.ERROR)
                        error("OpenAI returned HTTP ${response.code}: ${response.body?.string()}")
                    }
                    val raw = response.body?.string() ?: error("Empty OpenAI response")
                    val parsed: OpenAiResponse = mapper.readValue(raw)
                    span.setAttribute("llm.model", parsed.model)
                    span.setAttribute("llm.input_tokens", parsed.usage?.promptTokens?.toLong() ?: 0L)
                    span.setAttribute("llm.output_tokens", parsed.usage?.completionTokens?.toLong() ?: 0L)
                    LlmCompletion(
                        text = parsed.choices.firstOrNull()?.message?.content ?: "",
                        citation = "",
                    )
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
    private data class OpenAiResponse(
        val model: String = "",
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Choice(val message: Message = Message())

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Message(val content: String = "")

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Usage(
            @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
            @JsonProperty("completion_tokens") val completionTokens: Int = 0,
        )
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"

        fun create(openTelemetry: OpenTelemetry): OpenAiLlmClient {
            val apiKey = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY not set")
            val baseClient =
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            val callFactory = OkHttpTelemetry.create(openTelemetry).newCallFactory(baseClient)
            return OpenAiLlmClient(apiKey = apiKey, callFactory = callFactory, openTelemetry = openTelemetry)
        }
    }
}
