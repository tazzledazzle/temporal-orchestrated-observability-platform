package com.portfolio.temporalobs.worker

import com.portfolio.temporalobs.worker.clients.LlmClient
import com.portfolio.temporalobs.workflows.LangGraphAgentActivity
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory

class LangGraphAgentActivityImpl(
    private val llmClient: LlmClient,
    private val openTelemetry: OpenTelemetry,
) : LangGraphAgentActivity {

    private val tracer = openTelemetry.getTracer("ai-temporal-worker")

    override fun run(
        prompt: String,
        tools: List<String>,
    ): String {
        val observations = mutableListOf<String>()

        repeat(ITERATIONS) { i ->
            val iteration = i + 1
            val span =
                tracer
                    .spanBuilder("langgraph.iteration")
                    .setAttribute(ATTR_ITERATION, iteration.toLong())
                    .startSpan()
            val scope = span.makeCurrent()
            try {
                val context =
                    if (observations.isEmpty()) {
                        prompt
                    } else {
                        "$prompt\nObservations so far: ${observations.joinToString("; ")}"
                    }

                val planResult = llmClient.complete(context, emptyList())
                val toolName = tools.getOrNull(i % tools.size) ?: "search_docs"
                val observation = "$toolName:${planResult.text.take(80)}"
                observations.add(observation)

                span.setAttribute(ATTR_TOOL_CALLED, toolName)
                span.setAttribute(ATTR_OBSERVATION_LENGTH, observation.length.toLong())
                logger.info("langgraph.iteration iteration={} tool={}", iteration, toolName)
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR, e.message ?: "")
                throw e
            } finally {
                scope.close()
                span.end()
            }
        }

        val synthesis = llmClient.complete(prompt, observations)
        logger.info("langgraph.run complete observations={}", observations.size)
        return synthesis.text
    }

    companion object {
        const val ITERATIONS = 2

        private val ATTR_ITERATION = AttributeKey.longKey("iteration")
        private val ATTR_TOOL_CALLED = AttributeKey.stringKey("tool_called")
        private val ATTR_OBSERVATION_LENGTH = AttributeKey.longKey("observation_length")

        private val logger = LoggerFactory.getLogger(LangGraphAgentActivityImpl::class.java)
    }
}
