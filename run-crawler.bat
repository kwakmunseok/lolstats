@echo off
setlocal

cd /d "%~dp0"

echo [1/3] Starting Docker (MySQL/Redis)...
docker compose up -d
if errorlevel 1 (
    echo.
    echo Docker command failed - is Docker Desktop running?
    pause
    exit /b 1
)

if not exist ".env" (
    echo.
    echo .env not found: %cd%\.env
    pause
    exit /b 1
)

echo [2/3] Loading .env...
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
    set "%%A=%%B"
)

if "%RIOT_API_KEY%"=="" (
    echo.
    echo RIOT_API_KEY not found in .env
    pause
    exit /b 1
)

echo [3/3] Starting crawler (dev,crawler profile)
echo.
echo ============================================================
echo  Ctrl+C in this window stops it - that IS the pause switch.
echo  Do NOT run this at the same time as the normal dev server -
echo  each JVM gets its own rate-limit bucket and they'd double up.
echo ============================================================
echo.

rem --no-daemon: a reused Gradle daemon can be holding an old
rem environment snapshot from before RIOT_API_KEY was ever set,
rem so it silently ignores the value just loaded above. Without
rem the daemon, bootRun forks directly from this batch process's
rem own environment instead, so it always sees the current value.
call "%~dp0gradlew.bat" --no-daemon bootRun --args="--spring.profiles.active=dev,crawler"

echo.
echo Crawler exited.
pause
endlocal
