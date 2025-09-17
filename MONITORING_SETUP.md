# Monitoring Setup Guide for Afristays API Gateway

## Overview
This guide explains how to set up comprehensive monitoring for the Afristays API Gateway using Prometheus and Grafana.

## Prerequisites
- Java 21+
- Maven 3.6+
- Redis server running on localhost:6379
- Docker (for Prometheus and Grafana)

## Setup Steps

### 1. Build and Start the API Gateway

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### 2. Verify Actuator Endpoints

Once the application is running, test these endpoints:

#### Health Check
```bash
curl http://localhost:31304/actuator/health
```

#### Prometheus Metrics
```bash
curl http://localhost:31304/actuator/prometheus
```

#### Gateway Metrics
```bash
curl http://localhost:31304/actuator/metrics
```

#### Gateway Routes
```bash
curl http://localhost:31304/actuator/gateway/routes
```

### 3. Start Prometheus

```bash
# Using Docker
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus:latest

# Or using Docker Compose (recommended)
# Create docker-compose.yml with Prometheus and Grafana
```

### 4. Start Grafana

```bash
# Using Docker
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana:latest
```

### 5. Configure Grafana

1. Access Grafana at http://localhost:3000
2. Login with admin/admin (change password when prompted)
3. Add Prometheus as a data source:
   - URL: http://localhost:9090
   - Access: Browser
   - Save & Test

4. Import the dashboard:
   - Copy the contents of `grafana-dashboard.json`
   - In Grafana: + → Import → Paste JSON

## Available Metrics

### Standard Spring Boot Metrics
- `http_server_requests_seconds_count` - HTTP request count
- `http_server_requests_seconds_sum` - HTTP request duration sum
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_threads_live_threads` - Active thread count
- `system_cpu_usage` - System CPU usage

### Custom Afristays Metrics
- `afristays_rate_limit_violations_total` - Total rate limit violations
- `afristays_blocked_requests_total` - Total blocked requests
- `afristays_ddos_detections_total` - Total DDoS detections
- `afristays_whitelisted_requests_total` - Total whitelisted requests
- `afristays_redis_connections_active` - Active Redis connections
- `afristays_rate_limit_violations_current` - Current rate limit violations

### Spring Cloud Gateway Metrics
- `spring_cloud_gateway_requests_seconds_count` - Gateway request count by route
- `spring_cloud_gateway_requests_seconds_sum` - Gateway request duration by route

## Testing Rate Limiting Metrics

### Generate Test Traffic
```bash
# Generate normal traffic
for i in {1..50}; do curl http://localhost:31304/test/get; done

# Generate rate limit violations (exceed 120 requests/minute)
for i in {1..150}; do curl http://localhost:31304/test/get; done

# Generate burst traffic (exceed 20 requests in 10 seconds)
for i in {1..25}; do curl http://localhost:31304/test/get & done; wait
```

### Monitor in Grafana
1. Check the "Rate Limit Violations" panel
2. Watch "Blocked Requests" counter
3. Monitor "Request Rate" trends

## Docker Compose Setup (Recommended)

Create `docker-compose.monitoring.yml`:

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--web.enable-lifecycle'

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-storage:/var/lib/grafana

  redis:
    image: redis:alpine
    container_name: redis
    ports:
      - "6379:6379"

volumes:
  grafana-storage:
```

Run with:
```bash
docker-compose -f docker-compose.monitoring.yml up -d
```

## Troubleshooting

### Prometheus Can't Scrape Metrics
- Check if the API Gateway is running on port 31304
- Verify the `/actuator/prometheus` endpoint is accessible
- Check Prometheus configuration syntax

### No Custom Metrics in Grafana
- Verify the `micrometer-registry-prometheus` dependency is included
- Check that custom metrics are being created in `MetricsConfig`
- Ensure rate limiting is being triggered to generate metrics

### Grafana Dashboard Issues
- Verify Prometheus data source is configured correctly
- Check that metric names match between Prometheus and Grafana queries
- Verify time ranges and refresh intervals

## Key Endpoints Summary

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Application health status |
| `/actuator/prometheus` | Prometheus metrics export |
| `/actuator/metrics` | Available metrics list |
| `/actuator/gateway/routes` | Gateway route information |
| `/actuator/info` | Application information |

## Monitoring Best Practices

1. **Set up alerts** for high rate limit violation rates
2. **Monitor response times** across different routes
3. **Track error rates** by status code
4. **Watch JVM metrics** for memory leaks
5. **Monitor Redis connectivity** for rate limiting functionality
6. **Set up dashboards** for different stakeholder groups (developers, ops, business)

## Production Considerations

1. **Security**: Restrict actuator endpoints in production
2. **Performance**: Consider metrics export frequency
3. **Retention**: Configure appropriate data retention policies
4. **Scaling**: Use Prometheus federation for multiple instances
5. **Authentication**: Secure Grafana with proper authentication
