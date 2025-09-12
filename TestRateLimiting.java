import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestRateLimiting {
    
    public static void main(String[] args) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        String baseUrl = "http://localhost:31304";
        
        System.out.println("=== Testing Rate Limiting with Rapid Requests ===");
        System.out.println("Gateway URL: " + baseUrl);
        System.out.println();
        
        // Test 1: Block a test IP first
        System.out.println("1. Blocking test IP 192.168.1.100...");
        testEndpoint(client, baseUrl + "/admin/security/ip/192.168.1.100/block?durationMinutes=1&reason=Test", "POST");
        
        // Test 2: Check if IP is blocked
        System.out.println("\n2. Checking if IP is blocked...");
        testEndpoint(client, baseUrl + "/admin/security/ip/192.168.1.100/status", "GET");
        
        // Test 3: Rapid fire requests to trigger rate limiting
        System.out.println("\n3. Sending 20 rapid requests to trigger rate limiting...");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 1; i <= 20; i++) {
            final int requestNum = i;
            executor.submit(() -> {
                System.out.printf("Request %d: ", requestNum);
                testEndpoint(client, baseUrl + "/admin/security/health", "GET");
            });
            
            // Very small delay to create burst
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Test 4: Check current configuration
        System.out.println("\n4. Checking current rate limit configuration...");
        testEndpoint(client, baseUrl + "/admin/security/config", "GET");
        
        System.out.println("\n=== Test Complete ===");
        System.out.println("Note: 127.0.0.1 is whitelisted, so rate limiting may not trigger from localhost.");
        System.out.println("Check the gateway logs for detailed security events.");
    }
    
    private static void testEndpoint(String url, String method) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        testEndpoint(client, url, method);
    }
    
    private static void testEndpoint(HttpClient client, String url, String method) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5));
            
            if ("POST".equals(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                requestBuilder.GET();
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.printf("Status: %d", response.statusCode());
            
            // Check for rate limiting headers
            response.headers().firstValue("X-RateLimit-Limited").ifPresent(value -> 
                System.out.print(" [RATE LIMITED]"));
            response.headers().firstValue("X-RateLimit-Blocked").ifPresent(value -> 
                System.out.print(" [BLOCKED]"));
            response.headers().firstValue("Retry-After").ifPresent(value -> 
                System.out.printf(" [Retry-After: %s]", value));
            
            if (response.statusCode() == 200 && response.body().length() < 300) {
                String body = response.body().trim();
                if (body.length() > 100) {
                    body = body.substring(0, 100) + "...";
                }
                System.out.printf(" - %s", body);
            } else if (response.statusCode() == 429) {
                System.out.print(" - Rate limited!");
                if (response.body().length() < 200) {
                    System.out.printf(" %s", response.body().trim());
                }
            }
            
            System.out.println();
            
        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
