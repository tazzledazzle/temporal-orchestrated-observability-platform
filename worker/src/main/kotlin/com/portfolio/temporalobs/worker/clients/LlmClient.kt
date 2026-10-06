package com.portfolio.temporalobs.worker.clients

import com.portfolio.temporalobs.workflows.model.LlmCompletion

interface LlmClient {
    fun complete(question: String, chunks: List<String>): LlmCompletion
}
