import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.time.Duration;

public class TestDDoSProtection {
    
    private static HttpClient createInsecureHttpClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void main(String[] args) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String baseUrl = "http://localhost:31304";
        
        System.out.println("=== Testing DDoS Protection ===");
        System.out.println("Gateway URL: " + baseUrl);
        System.out.println();
        
        // Test 1: Health check
        System.out.println("1. Testing health endpoint...");
        testEndpoint(client, baseUrl + "/admin/security/health");
        
        // Test 2: Configuration endpoint
        System.out.println("\n2. Testing configuration endpoint...");
        testEndpoint(client, baseUrl + "/admin/security/config");
        
        // Test 3: Rate limiting simulation
        System.out.println("\n3. Testing rate limiting (sending 10 rapid requests)...");
        for (int i = 1; i <= 10; i++) {
            System.out.printf("Request %d: ", i);
            testEndpoint(client, baseUrl + "/admin/security/health");
            
            // Small delay between requests
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Test 4: IP status check
        System.out.println("\n4. Testing IP status endpoint...");
        testEndpoint(client, baseUrl + "/admin/security/ip/127.0.0.1/status");
        
        System.out.println("\n=== Test Complete ===");
        System.out.println("Check the gateway logs for security events and rate limiting information.");
    }
    
    private static void testEndpoint(HttpClient client, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.printf("Status: %d", response.statusCode());
            
            // Check for rate limiting headers
            response.headers().firstValue("X-RateLimit-Limited").ifPresent(value -> 
                System.out.print(" [RATE LIMITED]"));
            response.headers().firstValue("X-RateLimit-Blocked").ifPresent(value -> 
                System.out.print(" [BLOCKED]"));
            response.headers().firstValue("Retry-After").ifPresent(value -> 
                System.out.printf(" [Retry-After: %s]", value));
            
            if (response.statusCode() == 200 && response.body().length() < 200) {
                System.out.printf(" - %s", response.body().trim());
            } else if (response.statusCode() == 429) {
                System.out.print(" - Rate limited!");
            }
            
            System.out.println();
            
        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
