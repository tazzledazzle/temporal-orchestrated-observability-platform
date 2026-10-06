package com.portfolio.temporalobs.loadtest

import com.portfolio.temporalobs.workflows._
import io.gatling.core.Predef._
import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.{WorkflowServiceStubs, WorkflowServiceStubsOptions}

import java.time.Duration
import java.util.UUID
import scala.concurrent.duration._

class WorkflowThroughputSimulation extends Simulation {

  private val tier          = System.getProperty("tier", "baseline")
  private val temporalHost  = sys.env.getOrElse("TEMPORAL_HOST", "localhost:7233")
  private val namespace     = sys.env.getOrElse("TEMPORAL_NAMESPACE", "default")

  private val (concurrentUsers, testDuration) = tier match {
    case "target" => (10, 5.minutes)
    case "stress" => (25, 5.minutes)
    case _        => (5,  2.minutes)   // baseline
  }

  // error threshold: 1 % for baseline, 2 % for target/stress
  private val errorThresholdPct: Double = if (tier == "target") 2.0 else 1.0

  private val serviceStubs = WorkflowServiceStubs.newConnectedServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
      .setTarget(temporalHost)
      .build(),
    Duration.ofSeconds(30)
  )
  private val client = WorkflowClient.newInstance(serviceStubs)

  // Circular feeder: ping 40 %, rag 30 %, agent 20 %, batch 10 %
  private val feedData: Array[Map[String, String]] =
    (List.fill(4)(Map("wfType" -> "ping"))  ++
    List.fill(3)(Map("wfType" -> "rag"))   ++
    List.fill(2)(Map("wfType" -> "agent")) ++
    List(       Map("wfType" -> "batch"))).toArray

  private def makeOpts(wfId: String): WorkflowOptions =
    WorkflowOptions.newBuilder()
      .setWorkflowId(wfId)
      .setTaskQueue(TaskQueues.AI_WORKFLOWS)
      .build()

  private val scn = scenario("WorkflowThroughput")
    .feed(feedData.circular)
    .exec { session =>
      val wfType = session("wfType").as[String]
      val wfId   = s"load-$wfType-${UUID.randomUUID()}"
      try {
        wfType match {
          case "ping" =>
            client.newWorkflowStub(classOf[PingWorkflow], makeOpts(wfId)).ping()
          case "rag" =>
            client.newWorkflowStub(classOf[RagQaWorkflow], makeOpts(wfId))
              .ask("What is observability?")
          case "agent" =>
            client.newWorkflowStub(classOf[AgentToolsWorkflow], makeOpts(wfId))
              .run("Summarize observability architecture")
          case "batch" =>
            client.newWorkflowStub(classOf[BatchEvalWorkflow], makeOpts(wfId))
              .eval("load-eval", 3)
        }
        session.set("workflowId", wfId)
      } catch {
        case ex: Exception =>
          System.err.println(s"[WorkflowThroughput] $wfId failed: ${ex.getMessage}")
          session.markAsFailed
      }
    }

  setUp(
    scn.inject(atOnceUsers(concurrentUsers))
  ).maxDuration(testDuration)
    .assertions(
      global.responseTime.percentile(95).lt(30000),
      global.failedRequests.percent.lt(errorThresholdPct)
    )

  after {
    serviceStubs.shutdownNow()
  }
}
