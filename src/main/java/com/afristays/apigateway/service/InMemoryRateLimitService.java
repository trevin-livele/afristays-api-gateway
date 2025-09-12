package com.afristays.apigateway.service;

import com.afristays.apigateway.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fallback rate limiting service for development when Redis is not available
 */
@Service
@ConditionalOnMissingBean(ReactiveRedisTemplate.class)
public class InMemoryRateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryRateLimitService.class);
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    @Autowired
    private IpBlacklistService ipBlacklistService;
    
    // In-memory storage for rate limiting counters
    private final ConcurrentHashMap<String, AtomicInteger> minuteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hourCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> dayCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> secondCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> burstCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> endpointCounters = new ConcurrentHashMap<>();
    
    // Cleanup thread to remove old counters
    private volatile boolean cleanupRunning = true;
    
    public InMemoryRateLimitService() {
        logger.warn("Using in-memory rate limiting service. This is not recommended for production!");
        startCleanupThread();
    }
    
    /**
     * Check if request should be rate limited
     */
    public Mono<RateLimitService.RateLimitResult> checkRateLimit(String ip, String endpoint) {
        // Skip rate limiting for whitelisted IPs
        if (ipBlacklistService.isWhitelisted(ip)) {
            return Mono.just(new RateLimitService.RateLimitResult(false, "Whitelisted IP"));
        }
        
        String currentMinute = getCurrentMinute();
        String currentHour = getCurrentHour();
        String currentDay = getCurrentDay();
        String currentSecond = getCurrentSecond();
        
        // Check limits
        int minuteCount = getAndIncrement(minuteCounters, ip + ":" + currentMinute);
        int hourCount = getAndIncrement(hourCounters, ip + ":" + currentHour);
        int dayCount = getAndIncrement(dayCounters, ip + ":" + currentDay);
        int secondCount = getAndIncrement(secondCounters, ip + ":" + currentSecond);
        int burstCount = getAndIncrement(burstCounters, ip);
        
        // Track endpoint usage
        int endpointCount = getAndIncrement(endpointCounters, ip + ":" + endpoint + ":" + currentMinute);
        
        if (secondCount > rateLimitConfig.getDdosThresholdPerSecond()) {
            handleDDoSDetection(ip, "Too many requests per second");
            return Mono.just(new RateLimitService.RateLimitResult(true, "DDoS protection triggered"));
        }
        
        if (minuteCount > rateLimitConfig.getRequestsPerMinute()) {
            handleDDoSDetection(ip, "Too many requests per minute");
            return Mono.just(new RateLimitService.RateLimitResult(true, "Rate limit exceeded (per minute)"));
        }
        
        if (hourCount > rateLimitConfig.getRequestsPerHour()) {
            return Mono.just(new RateLimitService.RateLimitResult(true, "Rate limit exceeded (per hour)"));
        }
        
        if (dayCount > rateLimitConfig.getRequestsPerDay()) {
            return Mono.just(new RateLimitService.RateLimitResult(true, "Rate limit exceeded (per day)"));
        }
        
        if (burstCount > rateLimitConfig.getBurstCapacity()) {
            return Mono.just(new RateLimitService.RateLimitResult(true, "Burst limit exceeded"));
        }
        
        if (endpointCount > rateLimitConfig.getMaxSameEndpointRequests()) {
            ipBlacklistService.markSuspicious(ip, "Excessive same endpoint requests: " + endpoint);
        }
        
        return Mono.just(new RateLimitService.RateLimitResult(false, "Request allowed"));
    }
    
    /**
     * Track error responses for suspicious pattern detection
     */
    public Mono<Void> trackErrorResponse(String ip, int statusCode) {
        if (statusCode >= 400) {
            String key = ip + ":" + getCurrentMinute();
            int errorCount = getAndIncrement(errorCounters, key);
            
            if (errorCount >= rateLimitConfig.getMaxConsecutiveErrors()) {
                ipBlacklistService.blockTemporarily(ip, 
                        rateLimitConfig.getSuspiciousPatternBlockMinutes(),
                        "Too many error responses");
            }
        }
        return Mono.empty();
    }
    
    private int getAndIncrement(ConcurrentHashMap<String, AtomicInteger> map, String key) {
        return map.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    private void handleDDoSDetection(String ip, String reason) {
        logger.warn("DDoS attack detected from IP: {} - {}", ip, reason);
        ipBlacklistService.blockTemporarily(ip, rateLimitConfig.getDdosBlockDurationMinutes(), reason);
    }
    
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (cleanupRunning) {
                try {
                    Thread.sleep(60000); // Clean up every minute
                    cleanupOldCounters();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("RateLimit-Cleanup");
        cleanupThread.start();
    }
    
    private void cleanupOldCounters() {
        String currentMinute = getCurrentMinute();
        String currentHour = getCurrentHour();
        String currentDay = getCurrentDay();
        String currentSecond = getCurrentSecond();
        
        // Clean up old counters (keep only current time windows)
        minuteCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentMinute));
        hourCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentHour));
        dayCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentDay));
        secondCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentSecond));
        errorCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentMinute));
        endpointCounters.entrySet().removeIf(entry -> !entry.getKey().contains(currentMinute));
        
        // Clean up burst counters older than 10 seconds
        burstCounters.clear(); // Simple approach - clear all burst counters every minute
    }
    
    private String getCurrentMinute() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
    }
    
    private String getCurrentHour() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
    }
    
    private String getCurrentDay() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    private String getCurrentSecond() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }
    
    public void shutdown() {
        cleanupRunning = false;
    }
}
