package com.portfolio.temporalobs.workflows

import com.portfolio.temporalobs.workflows.model.LangGraphResult
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import java.time.Duration

class LangGraphWorkflowImpl : LangGraphWorkflow {
    private val agentActivity: LangGraphAgentActivity =
        Workflow.newActivityStub(
            LangGraphAgentActivity::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(5))
                .build(),
        )

    override fun run(prompt: String): LangGraphResult {
        val tools = listOf("search_docs", "calculate", "lookup")
        val answer = agentActivity.run(prompt, tools)
        return LangGraphResult(answer = answer, iterations = ITERATIONS)
    }

    companion object {
        const val ITERATIONS = 2
    }
}
