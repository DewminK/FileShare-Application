@echo off
echo ========================================
echo File Sharing Server - Startup Script
echo ========================================
echo.
echo [INFO] Starting server...
echo.

REM Run the server using exec plugin with server execution ID
mvn exec:java@server

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Server failed to start!
    pause
) else (
    echo.
    echo [INFO] Server stopped.
)