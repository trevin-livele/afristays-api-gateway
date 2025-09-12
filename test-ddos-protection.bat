@echo off
REM DDoS Protection Test Script for Windows
REM This script tests the rate limiting and DDoS protection features

set GATEWAY_URL=https://localhost:31304
set TEST_ENDPOINT=/api/v1/auth/health
set ADMIN_ENDPOINT=/admin/security

echo === DDoS Protection Test Script ===
echo Gateway URL: %GATEWAY_URL%
echo Test Endpoint: %TEST_ENDPOINT%
echo.

echo === Testing Rate Limiting ===
echo Sending multiple requests rapidly...

for /L %%i in (1,1,15) do (
    echo Request %%i:
    curl -s -k "%GATEWAY_URL%%TEST_ENDPOINT%"
    timeout /t 1 /nobreak >nul
)

echo.
echo Press any key to continue with admin endpoint tests...
pause >nul

echo === Testing Admin Endpoints ===

echo Getting current configuration:
curl -s -k "%GATEWAY_URL%%ADMIN_ENDPOINT%/config"
echo.

echo Checking IP status for 127.0.0.1:
curl -s -k "%GATEWAY_URL%%ADMIN_ENDPOINT%/ip/127.0.0.1/status"
echo.

echo Blocking test IP 192.168.1.100:
curl -s -k -X POST "%GATEWAY_URL%%ADMIN_ENDPOINT%/ip/192.168.1.100/block?durationMinutes=1&reason=Test"
echo.

echo Checking blocked IP status:
curl -s -k "%GATEWAY_URL%%ADMIN_ENDPOINT%/ip/192.168.1.100/status"
echo.

echo Unblocking test IP:
curl -s -k -X DELETE "%GATEWAY_URL%%ADMIN_ENDPOINT%/ip/192.168.1.100/blacklist"
echo.

echo.
echo Press any key to continue with DDoS simulation...
pause >nul

echo === Testing DDoS Simulation ===
echo Sending rapid burst of requests to trigger DDoS protection...

for /L %%i in (1,1,20) do (
    start /B curl -s -k "%GATEWAY_URL%%TEST_ENDPOINT%"
)

timeout /t 2 /nobreak >nul
echo Burst requests completed. Checking if IP is blocked...

curl -s -k "%GATEWAY_URL%%TEST_ENDPOINT%"

echo.
echo === Test Complete ===
echo Check the gateway logs for detailed information about rate limiting and blocking events.
echo Log file: logs/afristays-gateway.log
pause
