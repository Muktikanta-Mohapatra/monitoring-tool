#!/bin/bash
# ==============================================================================
# LOGFORWARDER - Docker Management Script (Unix/Linux/macOS)
# ==============================================================================
# This script provides easy commands to manage LogForwarder Docker services.
#
# Usage:
#   ./start.sh [command] [options]
#
# Commands:
#   dev         Start development environment (default)
#   prod        Start production environment
#   stop        Stop all running services
#   restart     Restart all services
#   logs        View logs (follow mode)
#   status      Show status of all services
#   clean       Stop services and remove volumes
#   fresh       Remove everything and start fresh (clean + dev)
#   build       Build all Docker images
#   help        Show this help message
# ==============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Navigate to the docker directory
cd "$(dirname "$0")"

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}[WARNING]${NC} .env file not found. Copying from .env.example..."
    cp .env.example .env
    echo -e "${BLUE}[INFO]${NC} Please review and update .env with your configuration."
fi

# Parse command
COMMAND="${1:-dev}"
SERVICE="${2:-}"

# Helper functions
print_header() {
    echo -e "${BLUE}============================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}============================================================${NC}"
}

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Command implementations
cmd_dev() {
    print_header "Starting LogForwarder Development Environment"
    echo ""
    echo "Services starting:"
    echo "  - ClickHouse (http://localhost:8123)"
    echo "  - Elasticsearch (http://localhost:9200)"
    echo "  - Redis (localhost:6379)"
    echo "  - PostgreSQL (localhost:5432)"
    echo "  - Kafka (localhost:9092)"
    echo "  - Tabix UI (http://localhost:8124)"
    echo "  - Kafka UI (http://localhost:8085)"
    echo "  - Redis Commander (http://localhost:8086)"
    echo "  - Middleware API (http://localhost:8080)"
    echo "  - LogForwarder Agent (http://localhost:9090)"
    echo "  - UI (http://localhost:3000)"
    echo ""
    docker-compose -f docker-compose.yml up -d $SERVICE
    echo ""
    print_info "Services started. Run './start.sh logs' to view logs."
}

cmd_prod() {
    print_header "Starting LogForwarder Production Environment"
    echo ""
    print_warning "Make sure you have configured .env with production values!"
    echo ""
    read -p "Continue? (y/N): " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    docker-compose -f docker-compose.prod.yml up -d $SERVICE
    echo ""
    print_info "Production services started."
}

cmd_stop() {
    print_info "Stopping all LogForwarder services..."
    docker-compose -f docker-compose.yml down 2>/dev/null || true
    docker-compose -f docker-compose.prod.yml down 2>/dev/null || true
    print_info "All services stopped."
}

cmd_restart() {
    print_info "Restarting LogForwarder services..."
    docker-compose -f docker-compose.yml restart $SERVICE
    print_info "Services restarted."
}

cmd_logs() {
    print_info "Following logs (Ctrl+C to exit)..."
    docker-compose -f docker-compose.yml logs -f $SERVICE
}

cmd_status() {
    print_header "LogForwarder Service Status"
    docker-compose -f docker-compose.yml ps
}

cmd_clean() {
    print_header "Cleaning Up LogForwarder Docker Resources"
    echo ""
    print_warning "This will:"
    echo "  - Stop all running services"
    echo "  - Remove all containers"
    echo "  - Remove all volumes (DATA WILL BE LOST!)"
    echo ""
    read -p "Are you sure? (y/N): " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    docker-compose -f docker-compose.yml down -v 2>/dev/null || true
    docker-compose -f docker-compose.prod.yml down -v 2>/dev/null || true
    print_info "All services and volumes removed."
}

cmd_fresh() {
    print_header "Fresh Start - Removing Everything and Starting Clean"
    echo ""
    print_warning "This will:"
    echo "  - Stop all running services"
    echo "  - Remove all containers"
    echo "  - Remove all volumes (DATA WILL BE LOST!)"
    echo "  - Remove all images built by this project"
    echo "  - Start fresh development environment"
    echo ""
    read -p "Are you sure you want to start fresh? (y/N): " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    echo ""
    print_info "Stopping all services..."
    docker-compose -f docker-compose.yml down -v --remove-orphans 2>/dev/null || true
    docker-compose -f docker-compose.prod.yml down -v --remove-orphans 2>/dev/null || true
    
    print_info "Removing project images..."
    docker images --filter "reference=*logforwarder*" -q | xargs -r docker rmi -f 2>/dev/null || true
    
    print_info "Pruning unused Docker resources..."
    docker network prune -f 2>/dev/null || true
    
    echo ""
    print_info "Starting fresh development environment..."
    echo ""
    cmd_dev
    echo ""
    print_info "Fresh deployment complete!"
}

cmd_build() {
    print_info "Building all Docker images..."
    docker-compose -f docker-compose.yml build $SERVICE
    print_info "Build complete."
}

cmd_help() {
    echo ""
    echo "============================================================"
    echo "LogForwarder Docker Management Script"
    echo "============================================================"
    echo ""
    echo "Usage: ./start.sh [command] [service]"
    echo ""
    echo "Commands:"
    echo "  dev       Start development environment (default)"
    echo "  prod      Start production environment"
    echo "  stop      Stop all running services"
    echo "  restart   Restart services (optionally specify service name)"
    echo "  logs      View logs in follow mode (optionally specify service)"
    echo "  status    Show status of all services"
    echo "  clean     Stop services and remove all volumes (DATA LOSS!)"
    echo "  fresh     Remove everything and start fresh deployment"
    echo "  build     Build all Docker images"
    echo "  help      Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./start.sh                    Start dev environment"
    echo "  ./start.sh dev middleware     Start only middleware service"
    echo "  ./start.sh logs clickhouse    Follow ClickHouse logs only"
    echo "  ./start.sh restart ui         Restart only the UI service"
    echo ""
    echo "Service names:"
    echo "  clickhouse, elasticsearch, redis, postgres, zookeeper,"
    echo "  kafka, kafka-ui, tabix, middleware, logforwarder, ui"
    echo ""
}

# Execute command
case "$COMMAND" in
    dev)
        cmd_dev
        ;;
    prod)
        cmd_prod
        ;;
    stop)
        cmd_stop
        ;;
    restart)
        cmd_restart
        ;;
    logs)
        cmd_logs
        ;;
    status)
        cmd_status
        ;;
    clean)
        cmd_clean
        ;;
    fresh)
        cmd_fresh
        ;;
    build)
        cmd_build
        ;;
    help|-h|--help)
        cmd_help
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        cmd_help
        exit 1
        ;;
esac
