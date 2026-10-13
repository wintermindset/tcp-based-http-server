package com.wintermindset.core;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

public final class ServerMetrics {
    private static final double[] BUCKETS = {.005, .01, .05, .1, .5, 1, 5, 30};
    public final LongAdder accepted = new LongAdder();
    public final LongAdder rejected = new LongAdder();
    public final LongAdder timeouts = new LongAdder();
    private final LongAdder requests = new LongAdder();
    private final LongAdder nanos = new LongAdder();
    private final LongAdder[] statuses = counters(6);
    private final LongAdder[] latency = counters(BUCKETS.length);

    private static LongAdder[] counters(int size) {
        LongAdder[] result = new LongAdder[size];
        for (int i = 0; i < size; i++) result[i] = new LongAdder();
        return result;
    }

    public void record(int status, long elapsedNanos) {
        requests.increment();
        nanos.add(elapsedNanos);
        statuses[Math.min(5, Math.max(0, status / 100))].increment();
        for (int i = 0; i < BUCKETS.length; i++) {
            if (elapsedNanos / 1e9 <= BUCKETS[i]) latency[i].increment();
        }
    }

    public String render(int active) {
        StringBuilder out = new StringBuilder();
        out.append("# TYPE http_connections_active gauge\nhttp_connections_active ").append(active).append('\n');
        out.append("# TYPE http_connections_accepted_total counter\nhttp_connections_accepted_total ").append(accepted.sum()).append('\n');
        out.append("# TYPE http_connections_rejected_total counter\nhttp_connections_rejected_total ").append(rejected.sum()).append('\n');
        out.append("# TYPE http_timeouts_total counter\nhttp_timeouts_total ").append(timeouts.sum()).append('\n');
        out.append("# TYPE http_requests_total counter\n");
        for (int i = 0; i < statuses.length; i++) {
            out.append("http_requests_total{status_class=\"").append(i == 0 ? "transport_error" : i + "xx")
                    .append("\"} ").append(statuses[i].sum()).append('\n');
        }
        out.append("# TYPE http_request_duration_seconds histogram\n");
        for (int i = 0; i < BUCKETS.length; i++) {
            out.append("http_request_duration_seconds_bucket{le=\"").append(BUCKETS[i]).append("\"} ")
                    .append(latency[i].sum()).append('\n');
        }
        out.append("http_request_duration_seconds_bucket{le=\"+Inf\"} ").append(requests.sum()).append('\n');
        out.append("http_request_duration_seconds_count ").append(requests.sum()).append('\n');
        out.append("http_request_duration_seconds_sum ").append(String.format(Locale.ROOT, "%.9f", nanos.sum() / 1e9)).append('\n');
        return out.toString();
    }
}
