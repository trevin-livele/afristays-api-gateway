package com.afristays.apigateway.service;

import com.afristays.apigateway.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpBlacklistService {
    
    private static final Logger logger = LoggerFactory.getLogger(IpBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:ip:";
    private static final String TEMP_BLOCK_KEY_PREFIX = "temp_block:ip:";
    private static final String SUSPICIOUS_KEY_PREFIX = "suspicious:ip:";
    
    @Autowired(required = false)
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    // In-memory cache for frequently checked IPs
    private final Set<String> permanentBlacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    // In-memory fallback storage when Redis is not available
    private final ConcurrentHashMap<String, Long> tempBlockedIps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tempBlockReasons = new ConcurrentHashMap<>();
    
    public void initializeLists() {
        // Initialize permanent blacklist and whitelist from config
        if (rateLimitConfig.getBlacklistedIps() != null) {
            permanentBlacklist.addAll(Arrays.asList(rateLimitConfig.getBlacklistedIps()));
        }
        if (rateLimitConfig.getWhitelistedIps() != null) {
            whitelist.addAll(Arrays.asList(rateLimitConfig.getWhitelistedIps()));
        }
        logger.info("Initialized IP lists - Blacklisted: {}, Whitelisted: {}", 
                   permanentBlacklist.size(), whitelist.size());
    }
    
    /**
     * Check if an IP is blocked (either permanently or temporarily)
     */
    public Mono<Boolean> isBlocked(String ip) {
        // Check whitelist first
        if (whitelist.contains(ip)) {
            return Mono.just(false);
        }

        // Check permanent blacklist
        if (permanentBlacklist.contains(ip)) {
            logger.warn("IP {} is permanently blacklisted", ip);
            return Mono.just(true);
        }

        // Check temporary blocks
        if (redisTemplate != null) {
            // Use Redis if available
            return redisTemplate.hasKey(TEMP_BLOCK_KEY_PREFIX + ip)
                    .doOnNext(blocked -> {
                        if (blocked) {
                            logger.warn("IP {} is temporarily blocked", ip);
                        }
                    });
        } else {
            // Use in-memory storage as fallback
            Long blockExpiry = tempBlockedIps.get(ip);
            if (blockExpiry != null && System.currentTimeMillis() < blockExpiry) {
                logger.warn("IP {} is temporarily blocked (in-memory)", ip);
                return Mono.just(true);
            } else if (blockExpiry != null) {
                // Block has expired, remove it
                tempBlockedIps.remove(ip);
                tempBlockReasons.remove(ip);
            }
            return Mono.just(false);
        }
    }
    
    /**
     * Temporarily block an IP for DDoS protection
     */
    public Mono<Void> blockTemporarily(String ip, int durationMinutes, String reason) {
        if (redisTemplate != null) {
            // Use Redis if available
            String key = TEMP_BLOCK_KEY_PREFIX + ip;
            String blockInfo = String.format("blocked_at:%s,reason:%s",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                            reason);

            return redisTemplate.opsForValue()
                    .set(key, blockInfo, Duration.ofMinutes(durationMinutes))
                    .doOnSuccess(result -> {
                        logger.warn("Temporarily blocked IP {} for {} minutes. Reason: {}",
                                   ip, durationMinutes, reason);
                    })
                    .then();
        } else {
            // Use in-memory storage as fallback
            long expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            tempBlockedIps.put(ip, expiryTime);
            tempBlockReasons.put(ip, reason);
            logger.warn("Temporarily blocked IP {} for {} minutes (in-memory). Reason: {}",
                       ip, durationMinutes, reason);
            return Mono.empty();
        }
    }
    
    /**
     * Add IP to permanent blacklist
     */
    public Mono<Void> addToPermanentBlacklist(String ip, String reason) {
        permanentBlacklist.add(ip);
        logger.error("Permanently blacklisted IP {}. Reason: {}", ip, reason);

        if (redisTemplate != null) {
            String key = BLACKLIST_KEY_PREFIX + ip;
            String blockInfo = String.format("blocked_at:%s,reason:%s",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                            reason);

            return redisTemplate.opsForValue()
                    .set(key, blockInfo)
                    .then();
        } else {
            return Mono.empty();
        }
    }
    
    /**
     * Remove IP from blacklist
     */
    public Mono<Void> removeFromBlacklist(String ip) {
        permanentBlacklist.remove(ip);
        tempBlockedIps.remove(ip);
        tempBlockReasons.remove(ip);
        logger.info("Removed IP {} from blacklist", ip);

        if (redisTemplate != null) {
            String tempKey = TEMP_BLOCK_KEY_PREFIX + ip;
            String permKey = BLACKLIST_KEY_PREFIX + ip;

            return redisTemplate.delete(tempKey, permKey).then();
        } else {
            return Mono.empty();
        }
    }
    
    /**
     * Mark IP as suspicious for pattern detection
     */
    public Mono<Void> markSuspicious(String ip, String pattern) {
        logger.warn("Marked IP {} as suspicious. Pattern: {}", ip, pattern);

        if (redisTemplate != null) {
            String key = SUSPICIOUS_KEY_PREFIX + ip;
            String suspiciousInfo = String.format("pattern:%s,detected_at:%s",
                                                 pattern,
                                                 LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return redisTemplate.opsForValue()
                    .set(key, suspiciousInfo, Duration.ofHours(1))
                    .then();
        } else {
            return Mono.empty();
        }
    }
    
    /**
     * Check if IP is whitelisted
     */
    public boolean isWhitelisted(String ip) {
        return whitelist.contains(ip);
    }
    
    /**
     * Add IP to whitelist
     */
    public void addToWhitelist(String ip) {
        whitelist.add(ip);
        logger.info("Added IP {} to whitelist", ip);
    }
    
    /**
     * Remove IP from whitelist
     */
    public void removeFromWhitelist(String ip) {
        whitelist.remove(ip);
        logger.info("Removed IP {} from whitelist", ip);
    }
    
    /**
     * Get block information for an IP
     */
    public Mono<String> getBlockInfo(String ip) {
        if (redisTemplate != null) {
            if (permanentBlacklist.contains(ip)) {
                return redisTemplate.opsForValue().get(BLACKLIST_KEY_PREFIX + ip);
            }
            return redisTemplate.opsForValue().get(TEMP_BLOCK_KEY_PREFIX + ip);
        } else {
            // Return in-memory block info
            if (permanentBlacklist.contains(ip)) {
                return Mono.just("Permanently blacklisted (in-memory)");
            }
            String reason = tempBlockReasons.get(ip);
            if (reason != null) {
                return Mono.just("Temporarily blocked: " + reason);
            }
            return Mono.empty();
        }
    }
}
