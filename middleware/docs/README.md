# LogForwarder Middleware API

## Project Overview

A high-performance Spring Boot 3.3 middleware API that serves as the bridge between the Rust-based LogForwarder system and the React-based UI. This middleware handles:

- **Event Ingestion**: Receiving and storing high-volume log batches from LogForwarder
- **Search & Analytics**: Full-text search with Elasticsearch integration  
- **Real-Time Updates**: WebSocket-based live streaming to UI clients
- **Forwarder Management**: Monitoring forwarder status, metrics, and health
- **User Authentication**: JWT-based authentication with role-based access control
- **Alert Management**: Rule evaluation and alert triggering
- **Audit Logging**: Complete audit trail of all system actions

## Technology Stack

- **Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.0
- **Build Tool**: Maven
- **Web**: Spring Web (Spring MVC)
- **Databases**:
  - ClickHouse (primary event storage - high-performance columnar database)
  - MongoDB (optional document storage)
  - Elasticsearch (full-text search)
  - Redis (caching & sessions)
- **ORM**: Hibernate (via Spring Data JPA)
- **Authentication**: Spring Security + JJWT (JWT tokens)
- **Real-Time**: Spring WebSocket + Netty
- **Validation**: Jakarta Bean Validation + Hibernate Validator
- **Monitoring**: Spring Boot Actuator + Micrometer + Prometheus
- **Logging**: SLF4J + Logback
- **Code Generation**: Lombok + MapStruct
- **Testing**: JUnit 5 + Spring Boot Test + TestContainers

## Project Structure

```
middleware/
├── src/main/java/com/monitoring/logforwarder/
│   ├── LogForwarderApplication.java           # Main entry point
│   ├── config/                                 # Configuration classes
│   │   ├── DatabaseConfig.java                # Database & HikariCP
│   │   ├── ElasticsearchConfig.java           # Elasticsearch setup
│   │   ├── RedisConfig.java                   # Redis caching
│   │   ├── WebSocketConfig.java               # WebSocket server
│   │   ├── SecurityConfig.java                # Spring Security
│   │   ├── CorsConfig.java                    # CORS configuration
│   │   └── ApplicationProperties.java         # Custom app properties
│   ├── entity/                                 # JPA Entities
│   │   ├── Event.java
│   │   ├── Forwarder.java
│   │   ├── User.java
│   │   ├── Alert.java
│   │   ├── Checkpoint.java
│   │   └── AuditLog.java
│   ├── repository/                             # Spring Data JPA Repositories
│   │   ├── EventRepository.java
│   │   ├── ForwarderRepository.java
│   │   ├── UserRepository.java
│   │   ├── AlertRepository.java
│   │   ├── CheckpointRepository.java
│   │   └── AuditLogRepository.java
│   ├── dto/                                    # Data Transfer Objects
│   │   ├── EventDTO.java
│   │   ├── EventBatchDTO.java
│   │   ├── ForwarderDTO.java
│   │   ├── SearchQueryDTO.java
│   │   ├── AlertDTO.java
│   │   ├── UserDTO.java
│   │   ├── DashboardDTO.java
│   │   └── ApiResponseDTO.java
│   ├── controller/                             # REST Controllers
│   │   ├── EventController.java               # /api/v1/events
│   │   ├── ForwarderController.java           # /api/v1/forwarders
│   │   ├── DashboardController.java           # /api/v1/dashboard
│   │   ├── AlertController.java               # /api/v1/alerts
│   │   ├── ConfigurationController.java       # /api/v1/config
│   │   ├── AuthController.java                # /api/v1/auth
│   │   └── UserController.java                # /api/v1/users
│   ├── service/                                # Business Logic
│   │   ├── EventService.java
│   │   ├── ForwarderService.java
│   │   ├── SearchService.java
│   │   ├── AlertService.java
│   │   ├── AuthService.java
│   │   ├── MetricsService.java
│   │   ├── NotificationService.java
│   │   └── AuditService.java
│   ├── security/                               # Security Components
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── CustomUserDetailsService.java
│   │   └── SecurityConstants.java
│   ├── websocket/                              # WebSocket Components
│   │   ├── WebSocketConfig.java
│   │   ├── WebSocketHandler.java
│   │   ├── WebSocketEventListener.java
│   │   ├── EventSubscriptionManager.java
│   │   ├── RealTimeEventBroadcaster.java
│   │   └── WebSocketMessage.java
│   ├── exception/                              # Exception Handling
│   │   ├── ApiException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── ValidationException.java
│   │   ├── AuthenticationException.java
│   │   └── GlobalExceptionHandler.java
│   ├── util/                                   # Utilities
│   │   ├── QueryParser.java
│   │   ├── ElasticsearchQueryBuilder.java
│   │   ├── ValidationUtil.java
│   │   ├── DateFormatUtil.java
│   │   ├── LoggerUtil.java
│   │   └── Constants.java
│   └── mapper/                                 # MapStruct Mappers
│       ├── EventMapper.java
│       ├── ForwarderMapper.java
│       ├── UserMapper.java
│       └── AlertMapper.java
├── src/main/resources/
│   ├── application.yml                        # Main config
│   ├── application-dev.yml                    # Dev profile
│   ├── application-prod.yml                   # Prod profile
│   ├── db/migration/                          # Flyway migrations
│   │   └── V001__Initial_schema.sql
│   └── elasticsearch/
│       └── index-template.json
├── src/test/java/...                          # Tests
├── docker-compose.yml                         # Local dev environment
├── Dockerfile                                 # Container build
├── .env.example                               # Environment variables template
├── pom.xml                                    # Maven dependencies
└── README.md                                  # This file
```

