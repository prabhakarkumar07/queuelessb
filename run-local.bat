@echo off
REM Run QueueLess backend locally with Supabase + Upstash
REM Copy .env.example to .env and fill in your values, then use this script.

set DB_HOST=%DB_HOST%
set DB_PORT=%DB_PORT%
set DB_NAME=%DB_NAME%
set DB_USER=%DB_USER%
set DB_PASSWORD=%DB_PASSWORD%
set REDIS_HOST=%REDIS_HOST%
set REDIS_PORT=%REDIS_PORT%
set REDIS_PASSWORD=%REDIS_PASSWORD%
set REDIS_SSL=%REDIS_SSL%
set JWT_SECRET=%JWT_SECRET%
set ALLOWED_ORIGINS=%ALLOWED_ORIGINS%
set GOOGLE_CLIENT_ID=%GOOGLE_CLIENT_ID%
set GOOGLE_CLIENT_SECRET=%GOOGLE_CLIENT_SECRET%

echo Starting QueueLess Backend...
echo DB: %DB_HOST%/%DB_NAME%
echo Redis: %REDIS_HOST%:%REDIS_PORT% (SSL)
echo.

call mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.data.redis.ssl.enabled=true"
