package com.portfolio.temporalobs.workflows

import io.temporal.client.WorkflowOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LangGraphWorkflowTest {
    private lateinit var testEnv: TestWorkflowEnvironment

    @BeforeEach
    fun setUp() {
        testEnv = TestEnvironmentFactory.newEnvironment()
        val worker = testEnv.newWorker(TaskQueues.AI_WORKFLOWS)
        worker.registerWorkflowImplementationTypes(LangGraphWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(StubLangGraphAgentActivity())
        testEnv.start()
    }

    @AfterEach
    fun tearDown() {
        testEnv.close()
    }

    @Test
    fun langGraphWorkflowReturnsAnswerWithCorrectIterationCount() {
        val workflow =
            testEnv.workflowClient.newWorkflowStub(
                LangGraphWorkflow::class.java,
                WorkflowOptions.newBuilder().setTaskQueue(TaskQueues.AI_WORKFLOWS).build(),
            )

        val result = workflow.run("Explain distributed tracing")
        assertTrue(result.answer.isNotBlank(), "answer should not be blank")
        assertEquals(LangGraphWorkflowImpl.ITERATIONS, result.iterations)
    }

    private class StubLangGraphAgentActivity : LangGraphAgentActivity {
        override fun run(
            prompt: String,
            tools: List<String>,
        ): String = "langgraph-answer:$prompt"
    }
}
