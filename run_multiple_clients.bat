@echo off
REM Batch file to run multiple client instances simultaneously

echo ========================================
echo File Sharing Application - Multiple Clients
echo ========================================
echo.
echo This will start multiple client instances
echo in separate windows.
echo.

REM Navigate to the project directory
cd /d "%~dp0"

REM Ask how many clients to start
set /p NUM_CLIENTS="How many clients do you want to start? (1-5): "

if "%NUM_CLIENTS%"=="" set NUM_CLIENTS=2

echo.
echo Starting %NUM_CLIENTS% client instances...
echo.

REM Start the specified number of clients
for /L %%i in (1,1,%NUM_CLIENTS%) do (
    echo Starting Client %%i...
    start "File Sharing Client %%i" cmd /k run_client.bat
    timeout /t 1 /nobreak >nul
)

echo.
echo ========================================
echo %NUM_CLIENTS% Client instances started!
echo ========================================
echo.
echo Each client is running in a separate window.
echo You can connect them all to the same server
echo to test simultaneous file operations.
echo.
pause

