# Temporal-Orchestrated Observability Platform

## What This Is

A **reference platform** that runs sample **AI workflows** on **Temporal**, instruments execution with **Kotlin + OpenTelemetry**, and surfaces **metrics, traces, and logs** in **Grafana (LGTM)** so operators get **end-to-end visibility** from workflow start through LLM/retrieval activities to on-call response.

## Core Value

**An operator can start an AI workflow, watch it succeed or fail in Temporal, and within Grafana trace the same run across metrics, traces, and logs using shared workflow identifiers.**

## Requirements

### Validated (v1 complete — 2026-05-20)

- ✓ **Foundation (Phase 1)** — Gradle modules, Compose LGTM+Temporal stack, smoke script, CI
- ✓ **Temporal (Phase 2)** — PingWorkflow, worker, starter CLI
- ✓ **AI workflows (Phase 3)** — RagQa, AgentTools, BatchEval + LLM stub
- ✓ **Instrumentation (Phase 4)** — OTel traces, metrics, JSON logs, OkHttp propagation
- ✓ **Backends (Phase 5)** — Jaeger + Prometheus scrape, collector validate
- ✓ **LGTM (Phase 6)** — Loki, Tempo, Grafana dashboards, log↔trace correlation
- ✓ **Operations (Phase 7)** — Runbooks, Prometheus alerts, manual validation

See `.planning/phases/*/VERIFICATION.md` for evidence.

### Out of Scope (v2)

- **Kubernetes deployment** — Helm/Kustomize stretch
- **Production Temporal Cloud** — Self-hosted Compose for v1
- **PagerDuty integration** — Example alerts only
- **Custom ML training** — Stubs / external APIs

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

| Decision | Rationale | ADR |
|----------|-----------|-----|
| Temporal for orchestration | Durable AI steps, UI, retries | ADR-001 |
| Kotlin + OTel workers | Matches diagram; LGTM-native telemetry | ADR-002 |
| Jaeger dev → Tempo LGTM | Fast debug + Grafana correlation | ADR-003 |
| workflow_id on traces/logs, not metric labels | Avoid cardinality explosion | ADR-004 |
| Compose-first platform | Reproducible local demo | ADR-005 |

---
*v1 completed: 2026-05-20*
