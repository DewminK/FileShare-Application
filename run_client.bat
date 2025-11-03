@echo off
REM Batch file to compile and run the File Sharing Client

echo ========================================
echo File Sharing Client - Startup Script
echo ========================================
echo.

REM Navigate to the project directory
cd /d "%~dp0"

REM Set JavaFX path (adjust if needed)
set JAVAFX_PATH=javafx-sdk-21.0.1\lib
set JAVAFX_MODULES=javafx.controls,javafx.fxml

REM Create necessary directories
if not exist "downloads" mkdir downloads
if not exist "bin" mkdir bin

echo [1/3] Compiling client code...
javac --module-path "%JAVAFX_PATH%" --add-modules %JAVAFX_MODULES% -d bin -sourcepath src src\client\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Please check if JavaFX is properly configured.
    echo.
    echo If you see JavaFX errors, you need to add JavaFX to your classpath.
    echo For JDK 11+, download JavaFX SDK from: https://openjfx.io/
    echo.
    pause
    exit /b 1
)

echo [2/3] Compilation successful!
echo.
echo [3/3] Starting client GUI...
echo.

REM Run with JavaFX modules
java --module-path "%JAVAFX_PATH%" --add-modules %JAVAFX_MODULES% -cp bin client.ClientUI

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo ERROR: Failed to start client!
    echo ========================================
    echo.
    pause
)
