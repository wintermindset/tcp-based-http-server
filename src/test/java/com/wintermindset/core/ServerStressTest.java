package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.handler.CalcHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "stress", matches = "true")
class ServerStressTest {
    @Test
    void sustainedLoadAndRecovery() throws Exception {
        int concurrency = Integer.getInteger("stress.concurrency", 32);
        int seconds = Integer.getInteger("stress.seconds", 15);
        assertTrue(concurrency > 0 && concurrency <= 256);
        assertTrue(seconds > 0 && seconds <= 3600);
        ServerConfig config = new ServerConfig();
        config.port = 0;
        config.logRequests = false;
        config.maxConnections = Math.max(64, concurrency * 2);
        config.shutdownTimeoutMillis = 500;
        config.handler = new CalcHandler();
        var latencies = Collections.synchronizedList(new ArrayList<Long>());
        LongAdder requests = new LongAdder();
        LongAdder errors = new LongAdder();
        AtomicLong peakHeap = new AtomicLong();
        AtomicLong peakConnections = new AtomicLong();
        AtomicLong peakDescriptors = new AtomicLong();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
             Server server = new Server(config);
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                     .connectTimeout(Duration.ofSeconds(3)).build();
             var sampler = Executors.newSingleThreadScheduledExecutor()) {
            Future<?> serving = workers.submit(() -> { server.start(); return null; });
            assertTrue(server.awaitStarted(Duration.ofSeconds(5)));
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port()
                    + "/calc?x=3&y=4&op=*")).timeout(Duration.ofSeconds(5)).build();
            for (int i = 0; i < 20; i++) {
                assertEquals("12.0", client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            }
            long initialHeap = heap();
            sampler.scheduleAtFixedRate(() -> {
                peakHeap.accumulateAndGet(heap(), Math::max);
                peakConnections.accumulateAndGet(server.activeConnections(), Math::max);
                peakDescriptors.accumulateAndGet(descriptors(), Math::max);
            }, 0, 100, TimeUnit.MILLISECONDS);
            long started = System.nanoTime();
            long deadline = started + TimeUnit.SECONDS.toNanos(seconds);
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(workers.submit(() -> {
                    while (System.nanoTime() < deadline) {
                        long before = System.nanoTime();
                        try {
                            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() != 200 || !response.body().equals("12.0")) errors.increment();
                        } catch (Exception e) {
                            errors.increment();
                            if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); break; }
                        }
                        requests.increment();
                        synchronized (latencies) {
                            if (latencies.size() < 100000) latencies.add(System.nanoTime() - before);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) future.get(seconds + 10L, TimeUnit.SECONDS);
            double elapsed = (System.nanoTime() - started) / 1e9;
            sampler.shutdownNow();
            Collections.sort(latencies);
            var recovery = client.send(request, HttpResponse.BodyHandlers.ofString());
            long finalHeap = heap();
            client.close();
            server.close();
            serving.get(5, TimeUnit.SECONDS);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (server.activeConnections() != 0) Thread.sleep(10);
            });
            String report = String.format(Locale.ROOT,
                    "{\"concurrency\":%d,\"seconds\":%.3f,\"requests\":%d,\"errors\":%d,"
                    + "\"requests_per_second\":%.2f,\"latency_samples\":%d,\"p95_ms\":%.3f,\"p99_ms\":%.3f,"
                    + "\"initial_heap_bytes\":%d,\"peak_heap_bytes\":%d,\"final_heap_bytes\":%d,"
                    + "\"peak_connections\":%d,\"peak_file_descriptors\":%d,\"remaining_connections\":%d}%n",
                    concurrency, elapsed, requests.sum(), errors.sum(), requests.sum() / elapsed,
                    latencies.size(), percentile(latencies, .95), percentile(latencies, .99),
                    initialHeap, peakHeap.get(), finalHeap, peakConnections.get(), peakDescriptors.get(), server.activeConnections());
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/stress-results.json"), report);
            System.out.print(report);
            assertTrue(requests.sum() > 0);
            assertEquals(0, errors.sum());
            assertEquals(200, recovery.statusCode());
            assertEquals("12.0", recovery.body());
            assertTrue(peakConnections.get() <= config.maxConnections);
        }
    }

    private static double percentile(java.util.List<Long> values, double quantile) {
        return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * quantile) - 1)) / 1e6;
    }

    private static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long descriptors() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            return unix.getOpenFileDescriptorCount();
        }
        return -1;
    }
}
