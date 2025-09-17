package com.afristays.apigateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private ReactiveRedisTemplate<String, String> redisTemplate;

    // Rate limiting metrics
    private final AtomicLong rateLimitViolations = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    private final AtomicLong ddosDetections = new AtomicLong(0);
    private final AtomicLong whitelistedRequests = new AtomicLong(0);

    @Bean
    public Counter rateLimitViolationCounter() {
        return Counter.builder("afristays_rate_limit_violations_total")
                .description("Total number of rate limit violations")
                .register(meterRegistry);
    }

    @Bean
    public Counter blockedRequestsCounter() {
        return Counter.builder("afristays_blocked_requests_total")
                .description("Total number of blocked requests")
                .register(meterRegistry);
    }

    @Bean
    public Counter ddosDetectionCounter() {
        return Counter.builder("afristays_ddos_detections_total")
                .description("Total number of DDoS attack detections")
                .register(meterRegistry);
    }

    @Bean
    public Counter whitelistedRequestsCounter() {
        return Counter.builder("afristays_whitelisted_requests_total")
                .description("Total number of whitelisted requests")
                .register(meterRegistry);
    }

    @Bean
    public Timer requestProcessingTimer() {
        return Timer.builder("afristays_request_processing_duration")
                .description("Request processing duration")
                .register(meterRegistry);
    }

    @Bean
    public Gauge activeConnectionsGauge() {
        return Gauge.builder("afristays_redis_connections_active", this, MetricsConfig::getActiveRedisConnections)
                .description("Number of active Redis connections")
                .register(meterRegistry);
    }

    @Bean
    public Gauge rateLimitViolationsGauge() {
        return Gauge.builder("afristays_rate_limit_violations_current", rateLimitViolations, AtomicLong::doubleValue)
                .description("Current rate limit violations")
                .register(meterRegistry);
    }

    @Bean
    public Gauge blockedRequestsGauge() {
        return Gauge.builder("afristays_blocked_requests_current", blockedRequests, AtomicLong::doubleValue)
                .description("Current blocked requests")
                .register(meterRegistry);
    }

    // Method to get active Redis connections
    private double getActiveRedisConnections() {
        if (redisTemplate != null) {
            // This is a simplified approach - in production you might want to
            // implement more sophisticated Redis connection monitoring
            return 1.0; // Placeholder - implement actual connection count logic
        }
        return 0.0;
    }

    // Methods to increment counters (to be called from services)
    public void incrementRateLimitViolations() {
        rateLimitViolations.incrementAndGet();
        rateLimitViolationCounter().increment();
    }

    public void incrementBlockedRequests() {
        blockedRequests.incrementAndGet();
        blockedRequestsCounter().increment();
    }

    public void incrementDdosDetections() {
        ddosDetections.incrementAndGet();
        ddosDetectionCounter().increment();
    }

    public void incrementWhitelistedRequests() {
        whitelistedRequests.incrementAndGet();
        whitelistedRequestsCounter().increment();
    }

    // Reset methods for periodic cleanup
    public void resetCounters() {
        rateLimitViolations.set(0);
        blockedRequests.set(0);
    }
}