## API Endpoints

### Events API (`/api/v1/events`)
- `POST /events/batch` - Accept batches from LogForwarder
- `GET /logs/search` - Full-text search with filters
- `GET /logs/:logId` - Get detailed log entry
- `GET /events/recent` - Get recent events

### Forwarders API (`/api/v1/forwarders`)
- `GET /forwarders` - List all forwarders
- `GET /forwarders/:id` - Get forwarder details
- `GET /forwarders/:id/metrics` - Historical metrics
- `POST /forwarders/:id/configuration` - Update config
- `POST /forwarders/:id/heartbeat` - Heartbeat from LogForwarder

### Dashboard API (`/api/v1/dashboard`)
- `GET /dashboard/summary` - Overview statistics
- `GET /dashboard/logs/recent` - Recent log entries
- `GET /dashboard/charts/:type` - Chart data (cpu, memory, events, etc.)

### Alert API (`/api/v1/alerts`)
- `GET /alerts` - List alerts with filtering
- `POST /alerts/acknowledge` - Acknowledge alert
- `GET /alerts/rules` - List alert rules
- `POST /alerts/rules` - Create alert rule
- `PUT /alerts/rules/:id` - Update alert rule

### Configuration API (`/api/v1/config`)
- `GET /config/inputs` - List input configurations
- `POST /config/inputs` - Create input config
- `PUT /config/inputs/:id` - Update input config
- `DELETE /config/inputs/:id` - Delete input config

### Authentication API (`/api/v1/auth`)
- `POST /auth/login` - Login with credentials
- `POST /auth/refresh` - Refresh JWT token
- `POST /auth/logout` - Logout
- `GET /auth/me` - Get current user info

### Users API (`/api/v1/users`)
- `GET /users` - List users (admin only)
- `POST /users` - Create user (admin only)
- `GET /users/:id` - Get user details
- `PUT /users/:id` - Update user
- `DELETE /users/:id` - Delete user

### System API (`/api/v1/system`)
- `GET /health` - Health check
- `GET /metrics` - Application metrics
- `GET /audit-logs` - Audit log entries

## Security Features

### Authentication
- JWT token-based authentication
- Access tokens (24 hours default) + Refresh tokens (7 days default)
- Token stored in HTTP-only cookies

### Authorization (RBAC)
- **ADMIN**: Full system access
- **OPERATOR**: View logs, manage forwarders, acknowledge alerts
- **VIEWER**: Read-only access to dashboard and logs
- **API_CLIENT**: Programmatic access for LogForwarder

### Additional Security
- CORS configuration for React UI
- Request validation with custom validators
- Audit logging for all write operations
- Password hashing with BCrypt
- Rate limiting (1000 requests/minute default)
- SQL injection protection via parameterized queries

## Real-Time Features (WebSocket)

### Subscription Channels
1. **live-events** - New events as they arrive
2. **metrics** - Forwarder metrics updates
3. **alerts** - Active alert notifications
4. **forwarder-status** - Forwarder status changes

### WebSocket Message Format
```json
{
  "type": "event|metric|alert|status",
  "action": "create|update|delete",
  "timestamp": "2024-12-13T12:45:32Z",
  "data": { /* payload */ }
}
```

## Search Features

