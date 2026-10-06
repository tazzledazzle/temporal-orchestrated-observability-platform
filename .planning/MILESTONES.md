# Milestones

## v1.0 Reference Platform (Shipped: 2026-05-20)

**Phases completed:** 7 phases, 8 plans  
**Requirements:** 22/22 v1 requirements validated  
**Archived:** [v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) · [v1.0-REQUIREMENTS.md](milestones/v1.0-REQUIREMENTS.md)

**Delivered:** A reproducible local reference platform — Temporal AI workflows, Kotlin + OpenTelemetry instrumentation, and unified LGTM/Grafana observability with operator runbooks.

**Key accomplishments:**

- Compose + smoke foundation: Temporal, Grafana, Prometheus, Loki, Tempo healthy on clone (`FOUND-01`–`FOUND-03`)
- Kotlin worker with PingWorkflow and three AI workflows (RAG, agent tools, batch eval) via starter CLI
- Full OTel instrumentation: Temporal interceptors, Prometheus metrics, JSON logs with trace correlation
- Jaeger + Prometheus backends wired; OTel Collector validated in CI
- LGTM stack: Loki log ingest, Tempo traces, Grafana dashboards with log↔trace derived fields
- Operations: four runbooks in `docs/OPERATIONS.md`, Prometheus alert rules, manual validation recorded

**Stats:**

- Kotlin: ~1,450 LOC
- Timeline: 2026-05-19 → 2026-05-20 (requirements → verification)

**Known gaps:**

- No `*-SUMMARY.md` execution artifacts (completion tracked via `*-VERIFICATION.md` instead)

---

## v1.1 Real LLM Integration (Planned: 2026-Q4)

**Status:** Planning  
**Requirements:** [v1.1-REQUIREMENTS.md](milestones/v1.1-REQUIREMENTS.md) · [v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

**Goal:** Replace the WireMock LLM stub with a real OpenAI/Anthropic provider behind a feature flag, and embed a LangGraph agent loop as a Temporal activity — no infra changes, purely software additions on the existing Kotlin/JVM 21 + Temporal + OTel stack.

**Phases:**

1. Real LLM wiring — `LlmClient` interface, OpenAI + Anthropic implementations, feature flag, OTel span attributes (AI-01)
2. LangGraph activity — `LangGraphAgentActivity` with plan→tool→observe loop and child spans (AI-02)
3. End-to-end verification — `llm-smoke.sh`, `v1.1-VERIFICATION.md`, dashboard evidence, operations runbook update

**Deferred to v1.2:** K8S-01 (Helm/Kustomize), K8S-02 (Temporal mTLS)

---
