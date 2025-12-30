@echo off
REM ==============================================================================
REM LOGFORWARDER - Docker Management Script (Windows)
REM ==============================================================================
REM This script provides easy commands to manage LogForwarder Docker services.
REM
REM Usage:
REM   start.bat [command] [options]
REM
REM Commands:
REM   dev         Start development environment (default)
REM   prod        Start production environment
REM   stop        Stop all running services
REM   restart     Restart all services
REM   logs        View logs (follow mode)
REM   status      Show status of all services
REM   clean       Stop services and remove volumes
REM   fresh       Remove everything and start fresh (clean + dev)
REM   build       Build all Docker images
REM   help        Show this help message
REM ==============================================================================

setlocal EnableDelayedExpansion

REM Navigate to the docker directory
cd /d "%~dp0"

REM Check if .env file exists
if not exist ".env" (
    echo [WARNING] .env file not found. Copying from .env.example...
    copy ".env.example" ".env" >nul
    echo [INFO] Please review and update .env with your configuration.
)

REM Parse command
set "COMMAND=%~1"
if "%COMMAND%"=="" set "COMMAND=dev"

REM Execute command
if /i "%COMMAND%"=="dev" goto :dev
if /i "%COMMAND%"=="prod" goto :prod
if /i "%COMMAND%"=="stop" goto :stop
if /i "%COMMAND%"=="restart" goto :restart
if /i "%COMMAND%"=="logs" goto :logs
if /i "%COMMAND%"=="status" goto :status
if /i "%COMMAND%"=="clean" goto :clean
if /i "%COMMAND%"=="fresh" goto :fresh
if /i "%COMMAND%"=="build" goto :build
if /i "%COMMAND%"=="help" goto :help
if /i "%COMMAND%"=="-h" goto :help
if /i "%COMMAND%"=="--help" goto :help

echo [ERROR] Unknown command: %COMMAND%
goto :help

:dev
echo ============================================================
echo Starting LogForwarder Development Environment
echo ============================================================
echo.
echo Services starting:
echo   - ClickHouse (http://localhost:8123)
echo   - Elasticsearch (http://localhost:9200)
echo   - Redis (localhost:6379)
echo   - PostgreSQL (localhost:5432)
echo   - Kafka (localhost:9092)
echo   - Tabix UI (http://localhost:8124)
echo   - Kafka UI (http://localhost:8085)
echo   - Redis Commander (http://localhost:8086)
echo   - Middleware API (http://localhost:8080)
echo   - LogForwarder Agent (http://localhost:9090)
echo   - UI (http://localhost:3000)
echo.
docker-compose -f docker-compose.yml up -d %2
echo.
echo [INFO] Services started. Run 'start.bat logs' to view logs.
goto :eof

:prod
echo ============================================================
echo Starting LogForwarder Production Environment
echo ============================================================
echo.
echo [WARNING] Make sure you have configured .env with production values!
echo.
set /p CONFIRM="Continue? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo Aborted.
    goto :eof
)
docker-compose -f docker-compose.prod.yml up -d %2
echo.
echo [INFO] Production services started.
goto :eof

:stop
echo Stopping all LogForwarder services...
docker-compose -f docker-compose.yml down 2>nul
docker-compose -f docker-compose.prod.yml down 2>nul
echo [INFO] All services stopped.
goto :eof

:restart
echo Restarting LogForwarder services...
docker-compose -f docker-compose.yml restart %2
echo [INFO] Services restarted.
goto :eof

:logs
echo Following logs (Ctrl+C to exit)...
docker-compose -f docker-compose.yml logs -f %2
goto :eof

:status
echo ============================================================
echo LogForwarder Service Status
echo ============================================================
docker-compose -f docker-compose.yml ps
goto :eof

:clean
echo ============================================================
echo Cleaning Up LogForwarder Docker Resources
echo ============================================================
echo.
echo [WARNING] This will:
echo   - Stop all running services
echo   - Remove all containers
echo   - Remove all volumes (DATA WILL BE LOST!)
echo.
set /p CONFIRM="Are you sure? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo Aborted.
    goto :eof
)
docker-compose -f docker-compose.yml down -v 2>nul
docker-compose -f docker-compose.prod.yml down -v 2>nul
echo [INFO] All services and volumes removed.
goto :eof

:fresh
echo ============================================================
echo Fresh Start - Removing Everything and Starting Clean
echo ============================================================
echo.
echo [WARNING] This will:
echo   - Stop all running services
echo   - Remove all containers
echo   - Remove all volumes (DATA WILL BE LOST!)
echo   - Remove all images built by this project
echo   - Start fresh development environment
echo.
set /p CONFIRM="Are you sure you want to start fresh? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo Aborted.
    goto :eof
)
echo.
echo [INFO] Stopping all services...
docker-compose -f docker-compose.yml down -v --remove-orphans 2>nul
docker-compose -f docker-compose.prod.yml down -v --remove-orphans 2>nul

echo [INFO] Removing project images...
for /f "tokens=*" %%i in ('docker images --filter "reference=*logforwarder*" -q 2^>nul') do docker rmi -f %%i 2>nul

echo [INFO] Pruning unused Docker resources...
docker network prune -f 2>nul

echo.
echo [INFO] Starting fresh development environment...
echo.
goto :dev_after_fresh

:dev_after_fresh
echo ============================================================
echo Starting LogForwarder Development Environment
echo ============================================================
echo.
echo Services starting:
echo   - ClickHouse (http://localhost:8123)
echo   - Elasticsearch (http://localhost:9200)
echo   - Redis (localhost:6379)
echo   - PostgreSQL (localhost:5432)
echo   - Kafka (localhost:9092)
echo   - Tabix UI (http://localhost:8124)
echo   - Kafka UI (http://localhost:8085)
echo   - Redis Commander (http://localhost:8086)
echo   - Middleware API (http://localhost:8080)
echo   - LogForwarder Agent (http://localhost:9090)
echo   - UI (http://localhost:3000)
echo.
docker-compose -f docker-compose.yml up -d
echo.
echo [INFO] Fresh deployment complete!
goto :eof

:build
echo Building all Docker images...
docker-compose -f docker-compose.yml build %2
echo [INFO] Build complete.
goto :eof

:help
echo.
echo ==============================================================
echo LogForwarder Docker Management Script
echo ==============================================================
echo.
echo Usage: start.bat [command] [service]
echo.
echo Commands:
echo   dev       Start development environment (default)
echo   prod      Start production environment
echo   stop      Stop all running services
echo   restart   Restart services (optionally specify service name)
echo   logs      View logs in follow mode (optionally specify service)
echo   status    Show status of all services
echo   clean     Stop services and remove all volumes (DATA LOSS!)
echo   fresh     Remove everything and start fresh deployment
echo   build     Build all Docker images
echo   help      Show this help message
echo.
echo Examples:
echo   start.bat                    Start dev environment
echo   start.bat dev middleware     Start only middleware service
echo   start.bat logs clickhouse    Follow ClickHouse logs only
echo   start.bat restart ui         Restart only the UI service
echo.
echo Service names:
echo   clickhouse, elasticsearch, redis, postgres, zookeeper,
echo   kafka, kafka-ui, tabix, middleware, logforwarder, ui
echo.
goto :eof
