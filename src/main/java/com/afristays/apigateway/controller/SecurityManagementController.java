package com.afristays.apigateway.controller;

import com.afristays.apigateway.config.RateLimitConfig;
import com.afristays.apigateway.service.IpBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/security")
public class SecurityManagementController {
    
    @Autowired
    private IpBlacklistService ipBlacklistService;
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    /**
     * Get current rate limit configuration
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("requestsPerMinute", rateLimitConfig.getRequestsPerMinute());
        config.put("requestsPerHour", rateLimitConfig.getRequestsPerHour());
        config.put("requestsPerDay", rateLimitConfig.getRequestsPerDay());
        config.put("ddosThresholdPerMinute", rateLimitConfig.getDdosThresholdPerMinute());
        config.put("ddosThresholdPerSecond", rateLimitConfig.getDdosThresholdPerSecond());
        config.put("ddosBlockDurationMinutes", rateLimitConfig.getDdosBlockDurationMinutes());
        config.put("maxConsecutiveErrors", rateLimitConfig.getMaxConsecutiveErrors());
        config.put("exemptPaths", rateLimitConfig.getExemptPaths());
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Check if an IP is blocked
     */
    @GetMapping("/ip/{ip}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getIpStatus(@PathVariable String ip) {
        return ipBlacklistService.isBlocked(ip)
                .flatMap(isBlocked -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("ip", ip);
                    status.put("blocked", isBlocked);
                    status.put("whitelisted", ipBlacklistService.isWhitelisted(ip));
                    
                    if (isBlocked) {
                        return ipBlacklistService.getBlockInfo(ip)
                                .map(blockInfo -> {
                                    status.put("blockInfo", blockInfo);
                                    return ResponseEntity.ok(status);
                                })
                                .defaultIfEmpty(ResponseEntity.ok(status));
                    }
                    
                    return Mono.just(ResponseEntity.ok(status));
                });
    }
    
    /**
     * Block an IP temporarily
     */
    @PostMapping("/ip/{ip}/block")
    public Mono<ResponseEntity<Map<String, String>>> blockIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "15") int durationMinutes,
            @RequestParam(defaultValue = "Manual block") String reason) {
        
        return ipBlacklistService.blockTemporarily(ip, durationMinutes, reason)
                .then(Mono.fromCallable(() -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "IP blocked successfully");
                    response.put("ip", ip);
                    response.put("duration", durationMinutes + " minutes");
                    response.put("reason", reason);
                    return ResponseEntity.ok(response);
                }));
    }
    
    /**
     * Add IP to permanent blacklist
     */
    @PostMapping("/ip/{ip}/blacklist")
    public Mono<ResponseEntity<Map<String, String>>> blacklistIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "Manual blacklist") String reason) {
        
        return ipBlacklistService.addToPermanentBlacklist(ip, reason)
                .then(Mono.fromCallable(() -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "IP permanently blacklisted");
                    response.put("ip", ip);
                    response.put("reason", reason);
                    return ResponseEntity.ok(response);
                }));
    }
    
    /**
     * Remove IP from blacklist
     */
    @DeleteMapping("/ip/{ip}/blacklist")
    public Mono<ResponseEntity<Map<String, String>>> unblockIp(@PathVariable String ip) {
        return ipBlacklistService.removeFromBlacklist(ip)
                .then(Mono.fromCallable(() -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "IP removed from blacklist");
                    response.put("ip", ip);
                    return ResponseEntity.ok(response);
                }));
    }
    
    /**
     * Add IP to whitelist
     */
    @PostMapping("/ip/{ip}/whitelist")
    public ResponseEntity<Map<String, String>> whitelistIp(@PathVariable String ip) {
        ipBlacklistService.addToWhitelist(ip);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "IP added to whitelist");
        response.put("ip", ip);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Remove IP from whitelist
     */
    @DeleteMapping("/ip/{ip}/whitelist")
    public ResponseEntity<Map<String, String>> removeFromWhitelist(@PathVariable String ip) {
        ipBlacklistService.removeFromWhitelist(ip);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "IP removed from whitelist");
        response.put("ip", ip);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check for security services
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "DDoS Protection");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }
}
