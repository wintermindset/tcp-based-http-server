# TCP based HTTP server

A Java 21 HTTP server on raw TCP, with one virtual thread per connection and
pluggable handlers. Supports HTTP/1.0 and HTTP/1.1, keep-alive, sequential
pipelining and chunked request bodies. Runtime logging uses Log4j 2.

## Build and run

Requires JDK 21 or newer; CI checks JDK 21 and 25. The Maven wrapper downloads
Maven and dependencies on first use. Docker is optional.

```sh
./mvnw clean verify
java -jar target/tcp-based-http-server-1.0.0.jar
curl 'http://localhost:8080/calc?x=3&y=4&op=*'
```

The calculator returns `12.0`. It accepts GET and HEAD; use `op=%2B` for addition.

## Configuration

Pass a UTF-8 file as the only argument, or set `SERVER_CONFIG`. The argument takes
precedence; without either, the server uses [bundled settings](src/main/resources/server-config.yaml).

```sh
java -jar target/tcp-based-http-server-1.0.0.jar /path/to/server.yaml
```

The format is a flat `key: value` mapping, not full YAML. Blank lines and whole-line
`#` comments are supported; quoting, nested mappings and inline comments are not.
Unknown or duplicate keys, invalid values and startup failures cause a nonzero exit.

```yaml
port: 8080
handler: com.wintermindset.handler.CalcHandler
managementPort: 9090
```

External files use defaults for omitted values, except `handler` is required and
`managementPort` defaults to `-1` (disabled). Bundled configuration enables management
on `127.0.0.1:9090`; the application binds to `0.0.0.0:8080`.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `maxConnections` | 64 | Concurrent application connections; excess sockets are closed |
| `maxRequestsPerConnection` | 100 | Requests before closing a persistent connection |
| `idleTimeoutMillis` | 15000 | Wait for the next request |
| `readTimeoutMillis` | 5000 | Wait for more request bytes |
| `requestTimeoutMillis` | 30000 | Total request deadline, including handler and response write |
| `shutdownTimeoutMillis` | 5000 | Grace period before closing active sockets and interrupting handlers |
| `maxHeaders` / `maxHeaderBytes` | 100 / 32768 | Header count and bytes; trailers are bounded separately |
| `maxBodyBytes` | 1048576 | Buffered request body; configurable up to 10 MiB |

Request/header lines are capped at 8192 bytes before CRLF. Size violations return
413/431 where applicable; malformed input returns 400, read timeout 408. A total
deadline or idle timeout can close the socket without a response. Budget heap for
concurrent request and response bodies, logging and JVM overhead; connection count
alone does not bound memory allocated by custom handlers.

## Handlers and protocol limits

Implement `com.wintermindset.handler.Handler` with a public no-argument constructor
and put the class on the application classpath:

```java
public final class HelloHandler implements com.wintermindset.handler.Handler {
    public com.wintermindset.http.HttpResponse handle(com.wintermindset.http.HttpRequest request) {
        return com.wintermindset.http.HttpResponse.ok("Hello world");
    }
}
```

Configure its fully qualified name as `handler`. One instance is shared across
connections: implementations must be thread-safe, bound their allocations and honor
interruption. Java cannot forcibly stop a handler that ignores interruption.
Responses are buffered and framed by the server; HEAD omits the body. Handler
exceptions and invalid final responses become 500 without exposing exception text.

This is a limited HTTP implementation: no TLS, HTTP/2, HTTP/3, CONNECT tunnels,
protocol upgrades, streaming responses or response chunking. Only `chunked` request
transfer coding is supported; request trailers are validated and discarded.
`Expect` requests return 417. Header syntax is restricted to ASCII; ambiguous
framing and duplicate Host, Content-Length or Transfer-Encoding fields are rejected.

## Operations and Docker

Logs are JSON on stdout with request ID, method, status and duration for completed
requests. Responses include `X-Request-Id`; `logRequests: false` disables access logs.
The separate management listener serves GET/HEAD `/healthz`, `/readyz` and `/metrics`
(Prometheus requests, status classes, duration buckets, connections and timeouts).
During graceful shutdown readiness becomes 503 while in-flight work drains.

```sh
docker build -t tcp-based-http-server:1.0.0 .
docker run --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --cap-drop ALL --security-opt no-new-privileges \
  --memory 512m --pids-limit 256 --cpus 2 --stop-timeout 10 \
  -p 127.0.0.1:8080:8080 tcp-based-http-server:1.0.0
```

The image runs as UID/GID 10001, limits JVM heap relative to container memory and
checks readiness on loopback port 9090. Override the health-check port if you change
`managementPort`. Mount external configuration read-only and pass its container path
after the image name. Allow a stop grace period longer than `shutdownTimeoutMillis`.

Terminate TLS at a trusted reverse proxy and restrict access to the application
port. Keep management private; it has no authentication and is not published by the
command above. Check readiness, error/timeout metrics and heap under your own traffic
before increasing limits. Connection saturation closes new sockets rather than
returning an HTTP 503.

## Tests and CI

`./mvnw clean verify` runs JUnit tests and SpotBugs. Tests cover buffer boundaries,
malformed/framed HTTP, response semantics, configuration, handlers, real TCP
connections, connection limits, management endpoints and graceful shutdown.
They bind local ephemeral ports and require no external service. Failures produce a
nonzero exit; inspect the failing test and stack trace in `target/surefire-reports/`
or static-analysis findings in `target/spotbugsXml.xml`.

Stress tests are opt-in:

```sh
./mvnw -Dtest=ServerStressTest -Dstress=true -Dstress.seconds=30 \
  -Dstress.concurrency=32 -DargLine=-Xmx256m test
```

`target/stress-results.json` records requests/errors, throughput, p95/p99 latency,
heap, connections and file descriptors (`-1` if unavailable). Latency uses up to the
first 100,000 samples; heap includes uncollected garbage. The test requires zero
errors, successful recovery and zero remaining connections after shutdown. A short
CI run is not a long-duration leak test or a production capacity guarantee.

[CI](.github/workflows/tests.yml) also smoke-tests the packaged JAR and restricted
container, validates release metadata and scans runtime dependencies for high/critical
vulnerabilities. Reports and stress measurements are attached to the workflow run.

## Releases and updates

See [CHANGELOG.md](CHANGELOG.md). Tag the tested `main` commit with the POM version.
The Release workflow repeats CI and prepares a draft containing the versioned JAR,
runtime CycloneDX SBOM, `build-info.json`, license and `SHA256SUMS`. Review it before
publishing. Subsequent releases use new versions and tags.

Before deployment, run `sha256sum --check SHA256SUMS` in the downloaded bundle and
check the commit in `build-info.json`. Retain the previous verified JAR and its
configuration (or container image digest). To roll back, stop gracefully, restore
them, restart, and verify `/readyz` and an application request.

Dependabot opens weekly Maven, Docker and GitHub Actions updates against `dev`.
Review dependency changes and scan results, merge after CI, then release a new version.

## License

[MIT](LICENSE). Dependency notices are retained in the packaged JAR.
