#!/bin/bash

# DDoS Protection Test Script
# This script tests the rate limiting and DDoS protection features

GATEWAY_URL="https://localhost:31304"
TEST_ENDPOINT="/api/v1/auth/health"
ADMIN_ENDPOINT="/admin/security"

echo "=== DDoS Protection Test Script ==="
echo "Gateway URL: $GATEWAY_URL"
echo "Test Endpoint: $TEST_ENDPOINT"
echo ""

# Function to make a request and show response
make_request() {
    local url=$1
    local method=${2:-GET}
    echo "Making $method request to: $url"
    
    response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nRESPONSE_TIME:%{time_total}" \
                   -X "$method" \
                   -k \
                   "$url" 2>/dev/null)
    
    echo "Response: $response"
    echo "---"
}

# Function to test rate limiting
test_rate_limiting() {
    echo "=== Testing Rate Limiting ==="
    echo "Sending multiple requests rapidly..."
    
    for i in {1..15}; do
        echo "Request $i:"
        make_request "$GATEWAY_URL$TEST_ENDPOINT"
        sleep 0.1
    done
}

# Function to test admin endpoints
test_admin_endpoints() {
    echo "=== Testing Admin Endpoints ==="
    
    # Get configuration
    echo "Getting current configuration:"
    make_request "$GATEWAY_URL$ADMIN_ENDPOINT/config"
    
    # Check IP status
    echo "Checking IP status for 127.0.0.1:"
    make_request "$GATEWAY_URL$ADMIN_ENDPOINT/ip/127.0.0.1/status"
    
    # Test blocking an IP
    echo "Blocking test IP 192.168.1.100:"
    make_request "$GATEWAY_URL$ADMIN_ENDPOINT/ip/192.168.1.100/block?durationMinutes=1&reason=Test" "POST"
    
    # Check blocked IP status
    echo "Checking blocked IP status:"
    make_request "$GATEWAY_URL$ADMIN_ENDPOINT/ip/192.168.1.100/status"
    
    # Unblock the IP
    echo "Unblocking test IP:"
    make_request "$GATEWAY_URL$ADMIN_ENDPOINT/ip/192.168.1.100/blacklist" "DELETE"
}

# Function to test DDoS simulation
test_ddos_simulation() {
    echo "=== Testing DDoS Simulation ==="
    echo "Sending rapid burst of requests to trigger DDoS protection..."
    
    for i in {1..20}; do
        curl -s -k "$GATEWAY_URL$TEST_ENDPOINT" &
    done
    
    wait
    echo "Burst requests completed. Checking if IP is blocked..."
    
    # Check if we're now blocked
    make_request "$GATEWAY_URL$TEST_ENDPOINT"
}

# Main test execution
echo "Starting DDoS protection tests..."
echo "Press Enter to continue or Ctrl+C to exit"
read

# Test 1: Basic rate limiting
test_rate_limiting

echo ""
echo "Press Enter to continue with admin endpoint tests..."
read

# Test 2: Admin endpoints
test_admin_endpoints

echo ""
echo "Press Enter to continue with DDoS simulation..."
read

# Test 3: DDoS simulation
test_ddos_simulation

echo ""
echo "=== Test Complete ==="
echo "Check the gateway logs for detailed information about rate limiting and blocking events."
echo "Log file: logs/afristays-gateway.log"
