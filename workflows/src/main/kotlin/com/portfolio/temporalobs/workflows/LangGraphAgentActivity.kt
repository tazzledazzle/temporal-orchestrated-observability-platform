package com.portfolio.temporalobs.workflows

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
interface LangGraphAgentActivity {
    @ActivityMethod
    fun run(
        prompt: String,
        tools: List<String>,
    ): String
}
