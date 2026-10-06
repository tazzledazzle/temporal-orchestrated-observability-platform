import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const lokiPushFailures = new Rate('loki_push_failures');
const otelPushFailures = new Rate('otel_push_failures');

const LOKI_URL        = __ENV.LOKI_PUSH_BASE_URL  || 'http://localhost:3100';
const PROM_URL        = __ENV.PROMETHEUS_BASE_URL  || 'http://localhost:9090';
const OTEL_URL        = __ENV.OTEL_HTTP_BASE_URL   || 'http://localhost:4318';
const OTEL_HEALTH     = __ENV.OTEL_HEALTH_URL      || 'http://localhost:13133';
const CARDINALITY_MAX = parseInt(__ENV.METRIC_CARDINALITY_MAX || '500', 10);

export const options = {
  scenarios: {
    // Synthetic OTLP span posting — ramps to 50 req/s
    otel_spans: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '60s', target: 50 },
        { duration: '30s', target: 0  },
      ],
      exec: 'postOtelSpans',
    },
    // Synthetic log batch posting — ramps to 30 req/s
    loki_logs: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5  },
        { duration: '60s', target: 30 },
        { duration: '30s', target: 0  },
      ],
      exec: 'postLokiLogs',
    },
    // Prometheus metrics scrape simulation — constant 2 VUs for 120 s
    metrics_scrape: {
      executor: 'constant-vus',
      vus: 2,
      duration: '120s',
      exec: 'scrapeWorkerMetrics',
    },
  },
  thresholds: {
    'loki_push_failures': ['rate<0.01'],
    'otel_push_failures':  ['rate<0.01'],
    'http_req_failed':     ['rate<0.05'],
  },
};

// Minimal valid OTLP JSON span payload
function otlpPayload() {
  const rand = () => Math.floor(Math.random() * 16).toString(16);
  const traceId = Array.from({ length: 32 }, rand).join('');
  const spanId  = Array.from({ length: 16 }, rand).join('');
  return JSON.stringify({
    resourceSpans: [{
      resource: {
        attributes: [{ key: 'service.name', value: { stringValue: 'k6-load-test' } }],
      },
      scopeSpans: [{
        scope: { name: 'k6' },
        spans: [{
          traceId,
          spanId,
          name: 'k6.synthetic.span',
          kind: 1,
          startTimeUnixNano: String(Date.now() * 1_000_000),
          endTimeUnixNano:   String((Date.now() + 100) * 1_000_000),
          status: { code: 1 },
          attributes: [
            { key: 'k6.scenario', value: { stringValue: 'otel_spans' } },
          ],
        }],
      }],
    }],
  });
}

export function postOtelSpans() {
  const res = http.post(`${OTEL_URL}/v1/traces`, otlpPayload(), {
    headers: { 'Content-Type': 'application/json' },
  });
  otelPushFailures.add(res.status !== 200);

  // OTel Collector health must stay up throughout the run
  const health = http.get(OTEL_HEALTH);
  check(health, { 'collector healthy': (r) => r.status === 200 });

  sleep(0.1);
}

export function postLokiLogs() {
  const payload = JSON.stringify({
    streams: [{
      stream: {
        service_name: 'k6-load-test',
        workflow_id: `k6-${Date.now()}`,
      },
      values: [[
        String(Date.now() * 1_000_000),
        'k6 synthetic log line',
      ]],
    }],
  });

  const res = http.post(`${LOKI_URL}/loki/api/v1/push`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  lokiPushFailures.add(res.status !== 204);

  sleep(0.1);
}

export function scrapeWorkerMetrics() {
  // Simulate Prometheus scraping the worker's metrics endpoint at high frequency
  const res = http.get('http://localhost:9464/metrics');
  check(res, {
    'worker metrics endpoint up': (r) => r.status === 200,
    'metrics body non-empty':     (r) => r.body.length > 0,
  });
  sleep(1);
}

// handleSummary runs after all scenarios complete (requires k6 >= 0.43.0)
export function handleSummary(data) {
  // Cardinality guard: count distinct metric label combinations in Prometheus
  const cardRes = http.get(
    `${PROM_URL}/api/v1/query?query=count(count by (__name__, job) ({__name__=~".+"}))`
  );
  if (cardRes.status === 200) {
    var parsed;
    try { parsed = JSON.parse(cardRes.body); } catch (_) { parsed = null; }
    const count = parseInt(parsed && parsed.data && parsed.data.result && parsed.data.result[0] && parsed.data.result[0].value && parsed.data.result[0].value[1] || '0', 10);
    if (count > CARDINALITY_MAX) {
      console.error(
        `CARDINALITY BREACH: ${count} label combinations exceeds max ${CARDINALITY_MAX}`
      );
    } else {
      console.log(`Cardinality OK: ${count} / ${CARDINALITY_MAX}`);
    }
  }
  return { stdout: JSON.stringify(data, null, 2) };
}
