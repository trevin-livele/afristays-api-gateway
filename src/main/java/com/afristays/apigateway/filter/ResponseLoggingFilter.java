package com.afristays.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingFilter.class);
    private static final Logger responseLogger = LoggerFactory.getLogger("RESPONSE_LOGGER");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    logOutgoingResponse(exchange);
                }));
    }

    private void logOutgoingResponse(ServerWebExchange exchange) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            
            // Get request details
            String requestId = (String) exchange.getAttributes().get("requestId");
            final String finalRequestId = requestId != null ? requestId : "unknown";

            final String clientIp = getClientIpAddress(request);
            Long startTime = (Long) exchange.getAttributes().get("startTime");
            long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;
            
            // Set MDC for structured logging
            MDC.put("requestId", finalRequestId);
            MDC.put("clientIp", clientIp);
            MDC.put("method", request.getMethod().toString());
            MDC.put("path", request.getPath().value());
            
            // Get response details
            HttpStatusCode statusCode = response.getStatusCode();
            int status = statusCode != null ? statusCode.value() : 0;
            String statusText = "Unknown";
            if (statusCode instanceof HttpStatus) {
                statusText = ((HttpStatus) statusCode).getReasonPhrase();
            }
            
            // Get response headers
            HttpHeaders responseHeaders = response.getHeaders();
            String contentType = responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
            String contentLength = responseHeaders.getFirst(HttpHeaders.CONTENT_LENGTH);
            String cacheControl = responseHeaders.getFirst(HttpHeaders.CACHE_CONTROL);
            String location = responseHeaders.getFirst(HttpHeaders.LOCATION);
            
            // Log structured response information
            String timestamp = LocalDateTime.now().format(formatter);
            String method = request.getMethod().toString();
            String path = request.getPath().value();
            String queryString = request.getURI().getQuery();
            String fullUrl = path + (queryString != null ? "?" + queryString : "");
            
            responseLogger.info("OUTGOING_RESPONSE | {} | {} | {} | {} | {} | {} | {} | {}ms | {} | {} | {} | {} | {}",
                    timestamp,
                    finalRequestId,
                    clientIp,
                    method,
                    fullUrl,
                    status,
                    statusText,
                    duration,
                    contentType != null ? contentType : "N/A",
                    contentLength != null ? contentLength : "N/A",
                    cacheControl != null ? cacheControl : "N/A",
                    location != null ? location : "N/A",
                    responseHeaders.size()
            );

            // Log response headers in debug mode
            if (logger.isDebugEnabled()) {
                responseHeaders.forEach((name, values) -> {
                    logger.debug("RESPONSE_HEADER | {} | {} | {}: {}", finalRequestId, clientIp, name, values);
                });
            }

            // Log error responses with more detail
            if (status >= 400) {
                logger.warn("ERROR_RESPONSE | {} | {} | {} | {} | {} | {} | {}ms",
                        finalRequestId, clientIp, method, fullUrl, status, statusText, duration);
            }

            // Log slow responses
            if (duration > 5000) { // More than 5 seconds
                logger.warn("SLOW_RESPONSE | {} | {} | {} | {} | {} | {}ms",
                        finalRequestId, clientIp, method, fullUrl, status, duration);
            }
            
        } catch (Exception e) {
            logger.error("Error logging outgoing response: {}", e.getMessage(), e);
        } finally {
            // Clear MDC
            MDC.clear();
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
        // Execute late in the filter chain to capture final response
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
