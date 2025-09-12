# DDoS Protection and Rate Limiting System

This document describes the comprehensive DDoS protection and rate limiting system implemented in the Afristays API Gateway.

## Overview

The system provides multi-layered protection against DDoS attacks and abuse:

1. **Rate Limiting** - Controls request frequency per IP address
2. **DDoS Detection** - Identifies and blocks suspicious traffic patterns
3. **IP Blacklisting** - Manages blocked and whitelisted IP addresses
4. **Pattern Analysis** - Detects suspicious behavior patterns
5. **Security Monitoring** - Comprehensive logging and alerting

## Features

### Rate Limiting
- **Per-minute limits**: 60 requests/minute (configurable)
- **Per-hour limits**: 1,000 requests/hour (configurable)
- **Per-day limits**: 10,000 requests/day (configurable)
- **Burst protection**: 10 requests in 10 seconds (configurable)

### DDoS Protection
- **Per-second threshold**: 10 requests/second triggers DDoS protection
- **Per-minute threshold**: 100 requests/minute for sustained attacks
- **Automatic blocking**: 15-minute temporary blocks (configurable)
- **Pattern detection**: Identifies suspicious request patterns

### IP Management
- **Whitelist**: Bypass rate limiting for trusted IPs
- **Blacklist**: Permanent blocking of malicious IPs
- **Temporary blocks**: Time-based blocking with automatic expiry
- **Dynamic management**: Runtime IP list management via API

## Configuration

### Application Properties (application.yml)

```yaml
afristays:
  rate-limit:
    requests-per-minute: 60
    requests-per-hour: 1000
    requests-per-day: 10000
    burst-capacity: 10
    burst-refill-rate: 5
    ddos-threshold-per-minute: 100
    ddos-threshold-per-second: 10
    ddos-block-duration-minutes: 15
    max-consecutive-errors: 10
    max-same-endpoint-requests: 50
    suspicious-pattern-block-minutes: 5
    whitelisted-ips:
      - "127.0.0.1"
      - "::1"
    blacklisted-ips: []
    exempt-paths:
      - "/actuator/health"
      - "/api/v1/auth/health"
      - "/admin/security/health"
```

### Redis Configuration

```yaml
spring.data.redis:
  host: localhost
  port: 6379
  password: 
  timeout: 2000ms
```

## Management API

### Security Management Endpoints

#### Get Configuration
```bash
GET /admin/security/config
```

#### Check IP Status
```bash
GET /admin/security/ip/{ip}/status
```

#### Block IP Temporarily
```bash
POST /admin/security/ip/{ip}/block?durationMinutes=15&reason=Manual%20block
```

#### Permanent Blacklist
```bash
POST /admin/security/ip/{ip}/blacklist?reason=Malicious%20activity
```

#### Remove from Blacklist
```bash
DELETE /admin/security/ip/{ip}/blacklist
```

#### Add to Whitelist
```bash
POST /admin/security/ip/{ip}/whitelist
```

#### Remove from Whitelist
```bash
DELETE /admin/security/ip/{ip}/whitelist
```

#### Health Check
```bash
GET /admin/security/health
```

## Response Headers

When rate limiting is applied, the following headers are included:

- `X-RateLimit-Limited: true` - Request was rate limited
- `X-RateLimit-Blocked: true` - IP is blocked
- `X-RateLimit-Reason: <reason>` - Reason for limiting/blocking
- `Retry-After: <seconds>` - When to retry the request

## Error Responses

### Rate Limited (429)
```json
{
  "error": "Rate Limit Exceeded",
  "message": "Rate limit exceeded (per minute)",
  "timestamp": "2025-09-07T10:30:00.000",
  "status": 429
}
```

### Blocked IP (429)
```json
{
  "error": "Too Many Requests",
  "message": "IP is blacklisted",
  "timestamp": "2025-09-07T10:30:00.000",
  "status": 429
}
```

## Monitoring and Logging

### Security Events
All security events are logged to the `SECURITY_LOGGER`:

- `BLOCKED_REQUEST` - IP is blocked
- `RATE_LIMITED` - Request rate limited
- `RATE_LIMIT_RESPONSE` - 429 response sent
- `CLIENT_ERROR` - 4xx responses
- `SERVER_ERROR` - 5xx responses

### Log Format
```
SECURITY_EVENT | timestamp | client_ip | method | path | status_code | reason
```

### Key Metrics to Monitor
1. **Rate limit violations per IP**
2. **DDoS attack attempts**
3. **Blocked IP addresses**
4. **Error response patterns**
5. **Suspicious activity patterns**

## Deployment Considerations

### Redis Requirements
- Redis server for storing rate limit counters and IP lists
- Recommended: Redis Cluster for high availability
- Memory sizing: ~1MB per 10,000 unique IPs per day

### Performance Impact
- Minimal latency overhead (~1-2ms per request)
- Redis operations are non-blocking (reactive)
- Graceful degradation on Redis failures

### Scaling
- Horizontal scaling supported via shared Redis
- Rate limits are enforced across all gateway instances
- IP lists synchronized across instances

## Security Best Practices

1. **Regular Monitoring**: Review security logs daily
2. **Threshold Tuning**: Adjust limits based on traffic patterns
3. **Whitelist Management**: Keep trusted IPs updated
4. **Incident Response**: Have procedures for DDoS attacks
5. **Backup Strategy**: Regular Redis backups for IP lists

## Troubleshooting

### Common Issues

1. **Legitimate users blocked**
   - Check if IP is in blacklist
   - Review rate limit thresholds
   - Add to whitelist if needed

2. **Redis connection issues**
   - Check Redis server status
   - Verify connection configuration
   - Monitor Redis memory usage

3. **False positive DDoS detection**
   - Review threshold settings
   - Check for legitimate traffic spikes
   - Adjust detection parameters

### Debug Commands

```bash
# Check IP status
curl -X GET "http://localhost:31304/admin/security/ip/192.168.1.100/status"

# View current configuration
curl -X GET "http://localhost:31304/admin/security/config"

# Unblock an IP
curl -X DELETE "http://localhost:31304/admin/security/ip/192.168.1.100/blacklist"
```

## Future Enhancements

1. **Machine Learning**: AI-based pattern detection
2. **Geolocation**: Country-based blocking
3. **Behavioral Analysis**: User behavior profiling
4. **Integration**: SIEM system integration
5. **Dashboard**: Real-time monitoring dashboard
