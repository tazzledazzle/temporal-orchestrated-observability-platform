# Temporal-Orchestrated Observability Platform

A portfolio-grade reference system that runs **AI workflows** on **Temporal**, instruments execution with **Kotlin + OpenTelemetry**, and delivers **end-to-end visibility** through the **LGTM stack** (Loki, Grafana, Tempo, Prometheus) for operators and on-call engineers.

<!-- Demo: add screenshot or GIF of Grafana Workflow Overview after ./scripts/lgtm-sample-load.sh -->

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design, data flows, and ADRs.

```
AI Workflows → Temporal → Kotlin OTel SDK → Collector → Jaeger/Tempo + Prometheus
                              ↓                              ↓
                         JSON logs → Loki ──────────────→ Grafana → On-call runbooks
```

## Quick start

Requires **JDK 21**, **Docker** (≥8 GB RAM), and **curl**.

```bash
docker compose -f deploy/docker-compose.yml up -d
./scripts/smoke.sh
./gradlew build test
```

**Sample observability data:**

```bash
./scripts/lgtm-sample-load.sh
```

Open http://localhost:3000 (Grafana) and http://localhost:8080 (Temporal UI).

## Documentation

| Doc | Purpose |
|-----|---------|
| [docs/LOCAL-DEV.md](docs/LOCAL-DEV.md) | URLs, worker/starter, workflows |
| [docs/GRAFANA.md](docs/GRAFANA.md) | Dashboards and Explore queries |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | On-call runbooks and alert rules |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design |

## Planning (GSD)

Implementation is tracked in [`.planning/`](.planning/) (requirements, roadmap, per-phase verification).

**Workflow starters:** `ping`, `rag`, `agent`, `batch` — see [docs/LOCAL-DEV.md](docs/LOCAL-DEV.md).

## Verification scripts

| Script | Checks |
|--------|--------|
| `./scripts/smoke.sh` | Compose stack health |
| `./scripts/otel-smoke.sh` | Worker Prometheus metrics |
| `./scripts/trace-smoke.sh` | Jaeger traces + scrape target |
| `./scripts/lgtm-sample-load.sh` | Logs, traces, dashboard sample load |

---

*v1 complete — 2026-05-20*