### Lucene Query Syntax Support
- Full-text search: `connection timeout`
- Phrase search: `"exact phrase"`
- Wildcards: `conn*`, `?onnection`
- Boolean operators: `error AND database`
- Field search: `severity:ERROR`, `sourcetype:json`
- Range queries: `timestamp:[2024-01-01 TO 2024-01-31]`

### Filters
- By severity (DEBUG, INFO, WARNING, ERROR, CRITICAL)
- By source ID or name
- By sourcetype
- By host
- By time range
- By forwarder ID

## Development Setup

### Prerequisites
- Java 21+
- Maven 3.8+
- ClickHouse 24.x+
- Elasticsearch 8.x
- Redis 7.x
- Docker & Docker Compose (optional)

### Local Development

1. **Clone and setup**:
```bash
git clone <repo>
cd middleware
```

2. **Configure environment**:
```bash
cp .env.example .env
# Edit .env with your local database credentials
```

3. **Start dependencies (using Docker Compose)**:
```bash
docker-compose up -d
```

4. **Build project**:
```bash
mvn clean build
```

5. **Run application**:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Application runs on `http://localhost:8080`

### Building for Production

```bash
mvn clean package -DskipTests -Pprod
docker build -t logforwarder-middleware:1.0.0 .
```

## Database Schema

Key tables (stored in ClickHouse):
- **events**: Log events (primary storage with monthly partitioning)
- **forwarders**: Forwarder instances and metadata
- **users**: User accounts and credentials
- **alerts**: Alert instances
- **checkpoints**: File reading progress tracking
- **audit_logs**: System action audit trail

### ClickHouse Tables
Events table uses ClickHouse MergeTree engine with monthly partitioning and TTL for optimal performance:
```sql
CREATE TABLE logforwarder.events (
    id UInt64,
    timestamp DateTime64(3),
    source_id UInt32,
    -- ... other columns
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, source_id, id)
TTL toDateTime(timestamp) + INTERVAL 365 DAY;
```

## Monitoring & Observability

### Health Checks
- `GET /actuator/health` - Overall application health
- `GET /actuator/health/db` - Database connectivity
- `GET /actuator/health/elasticsearch` - Elasticsearch status
- `GET /actuator/health/redis` - Redis connectivity

### Metrics
- `GET /actuator/metrics` - Available metrics
- `GET /actuator/prometheus` - Prometheus format metrics

### Logging
- Structured logging with SLF4J
- Log levels configurable per environment
- Asynchronous logging for performance
- Rotation: 10MB per file, 30 days retention

## Performance Tuning

### Database
- Connection pooling: HikariCP (20 connections max)
- Prepared statement caching (250 statements)
- Batch operations (100 inserts per batch)
- Query result caching via Redis

### Elasticsearch
- 5 shards per index
- 1 replica for HA
- Refresh interval: 1 second
- Index compression enabled

### Caching
- Local cache: Caffeine (for reference data)
- Distributed cache: Redis (for sessions & search results)
- Cache TTL: 1 hour (configurable)

## Deployment

### Docker Deployment
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### Kubernetes Deployment
See `k8s/` directory for Helm charts and manifests.

### Environment Variables (Production)
```
DB_HOST=clickhouse
DB_PORT=8123
DB_NAME=logforwarder
DB_USER=logforwarder
DB_PASSWORD=***

REDIS_HOST=redis
REDIS_PORT=6379

ES_HOSTS=http://elasticsearch:9200

JWT_SECRET=*** (generate with: openssl rand -hex 32)
JWT_EXPIRATION=86400000

SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

## Testing

### Run all tests
```bash
mvn test
```

### Run specific test class
```bash
mvn test -Dtest=EventServiceTest
```

### Test coverage
```bash
mvn jacoco:report
# Coverage report: target/site/jacoco/index.html
```

## Contributing

1. Follow existing code conventions
2. Write tests for new features
3. Maintain minimum 80% code coverage
4. Document complex business logic
5. Use meaningful commit messages

## Troubleshooting

### Common Issues

**Database connection failed**
- Check ClickHouse is running: `docker-compose ps clickhouse`
- Verify credentials in application-dev.yml
- Check database exists: `clickhouse-client --query "SHOW DATABASES"`

**Elasticsearch connection timeout**
- Verify Elasticsearch is running: `curl http://localhost:9200`
- Check index creation: `GET /_cat/indices`

**Redis connection refused**
- Start Redis: `docker-compose up -d redis`
- Verify connection: `redis-cli ping`

**JWT token invalid**
- Regenerate secret with: `openssl rand -hex 32`
- Clear cookies in browser and login again

## License

Copyright © 2025 Monitoring Solutions
