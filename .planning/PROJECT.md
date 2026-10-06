# Temporal-Orchestrated Observability Platform

## What This Is

A **reference platform** that runs sample **AI workflows** on **Temporal**, instruments execution with **Kotlin + OpenTelemetry**, and surfaces **metrics, traces, and logs** in **Grafana (LGTM)** so operators get **end-to-end visibility** from workflow start through LLM/retrieval activities to on-call response.

## Core Value

**An operator can start an AI workflow, watch it succeed or fail in Temporal, and within Grafana trace the same run across metrics, traces, and logs using shared workflow identifiers.**

## Current State (v1.0 — shipped 2026-05-20)

- **Worker:** Kotlin/JVM 21 Gradle monorepo (~1,450 LOC) — `:workflows`, `:worker`, `:starter`
- **Workflows:** PingWorkflow, RagQa, AgentTools, BatchEval with WireMock LLM stub
- **Stack:** Docker Compose — Temporal, Grafana, Loki, Tempo, Prometheus, Jaeger, OTel Collector, Promtail
- **Telemetry:** OTel traces/metrics/logs correlated by `workflow_id`; Prometheus `:9464/metrics`
- **Operations:** `docs/OPERATIONS.md` runbooks, `deploy/prometheus/alerts.yml`, smoke scripts
- **Evidence:** `.planning/phases/*/VERIFICATION.md` · archived requirements in `.planning/milestones/v1.0-REQUIREMENTS.md`

## Requirements

### Validated (v1.0)

- ✓ **Foundation (Phase 1)** — Gradle modules, Compose LGTM+Temporal stack, smoke script, CI
- ✓ **Temporal (Phase 2)** — PingWorkflow, worker, starter CLI
- ✓ **AI workflows (Phase 3)** — RagQa, AgentTools, BatchEval + LLM stub
- ✓ **Instrumentation (Phase 4)** — OTel traces, metrics, JSON logs, OkHttp propagation
- ✓ **Backends (Phase 5)** — Jaeger + Prometheus scrape, collector validate
- ✓ **LGTM (Phase 6)** — Loki, Tempo, Grafana dashboards, log↔trace correlation
- ✓ **Operations (Phase 7)** — Runbooks, Prometheus alerts, manual validation

### Active (v1.1 — Real LLM Integration)

**Requirements:** [.planning/milestones/v1.1-REQUIREMENTS.md](milestones/v1.1-REQUIREMENTS.md)  
**Roadmap:** [.planning/milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

| ID | Requirement | Phase | Status |
|----|-------------|-------|--------|
| LLM-01 | `LlmClient` interface; stub/openai/anthropic impls; `LLM_PROVIDER` env flag | 8 | Planned |
| LLM-02 | OpenAI provider: `api.openai.com/v1/chat/completions` via existing OkHttp | 8 | Planned |
| LLM-03 | Anthropic provider: `api.anthropic.com/v1/messages` via existing OkHttp | 8 | Planned |
| LLM-04 | OTel span attrs on real calls: `llm.provider`, `llm.model`, `llm.input_tokens`, `llm.output_tokens` | 8 | Planned |
| LG-01 | `LangGraphAgentActivity` runs plan→tool→observe loop (≥2 iterations) as Temporal activity | 9 | Planned |
| LG-02 | Each loop iteration emits child span `langgraph.iteration` with `iteration`, `tool_called`, `observation_length` | 9 | Planned |
| LG-03 | Starter CLI `--workflow langgraph` triggers the new workflow | 9 | Planned |
| LLM-05 | `./scripts/llm-smoke.sh` verifies real-provider round-trip; `smoke.sh` still exits 0 stub-only | 10 | Planned |

**Deferred to v1.2 (Production Deployment):** K8S-01 (Helm/Kustomize), K8S-02 (Temporal mTLS)

### Out of Scope

| Feature | Reason |
|---------|--------|
| Multi-region Temporal | Portfolio scope |
| Custom embedding training | Not observability focus |
| SaaS auth/billing | Demo platform |
| PagerDuty integration | Example alerts only (v1) |
| Production Temporal Cloud | Self-hosted Compose for v1 |

## Next Milestone Goals (v1.1)

1. **Phase 8** — Wire `LlmClient` interface; add OpenAI + Anthropic providers behind `LLM_PROVIDER` flag; enrich OTel span attributes
2. **Phase 9** — Implement `LangGraphAgentActivity`; update or add workflow; verify child spans in Tempo
3. **Phase 10** — `llm-smoke.sh`, `v1.1-VERIFICATION.md`, dashboard evidence, operations runbook update

## Context

Greenfield project from architecture diagram **"5. Temporal-Orchestrated Observability Platform"** (2026-05-19).  
Design: `docs/ARCHITECTURE.md` · ADRs: `docs/adr/`

## Constraints

- **Kotlin/JVM 21** workers with Gradle
- **Temporal** 1.25+ SDK
- **OpenTelemetry** Java SDK; OTLP + Prometheus exporters
- **Docker Compose** for all infrastructure in v1
- **Portfolio credibility** — reproducible smoke, honest docs

## Key Decisions

| Decision | Rationale | ADR | Outcome |
|----------|-----------|-----|---------|
| Temporal for orchestration | Durable AI steps, UI, retries | ADR-001 | ✓ Good — all workflows durable with visible history |
| Kotlin + OTel workers | Matches diagram; LGTM-native telemetry | ADR-002 | ✓ Good — full trace/metric/log correlation |
| Jaeger dev → Tempo LGTM | Fast debug + Grafana correlation | ADR-003 | ✓ Good — dual-export supports both paths |
| workflow_id on traces/logs, not metric labels | Avoid cardinality explosion | ADR-004 | ✓ Good — bounded Prometheus labels |
| Compose-first platform | Reproducible local demo | ADR-005 | ✓ Good — single-command stack bring-up |

---
*Last updated: 2026-10-05 — v1.1 milestone scope defined*
