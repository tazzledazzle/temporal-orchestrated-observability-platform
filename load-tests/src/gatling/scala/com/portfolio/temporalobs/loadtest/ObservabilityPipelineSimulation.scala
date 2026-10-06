package com.portfolio.temporalobs.loadtest

import com.portfolio.temporalobs.workflows._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.{WorkflowServiceStubs, WorkflowServiceStubsOptions}

import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration._

class ObservabilityPipelineSimulation extends Simulation {

  private val tempoBase = sys.env.getOrElse("TEMPO_BASE_URL",      "http://localhost:3200")
  private val lokiBase  = sys.env.getOrElse("LOKI_PUSH_BASE_URL",  "http://localhost:3100")
  private val promBase  = sys.env.getOrElse("PROMETHEUS_BASE_URL", "http://localhost:9090")

  private val serviceStubs = WorkflowServiceStubs.newConnectedServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
      .setTarget(sys.env.getOrElse("TEMPORAL_HOST", "localhost:7233"))
      .build(),
    Duration.ofSeconds(30)
  )
  private val client = WorkflowClient.newInstance(serviceStubs)

  // Populated by Phase 1; consumed by Phase 2 feeder
  private val startedIds = new ConcurrentLinkedQueue[String]()

  // ── Phase 1: start 5 ping workflows, collect IDs ──────────────────────────
  private val workflowPhase = scenario("StartWorkflows")
    .exec { session =>
      val wfId = s"obs-load-${UUID.randomUUID()}"
      val opts = WorkflowOptions.newBuilder()
        .setWorkflowId(wfId)
        .setTaskQueue(TaskQueues.AI_WORKFLOWS)
        .build()
      try {
        client.newWorkflowStub(classOf[PingWorkflow], opts).ping()
        startedIds.add(wfId)
        session.set("workflowId", wfId)
      } catch {
        case ex: Exception =>
          System.err.println(s"[ObsPipeline/Phase1] $wfId failed: ${ex.getMessage}")
          session.markAsFailed
      }
    }

  // ── Phase 2: for each started ID, verify telemetry in Tempo/Loki/Prometheus ─
  // Feeder drains the queue; falls back to a sentinel if queue is empty
  private val idFeeder = Iterator.continually {
    Map("verifyId" -> Option(startedIds.poll()).getOrElse("__missing__"))
  }

  private val verifyPhase = scenario("VerifyTelemetry")
    .feed(idFeeder)
    // Tempo TraceQL: find spans where span attribute workflow_id matches
    .exec(
      http("Tempo: root span exists for workflow_id")
        .get(s"$tempoBase/api/search")
        .queryParam("q", "{span.workflow_id=\"${verifyId}\"}")
        .queryParam("limit", "1")
        .check(status.is(200))
        .check(jsonPath("$.traces[0].traceID").exists)
    )
    // Loki: at least one structured log line with matching workflow_id
    .exec(
      http("Loki: log line with matching workflow_id")
        .get(s"$lokiBase/loki/api/v1/query")
        .queryParam("query", """{service_name="ai-temporal-worker"} | json | workflow_id=`${verifyId}`""")
        .check(status.is(200))
        .check(jsonPath("$.data.result[0]").exists)
    )
    // Prometheus: workflow_completed_total counter has at least one series
    .exec(
      http("Prometheus: workflow_completed_total metric exists")
        .get(s"$promBase/api/v1/query")
        .queryParam("query", "workflow_completed_total")
        .check(status.is(200))
        .check(jsonPath("$.data.result[0]").exists)
    )

  setUp(
    workflowPhase.inject(atOnceUsers(5)),
    // 45 s pause gives OTel Collector time to flush spans/logs to Tempo and Loki
    verifyPhase.inject(nothingFor(45.seconds), atOnceUsers(5))
  ).assertions(
    // Verification phase must have zero failures; Phase 1 failures are workflow errors
    forAll.failedRequests.percent.lt(5.0)
  )

  after {
    serviceStubs.shutdownNow()
  }
}
