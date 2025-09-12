package com.afristays.apigateway.filter;

import com.afristays.apigateway.config.RateLimitConfig;
import com.afristays.apigateway.service.IpBlacklistService;
import com.afristays.apigateway.service.RateLimitService;
import com.afristays.apigateway.service.InMemoryRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Component
public class DDoSProtectionFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(DDoSProtectionFilter.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_LOGGER");
    
    @Autowired
    private IpBlacklistService ipBlacklistService;

    @Autowired(required = false)
    private RateLimitService rateLimitService;

    @Autowired(required = false)
    private InMemoryRateLimitService inMemoryRateLimitService;

    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIpAddress(request);
        String path = request.getPath().value();
        String method = request.getMethod().toString();
        
        // Skip DDoS protection for exempt paths
        if (isExemptPath(path)) {
            return chain.filter(exchange);
        }
        
        // Check if IP is blocked
        return ipBlacklistService.isBlocked(clientIp)
                .flatMap(isBlocked -> {
                    if (isBlocked) {
                        return handleBlockedRequest(exchange, clientIp, "IP is blacklisted");
                    }
                    
                    // Check rate limits using available service
                    Mono<RateLimitService.RateLimitResult> rateLimitCheck;
                    if (rateLimitService != null) {
                        rateLimitCheck = rateLimitService.checkRateLimit(clientIp, path);
                    } else if (inMemoryRateLimitService != null) {
                        rateLimitCheck = inMemoryRateLimitService.checkRateLimit(clientIp, path);
                    } else {
                        // No rate limiting service available, allow request
                        rateLimitCheck = Mono.just(new RateLimitService.RateLimitResult(false, "No rate limiting service"));
                    }

                    return rateLimitCheck.flatMap(rateLimitResult -> {
                        if (rateLimitResult.isRateLimited()) {
                            return handleRateLimitedRequest(exchange, clientIp, rateLimitResult.getReason());
                        }

                        // Continue with the request and track response
                        return chain.filter(exchange)
                                .doFinally(signalType -> {
                                    // Track error responses for pattern detection
                                    ServerHttpResponse response = exchange.getResponse();
                                    if (response.getStatusCode() != null) {
                                        int statusCode = response.getStatusCode().value();

                                        // Track errors using available service
                                        if (rateLimitService != null) {
                                            rateLimitService.trackErrorResponse(clientIp, statusCode).subscribe();
                                        } else if (inMemoryRateLimitService != null) {
                                            inMemoryRateLimitService.trackErrorResponse(clientIp, statusCode).subscribe();
                                        }

                                        // Log security events
                                        logSecurityEvent(clientIp, method, path, statusCode);
                                    }
                                });
                    });
                })
                .onErrorResume(error -> {
                    logger.error("Error in DDoS protection filter for IP {}: {}", clientIp, error.getMessage(), error);
                    // Continue with request on error to avoid blocking legitimate traffic
                    return chain.filter(exchange);
                });
    }
    
    private boolean isExemptPath(String path) {
        return Arrays.stream(rateLimitConfig.getExemptPaths())
                .anyMatch(exemptPath -> path.startsWith(exemptPath));
    }
    
    private Mono<Void> handleBlockedRequest(ServerWebExchange exchange, String clientIp, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        
        // Log security event
        securityLogger.warn("BLOCKED_REQUEST | {} | {} | {} | {} | {}", 
                           LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                           clientIp,
                           exchange.getRequest().getMethod(),
                           exchange.getRequest().getPath().value(),
                           reason);
        
        // Set response headers
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-RateLimit-Blocked", "true");
        response.getHeaders().add("X-RateLimit-Reason", reason);
        response.getHeaders().add("Retry-After", "900"); // 15 minutes
        
        // Create error response body
        String errorBody = String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"%s\",\"timestamp\":\"%s\",\"status\":429}",
                reason,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
    
    private Mono<Void> handleRateLimitedRequest(ServerWebExchange exchange, String clientIp, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        
        // Log security event
        securityLogger.warn("RATE_LIMITED | {} | {} | {} | {} | {}", 
                           LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                           clientIp,
                           exchange.getRequest().getMethod(),
                           exchange.getRequest().getPath().value(),
                           reason);
        
        // Set response headers
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-RateLimit-Limited", "true");
        response.getHeaders().add("X-RateLimit-Reason", reason);
        response.getHeaders().add("Retry-After", "60"); // 1 minute
        
        // Create error response body
        String errorBody = String.format(
                "{\"error\":\"Rate Limit Exceeded\",\"message\":\"%s\",\"timestamp\":\"%s\",\"status\":429}",
                reason,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
    
    private void logSecurityEvent(String clientIp, String method, String path, int statusCode) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            if (statusCode == 429) {
                securityLogger.warn("RATE_LIMIT_RESPONSE | {} | {} | {} | {} | {}", 
                                   timestamp, clientIp, method, path, statusCode);
            } else if (statusCode >= 400 && statusCode < 500) {
                securityLogger.info("CLIENT_ERROR | {} | {} | {} | {} | {}", 
                                   timestamp, clientIp, method, path, statusCode);
            } else if (statusCode >= 500) {
                securityLogger.error("SERVER_ERROR | {} | {} | {} | {} | {}", 
                                    timestamp, clientIp, method, path, statusCode);
            }
        } catch (Exception e) {
            logger.error("Error logging security event: {}", e.getMessage());
        }
    }
    
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check for X-Forwarded-For header (common in load balancers/proxies)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (common in Nginx)
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        // Check for CF-Connecting-IP header (Cloudflare)
        String cfConnectingIp = request.getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.trim();
        }
        
        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    @Override
    public int getOrder() {
        // Run before other filters but after request logging
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
