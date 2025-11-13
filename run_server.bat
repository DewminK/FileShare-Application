@echo off
cd /d "%~dp0"

set JAVAFX_PATH=javafx-sdk-21.0.1\lib
set JAVAFX_MODULES=javafx.controls,javafx.fxml

if not exist "shared_files" mkdir shared_files
if not exist "bin" mkdir bin

echo [1/3] Compiling server code...
javac --module-path "%JAVAFX_PATH%" --add-modules %JAVAFX_MODULES% -d bin -sourcepath src src\server\*.java src\shared\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo [2/3] Compilation successful!
echo.
echo [3/3] Starting server GUI...
echo.

java --module-path "%JAVAFX_PATH%" --add-modules %JAVAFX_MODULES% -cp bin server.ServerUI

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start server!
    pause
)

