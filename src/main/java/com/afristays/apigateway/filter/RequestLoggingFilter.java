package com.afristays.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Logger requestLogger = LoggerFactory.getLogger("REQUEST_LOGGER");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Generate unique request ID for tracking
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // Extract client IP address
        String clientIp = getClientIpAddress(request);
        
        // Set MDC for structured logging
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        MDC.put("method", request.getMethod().toString());
        MDC.put("path", request.getPath().value());
        
        // Store request start time
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put("startTime", startTime);
        exchange.getAttributes().put("requestId", requestId);
        
        // Log incoming request details
        logIncomingRequest(request, clientIp, requestId);
        
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // Calculate request duration
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    
                    // Log request completion
                    logRequestCompletion(requestId, clientIp, request.getMethod().toString(), 
                                       request.getPath().value(), duration, signalType.toString());
                    
                    // Clear MDC
                    MDC.clear();
                });
    }

    private void logIncomingRequest(ServerHttpRequest request, String clientIp, String requestId) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String method = request.getMethod().toString();
            String path = request.getPath().value();
            String queryString = request.getURI().getQuery();
            String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
            String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
            String contentType = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            
            // Build full URL
            String fullUrl = path + (queryString != null ? "?" + queryString : "");
            
            // Log structured request information
            requestLogger.info("INCOMING_REQUEST | {} | {} | {} | {} | {} | {} | {} | {} | {} | {}", 
                    timestamp,
                    requestId,
                    clientIp,
                    method,
                    fullUrl,
                    userAgent != null ? userAgent : "N/A",
                    referer != null ? referer : "N/A",
                    contentType != null ? contentType : "N/A",
                    authorization != null ? "Bearer ***" : "N/A",
                    request.getHeaders().size()
            );
            
            // Log all headers in debug mode
            if (logger.isDebugEnabled()) {
                request.getHeaders().forEach((name, values) -> {
                    if (!name.equalsIgnoreCase("authorization") && !name.equalsIgnoreCase("cookie")) {
                        logger.debug("REQUEST_HEADER | {} | {} | {}: {}", requestId, clientIp, name, values);
                    }
                });
            }
            
        } catch (Exception e) {
            logger.error("Error logging incoming request: {}", e.getMessage(), e);
        }
    }

    private void logRequestCompletion(String requestId, String clientIp, String method, 
                                    String path, long duration, String signalType) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            
            requestLogger.info("REQUEST_COMPLETED | {} | {} | {} | {} | {} | {}ms | {}", 
                    timestamp,
                    requestId,
                    clientIp,
                    method,
                    path,
                    duration,
                    signalType
            );
            
        } catch (Exception e) {
            logger.error("Error logging request completion: {}", e.getMessage(), e);
        }
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        // Check for X-Forwarded-For header (common in load balancers/proxies)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (nginx)
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        // Check for X-Forwarded header
        String xForwarded = request.getHeaders().getFirst("X-Forwarded");
        if (xForwarded != null && !xForwarded.isEmpty() && !"unknown".equalsIgnoreCase(xForwarded)) {
            return xForwarded;
        }
        
        // Check for Forwarded header (RFC 7239)
        String forwarded = request.getHeaders().getFirst("Forwarded");
        if (forwarded != null && !forwarded.isEmpty()) {
            // Parse Forwarded header: for=192.0.2.60;proto=http;by=203.0.113.43
            String[] parts = forwarded.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("for=")) {
                    String forValue = part.trim().substring(4);
                    // Remove quotes and brackets if present
                    forValue = forValue.replaceAll("[\"\\[\\]]", "");
                    return forValue;
                }
            }
        }
        
        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    @Override
    public int getOrder() {
        // Execute early in the filter chain
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
