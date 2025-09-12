//package com.afristays.apigateway;
//
//import com.afristays.apigateway.config.RateLimitConfig;
//import com.afristays.apigateway.service.IpBlacklistService;
//import com.afristays.apigateway.service.RateLimitService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.data.redis.core.ReactiveRedisTemplate;
//import org.springframework.test.context.TestPropertySource;
//import reactor.core.publisher.Mono;
//import reactor.test.StepVerifier;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.when;
//
//@SpringBootTest
//@TestPropertySource(properties = {
//    "afristays.rate-limit.requests-per-minute=5",
//    "afristays.rate-limit.ddos-threshold-per-second=3"
//})
//public class DDoSProtectionTest {
//
//    @MockBean
//    private ReactiveRedisTemplate<String, String> redisTemplate;
//
//    private RateLimitService rateLimitService;
//    private IpBlacklistService ipBlacklistService;
//    private RateLimitConfig rateLimitConfig;
//
//    @BeforeEach
//    void setUp() {
//        rateLimitConfig = new RateLimitConfig();
//        rateLimitConfig.setRequestsPerMinute(5);
//        rateLimitConfig.setDdosThresholdPerSecond(3);
//        rateLimitConfig.setDdosBlockDurationMinutes(15);
//
//        ipBlacklistService = new IpBlacklistService();
//        rateLimitService = new RateLimitService();
//
//        // Mock Redis operations
//        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
//        when(redisTemplate.opsForValue().get(anyString())).thenReturn(Mono.empty());
//        when(redisTemplate.opsForValue().increment(anyString())).thenReturn(Mono.just(1L));
//        when(redisTemplate.opsForValue().set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
//        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
//    }
//
//    @Test
//    void testIpBlacklistBlocking() {
//        String testIp = "192.168.1.100";
//
//        // Test IP is not blocked initially
//        StepVerifier.create(ipBlacklistService.isBlocked(testIp))
//                .expectNext(false)
//                .verifyComplete();
//
//        // Block IP temporarily
//        StepVerifier.create(ipBlacklistService.blockTemporarily(testIp, 15, "Test block"))
//                .verifyComplete();
//
//        // Mock Redis to return true for blocked IP
//        when(redisTemplate.hasKey("temp_block:ip:" + testIp)).thenReturn(Mono.just(true));
//
//        // Test IP is now blocked
//        StepVerifier.create(ipBlacklistService.isBlocked(testIp))
//                .expectNext(true)
//                .verifyComplete();
//    }
//
//    @Test
//    void testWhitelistBypass() {
//        String whitelistedIp = "127.0.0.1";
//
//        // Add IP to whitelist
//        ipBlacklistService.addToWhitelist(whitelistedIp);
//
//        // Test whitelisted IP is not blocked
//        StepVerifier.create(ipBlacklistService.isBlocked(whitelistedIp))
//                .expectNext(false)
//                .verifyComplete();
//    }
//
//    @Test
//    void testRateLimitConfiguration() {
//        // Test configuration values
//        assert rateLimitConfig.getRequestsPerMinute() == 5;
//        assert rateLimitConfig.getDdosThresholdPerSecond() == 3;
//        assert rateLimitConfig.getDdosBlockDurationMinutes() == 15;
//    }
//}
