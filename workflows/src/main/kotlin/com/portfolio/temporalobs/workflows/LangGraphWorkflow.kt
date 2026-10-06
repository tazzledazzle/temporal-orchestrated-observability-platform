package com.portfolio.temporalobs.workflows

import com.portfolio.temporalobs.workflows.model.LangGraphResult
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface LangGraphWorkflow {
    @WorkflowMethod
    fun run(prompt: String): LangGraphResult
}
