package com.afristays.apigateway.service;

import com.afristays.apigateway.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnBean(ReactiveRedisTemplate.class)
public class RateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    // Redis key prefixes for different time windows
    private static final String MINUTE_KEY_PREFIX = "rate_limit:minute:";
    private static final String HOUR_KEY_PREFIX = "rate_limit:hour:";
    private static final String DAY_KEY_PREFIX = "rate_limit:day:";
    private static final String SECOND_KEY_PREFIX = "rate_limit:second:";
    private static final String BURST_KEY_PREFIX = "rate_limit:burst:";
    
    // Pattern tracking keys
    private static final String ERROR_COUNT_KEY_PREFIX = "error_count:";
    private static final String ENDPOINT_COUNT_KEY_PREFIX = "endpoint_count:";
    
    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    @Autowired
    private IpBlacklistService ipBlacklistService;
    
    /**
     * Check if request should be rate limited
     */
    public Mono<RateLimitResult> checkRateLimit(String ip, String endpoint) {
        // Skip rate limiting for whitelisted IPs
        if (ipBlacklistService.isWhitelisted(ip)) {
            return Mono.just(new RateLimitResult(false, "Whitelisted IP"));
        }
        
        String currentMinute = getCurrentMinute();
        String currentHour = getCurrentHour();
        String currentDay = getCurrentDay();
        String currentSecond = getCurrentSecond();
        
        // Check all rate limits in parallel
        return Mono.zip(
                checkMinuteLimit(ip, currentMinute),
                checkHourLimit(ip, currentHour),
                checkDayLimit(ip, currentDay),
                checkSecondLimit(ip, currentSecond),
                checkBurstLimit(ip)
        ).flatMap(tuple -> {
            boolean minuteExceeded = tuple.getT1();
            boolean hourExceeded = tuple.getT2();
            boolean dayExceeded = tuple.getT3();
            boolean secondExceeded = tuple.getT4();
            boolean burstExceeded = tuple.getT5();
            
            if (secondExceeded) {
                return handleDDoSDetection(ip, "Too many requests per second")
                        .then(Mono.just(new RateLimitResult(true, "DDoS protection triggered")));
            }
            
            if (minuteExceeded) {
                return handleDDoSDetection(ip, "Too many requests per minute")
                        .then(Mono.just(new RateLimitResult(true, "Rate limit exceeded (per minute)")));
            }
            
            if (hourExceeded) {
                return Mono.just(new RateLimitResult(true, "Rate limit exceeded (per hour)"));
            }
            
            if (dayExceeded) {
                return Mono.just(new RateLimitResult(true, "Rate limit exceeded (per day)"));
            }
            
            if (burstExceeded) {
                return Mono.just(new RateLimitResult(true, "Burst limit exceeded"));
            }
            
            // Increment counters if not rate limited
            return incrementCounters(ip, currentMinute, currentHour, currentDay, currentSecond, endpoint)
                    .then(Mono.just(new RateLimitResult(false, "Request allowed")));
        });
    }
    
    private Mono<Boolean> checkMinuteLimit(String ip, String currentMinute) {
        String key = MINUTE_KEY_PREFIX + ip + ":" + currentMinute;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Integer.parseInt(count) >= rateLimitConfig.getRequestsPerMinute())
                .defaultIfEmpty(false);
    }
    
    private Mono<Boolean> checkHourLimit(String ip, String currentHour) {
        String key = HOUR_KEY_PREFIX + ip + ":" + currentHour;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Integer.parseInt(count) >= rateLimitConfig.getRequestsPerHour())
                .defaultIfEmpty(false);
    }
    
    private Mono<Boolean> checkDayLimit(String ip, String currentDay) {
        String key = DAY_KEY_PREFIX + ip + ":" + currentDay;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Integer.parseInt(count) >= rateLimitConfig.getRequestsPerDay())
                .defaultIfEmpty(false);
    }
    
    private Mono<Boolean> checkSecondLimit(String ip, String currentSecond) {
        String key = SECOND_KEY_PREFIX + ip + ":" + currentSecond;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Integer.parseInt(count) >= rateLimitConfig.getDdosThresholdPerSecond())
                .defaultIfEmpty(false);
    }
    
    private Mono<Boolean> checkBurstLimit(String ip) {
        String key = BURST_KEY_PREFIX + ip;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Integer.parseInt(count) >= rateLimitConfig.getBurstCapacity())
                .defaultIfEmpty(false);
    }
    
    private Mono<Void> incrementCounters(String ip, String currentMinute, String currentHour, 
                                       String currentDay, String currentSecond, String endpoint) {
        return Mono.when(
                incrementCounter(MINUTE_KEY_PREFIX + ip + ":" + currentMinute, Duration.ofMinutes(1)),
                incrementCounter(HOUR_KEY_PREFIX + ip + ":" + currentHour, Duration.ofHours(1)),
                incrementCounter(DAY_KEY_PREFIX + ip + ":" + currentDay, Duration.ofDays(1)),
                incrementCounter(SECOND_KEY_PREFIX + ip + ":" + currentSecond, Duration.ofSeconds(1)),
                incrementBurstCounter(ip),
                trackEndpointUsage(ip, endpoint)
        );
    }
    
    private Mono<Void> incrementCounter(String key, Duration expiration) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, expiration).then();
                    }
                    return Mono.empty();
                });
    }
    
    private Mono<Void> incrementBurstCounter(String ip) {
        String key = BURST_KEY_PREFIX + ip;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofSeconds(10)).then();
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    private Mono<Void> trackEndpointUsage(String ip, String endpoint) {
        String key = ENDPOINT_COUNT_KEY_PREFIX + ip + ":" + endpoint + ":" + getCurrentMinute();
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofMinutes(1)).then();
                    }
                    if (count >= rateLimitConfig.getMaxSameEndpointRequests()) {
                        return ipBlacklistService.markSuspicious(ip, "Excessive same endpoint requests: " + endpoint);
                    }
                    return Mono.empty();
                });
    }
    
    private Mono<Void> handleDDoSDetection(String ip, String reason) {
        logger.warn("DDoS attack detected from IP: {} - {}", ip, reason);
        return ipBlacklistService.blockTemporarily(ip, rateLimitConfig.getDdosBlockDurationMinutes(), reason);
    }
    
    /**
     * Track error responses for suspicious pattern detection
     */
    public Mono<Void> trackErrorResponse(String ip, int statusCode) {
        if (statusCode >= 400) {
            String key = ERROR_COUNT_KEY_PREFIX + ip + ":" + getCurrentMinute();
            return redisTemplate.opsForValue().increment(key)
                    .flatMap(count -> {
                        if (count == 1) {
                            return redisTemplate.expire(key, Duration.ofMinutes(1)).then();
                        }
                        if (count >= rateLimitConfig.getMaxConsecutiveErrors()) {
                            return ipBlacklistService.blockTemporarily(ip, 
                                    rateLimitConfig.getSuspiciousPatternBlockMinutes(),
                                    "Too many error responses");
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
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
    
    /**
     * Result class for rate limit checks
     */
    public static class RateLimitResult {
        private final boolean rateLimited;
        private final String reason;
        
        public RateLimitResult(boolean rateLimited, String reason) {
            this.rateLimited = rateLimited;
            this.reason = reason;
        }
        
        public boolean isRateLimited() {
            return rateLimited;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
