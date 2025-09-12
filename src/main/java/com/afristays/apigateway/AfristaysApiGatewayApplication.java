package com.afristays.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import com.afristays.apigateway.config.RateLimitConfig;




@SpringBootApplication
@EnableConfigurationProperties(RateLimitConfig.class)
public class AfristaysApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(AfristaysApiGatewayApplication.class, args);
	}



    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service Routes
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "API-Gateway"))
                        .uri("http://afristays-auth:31301"))

                // Legacy Auth Route (for backward compatibility)
                .route("auth-service-legacy", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .rewritePath("/api/auth/(?<remaining>.*)", "/api/v1/auth/${remaining}")
                                .addRequestHeader("X-Gateway", "API-Gateway"))
                        .uri("http://afristays-auth:31301"))

                // Booking Service Routes
                .route("booking-service", r -> r
                        .path("/api/bookings", "/api/bookings/**")
                        .filters(f -> f
                                .rewritePath("/api/bookings(?<remaining>.*)", "/booking-service/api/v1/bookings${remaining}")
                                .addRequestHeader("X-Gateway", "API-Gateway"))
                        .uri("http://afristays-booking:31303"))

                // Listings Service Routes
                .route("listings-service", r -> r
                        .path("/api/listings/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addRequestHeader("X-Gateway", "API-Gateway"))
                        .uri("http://afristays-listing:31300"))


                // Portal Switch Service Routes
                .route("portal-switch-service", r -> r
                        .path("/api/portal-switch/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "API-Gateway"))
                        .uri("http://portal-switch-service:31307"))


                // Health check route (gateway's own health)
                .route("health", r -> r
                        .path("/actuator/health")
                        .uri("http://173.249.1.107:31304"))
                .build();
    }



}
