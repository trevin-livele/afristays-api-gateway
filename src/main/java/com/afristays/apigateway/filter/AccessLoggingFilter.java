package com.afristays.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;

@Component
public class AccessLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger accessLogger = LoggerFactory.getLogger("REQUEST_LOGGER");
    private static final DateTimeFormatter accessLogFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    logAccessEntry(exchange, startTime);
                }));
    }

    private void logAccessEntry(ServerWebExchange exchange, long startTime) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            
            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Get client IP
            String clientIp = getClientIpAddress(request);
            
            // Get request details
            String method = request.getMethod().toString();
            String uri = request.getURI().toString();
            String protocol = "HTTP/1.1"; // Default for Spring WebFlux
            
            // Get response details
            HttpStatusCode statusCode = response.getStatusCode();
            int status = statusCode != null ? statusCode.value() : 0;
            
            // Get response size
            String contentLength = response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
            String responseSize = contentLength != null ? contentLength : "-";
            
            // Get request headers
            String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
            String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
            
            // Format timestamp
            String timestamp = LocalDateTime.now().format(accessLogFormatter);
            
            // Create Apache Combined Log Format entry
            // Format: IP - - [timestamp] "METHOD URI PROTOCOL" status size "referer" "user-agent" response_time_ms
            String logEntry = String.format("%s - - [%s] \"%s %s %s\" %d %s \"%s\" \"%s\" %dms",
                    clientIp,
                    timestamp,
                    method,
                    uri,
                    protocol,
                    status,
                    responseSize,
                    referer != null ? referer : "-",
                    userAgent != null ? userAgent : "-",
                    responseTime
            );
            
            accessLogger.info(logEntry);
            
        } catch (Exception e) {
            // Don't let logging errors affect the request
            // Log to a different logger to avoid recursion
            LoggerFactory.getLogger(AccessLoggingFilter.class).error("Error creating access log entry: {}", e.getMessage());
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
        // Execute after response logging filter
        return Ordered.LOWEST_PRECEDENCE;
    }
}
