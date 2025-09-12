package com.afristays.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "afristays.rate-limit")
public class RateLimitConfig {
    
    // Basic rate limiting
    private int requestsPerMinute = 60;
    private int requestsPerHour = 1000;
    private int requestsPerDay = 10000;
    
    // Burst protection
    private int burstCapacity = 10;
    private int burstRefillRate = 5;
    
    // DDoS protection thresholds
    private int ddosThresholdPerMinute = 100;
    private int ddosThresholdPerSecond = 10;
    private int ddosBlockDurationMinutes = 15;
    
    // Suspicious pattern detection
    private int maxConsecutiveErrors = 10;
    private int maxSameEndpointRequests = 50;
    private int suspiciousPatternBlockMinutes = 5;
    
    // IP whitelist and blacklist
    private String[] whitelistedIps = {};
    private String[] blacklistedIps = {};
    
    // Rate limit bypass for certain paths
    private String[] exemptPaths = {"/actuator/health", "/api/v1/auth/health"};
    
    // Getters and setters
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
    
    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
    
    public int getRequestsPerHour() {
        return requestsPerHour;
    }
    
    public void setRequestsPerHour(int requestsPerHour) {
        this.requestsPerHour = requestsPerHour;
    }
    
    public int getRequestsPerDay() {
        return requestsPerDay;
    }
    
    public void setRequestsPerDay(int requestsPerDay) {
        this.requestsPerDay = requestsPerDay;
    }
    
    public int getBurstCapacity() {
        return burstCapacity;
    }
    
    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }
    
    public int getBurstRefillRate() {
        return burstRefillRate;
    }
    
    public void setBurstRefillRate(int burstRefillRate) {
        this.burstRefillRate = burstRefillRate;
    }
    
    public int getDdosThresholdPerMinute() {
        return ddosThresholdPerMinute;
    }
    
    public void setDdosThresholdPerMinute(int ddosThresholdPerMinute) {
        this.ddosThresholdPerMinute = ddosThresholdPerMinute;
    }
    
    public int getDdosThresholdPerSecond() {
        return ddosThresholdPerSecond;
    }
    
    public void setDdosThresholdPerSecond(int ddosThresholdPerSecond) {
        this.ddosThresholdPerSecond = ddosThresholdPerSecond;
    }
    
    public int getDdosBlockDurationMinutes() {
        return ddosBlockDurationMinutes;
    }
    
    public void setDdosBlockDurationMinutes(int ddosBlockDurationMinutes) {
        this.ddosBlockDurationMinutes = ddosBlockDurationMinutes;
    }
    
    public int getMaxConsecutiveErrors() {
        return maxConsecutiveErrors;
    }
    
    public void setMaxConsecutiveErrors(int maxConsecutiveErrors) {
        this.maxConsecutiveErrors = maxConsecutiveErrors;
    }
    
    public int getMaxSameEndpointRequests() {
        return maxSameEndpointRequests;
    }
    
    public void setMaxSameEndpointRequests(int maxSameEndpointRequests) {
        this.maxSameEndpointRequests = maxSameEndpointRequests;
    }
    
    public int getSuspiciousPatternBlockMinutes() {
        return suspiciousPatternBlockMinutes;
    }
    
    public void setSuspiciousPatternBlockMinutes(int suspiciousPatternBlockMinutes) {
        this.suspiciousPatternBlockMinutes = suspiciousPatternBlockMinutes;
    }
    
    public String[] getWhitelistedIps() {
        return whitelistedIps;
    }
    
    public void setWhitelistedIps(String[] whitelistedIps) {
        this.whitelistedIps = whitelistedIps;
    }
    
    public String[] getBlacklistedIps() {
        return blacklistedIps;
    }
    
    public void setBlacklistedIps(String[] blacklistedIps) {
        this.blacklistedIps = blacklistedIps;
    }
    
    public String[] getExemptPaths() {
        return exemptPaths;
    }
    
    public void setExemptPaths(String[] exemptPaths) {
        this.exemptPaths = exemptPaths;
    }
}
