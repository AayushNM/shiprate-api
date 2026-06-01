# ShipRate API

A Spring Boot REST API simulating enterprise shipping rating logic — base rates, fuel surcharges, and remote-area fees — built with a layered Controller–Service–Repository architecture. Designed to demonstrate production-grade Java/Spring Boot patterns including JWT authentication, Caffeine in-process caching, Redis distributed caching, and containerized deployment.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Database | PostgreSQL 16 (Spring Data JPA / Hibernate) |
| Distributed Cache | Redis 7 (surcharge rate lookups) |
| In-Process Cache | Caffeine (JWT claims validation) |
| Build | Maven |
| Containerization | Docker + Docker Compose |
| CI | GitHub Actions |
| Monitoring | Spring Boot Actuator |
| Testing | JUnit 5 + Mockito |
| Utilities | Lombok, Spring Boot Actuator |

---

## Architecture

```
src/main/java/com/aayushnair/shiprate_api/
├── controller/
│   ├── AuthController.java          # POST /auth/login → JWT token
│   └── SurchargeController.java     # /api/** rating endpoints
├── service/
│   └── SurchargeService.java        # Business logic: base rate, fuel, remote-area fee
├── repository/
│   └── ShipmentRepository.java      # JPA repository — persists shipment records
├── entity/
│   └── Shipment.java                # PostgreSQL-mapped shipment record
├── dto/
│   ├── AuthRequest.java             # Login payload (username, password)
│   ├── AuthResponse.java            # Login response (JWT token)
│   ├── SurchargeRequest.java        # origin, destination, weight, serviceType
│   └── SurchargeResponse.java       # baseRate, fuelSurcharge, remoteAreaFee, finalCharge
├── security/
│   ├── JwtUtil.java                 # Token generation + Caffeine-cached validation
│   ├── JwtAuthFilter.java           # OncePerRequestFilter — validates Bearer token
│   └── SecurityConfig.java         # SecurityFilterChain — stateless JWT setup
└── config/
    └── RedisConfig.java             # RedisCacheManager — 10min TTL, JSON serialization
```

---

## Caching Strategy

Two caching layers are deliberately used for different purposes:

**Caffeine (in-process) — JWT claims**

Every authenticated request previously re-parsed and cryptographically verified the JWT from scratch. Caffeine caches parsed `Claims` by token string for 10 minutes (well within the 24hr token TTL). Cache miss = one cryptographic parse. All subsequent hits = in-memory HashMap lookup in nanoseconds. Max 1,000 entries. Configured in `JwtUtil` via `@PostConstruct`.

This mirrors the FedEx production pattern — Okta JWT tokens were cached in Caffeine to avoid synchronous network calls to Okta on every request. Here the expensive operation is local cryptographic work rather than a network call, but the caching pattern is identical.

**Redis (distributed) — surcharge rate lookups**

`GET /api/surcharge/lookup` results are cached by composite key: `origin-destination-weight-serviceType`. 10-minute TTL. JSON serialized via `GenericJackson2JsonRedisSerializer`. Avoids redundant rate computation for repeated route/weight combinations.

Redis is used here (not Caffeine) because rate data is shared across instances — all instances connect to the same Redis server and share cached results. Caffeine is per-JVM; Redis is distributed.

**The rule of thumb:**
- Caffeine = in-JVM, nanoseconds, for per-request hot-path operations where a cache miss means one extra computation
- Redis = external, network hop, for shared data that needs to be consistent across instances and survive app restarts

**Why `lookupRate` is cached but `calculate` is not:**

`calculate` writes a shipment record to PostgreSQL on every call. Caching it would skip the DB write on cache hits — incorrect behavior. `lookupRate` is intentionally read-only: the "what would this cost?" endpoint. Same inputs always produce the same output (pure function) — ideal for caching.

---

## Surcharge Calculation Logic

| Component | Rule |
|---|---|
| Base rate | `$10.00 flat + ($1.50 × weight in lbs)` |
| Express multiplier | Base rate `× 1.15` if `serviceType = EXPRESS` |
| Fuel surcharge | `8%` of base rate |
| Remote area fee | `$25.00` flat if destination is `AK` or `HI` |
| Final charge | `baseRate + fuelSurcharge + remoteAreaFee` |

**Example — 10lb EXPRESS to AK:**
- Base: `10 + (10 × 1.5) = 25.00 × 1.15 = 28.75`
- Fuel: `28.75 × 0.08 = 2.30`
- Remote: `25.00`
- **Total: $56.05**

---

## API Endpoints

### Auth

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/auth/login` | None | Returns JWT token |

**Request:**
```json
{
  "username": "admin",
  "password": "password"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9..."
}
```

---

### Surcharge

All `/api/**` endpoints except `/api/health` require `Authorization: Bearer <token>`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | None | Health check |
| GET | `/api/rates` | Required | Lists available service types |
| POST | `/api/surcharge/calculate` | Required | Calculates surcharge + persists shipment to DB |
| GET | `/api/surcharge/lookup` | Required | Calculates surcharge only — result cached in Redis |
| GET | `/api/shipments` | Required | Returns all persisted shipment records |

**POST `/api/surcharge/calculate` request:**
```json
{
  "origin": "CA",
  "destination": "AK",
  "weight": 10.0,
  "serviceType": "EXPRESS"
}
```

**Response:**
```json
{
  "shipmentId": "uuid-here",
  "baseRate": 28.75,
  "fuelSurcharge": 2.30,
  "remoteAreaFee": 25.00,
  "finalCharge": 56.05
}
```

**GET `/api/surcharge/lookup` (Redis-cached):**
```
GET /api/surcharge/lookup?origin=CA&destination=AK&weight=10.0&serviceType=EXPRESS
```
Same response shape. `shipmentId` is null — no DB write.

---

## Running Locally

### Prerequisites
- Java 17
- Maven
- Docker Desktop

### Option A — Docker Compose (recommended — full stack in one command)
```bash
docker-compose up --build
```
Starts PostgreSQL, Redis, and the app together. App available at `http://localhost:8080`.

### Option B — Run app locally, infrastructure via Docker
```bash
# Start PostgreSQL
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=shiprate \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:16-alpine

# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Start app
./mvnw spring-boot:run
```

### Option C — IntelliJ
Run `ShiprateApiApplication.java` directly. Start PostgreSQL and Redis first (Option B above).

---

## Testing the API

### 1. Get a token
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

### 2. Calculate a surcharge (persists to DB)
```bash
curl -X POST http://localhost:8080/api/surcharge/calculate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"origin":"CA","destination":"AK","weight":10.0,"serviceType":"EXPRESS"}'
```

### 3. Lookup cached rate (call twice — second hits Redis)
```bash
curl "http://localhost:8080/api/surcharge/lookup?origin=CA&destination=AK&weight=10.0&serviceType=EXPRESS" \
  -H "Authorization: Bearer <token>"
```

### 4. Verify Redis cache entry
```bash
docker exec -it $(docker ps -qf "ancestor=redis:7-alpine") redis-cli
KEYS *
# Expected: "surcharge-rates::CA-AK-10.0-EXPRESS"
```

### 5. View all shipments
```bash
curl http://localhost:8080/api/shipments \
  -H "Authorization: Bearer <token>"
```

---

## Running Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=SurchargeServiceTest

# Single test method
mvn test -Dtest=SurchargeServiceTest#calculate_groundRate_returnsCorrectCharges
```

Tests use H2 in-memory database and disable Redis via the `test` Spring profile. No external infrastructure needed.

**What the 4 unit tests validate:**

| Test | Business rule |
|---|---|
| `calculate_groundRate_returnsCorrectCharges` | Base rate formula: `10 + (weight × 1.5)` |
| `calculate_expressService_appliesMultiplier` | EXPRESS adds 15% multiplier |
| `calculate_alaskaDestination_appliesRemoteAreaFee` | AK and HI trigger $25 remote fee |
| `calculate_persistsShipmentToDatabase` | `save()` called with correct data |

---

## CI/CD

GitHub Actions runs on every push and pull request to `main`:
- Sets up JDK 17 (Temurin distribution)
- Caches Maven dependencies between runs
- Runs `mvn verify` with the `test` Spring profile (H2 + no Redis)

Workflow: `.github/workflows/ci.yml`

The `test` profile is used in CI because the GitHub Actions runner has no PostgreSQL or Redis. H2 in-memory database allows the full test suite to run without any infrastructure.

---

## Configuration

All configuration lives in `src/main/resources/application.yaml`.

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/shiprate` | PostgreSQL connection |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `jwt.secret` | base64 key | JWT signing key — replace in production |
| `jwt.expiration` | `86400000` | Token TTL in ms (24 hours) |
| `spring.jpa.open-in-view` | `false` | Prevents lazy-loading during response serialization |

For Docker Compose, the app container overrides `SPRING_DATASOURCE_URL` and `SPRING_DATA_REDIS_HOST` via environment variables so the app connects to Docker service hostnames (`postgres`, `redis`) instead of `localhost`.

---

## Demo Credentials

| Username | Password |
|---|---|
| admin | password |

Hardcoded demo user in `AuthController.java`. The password is encoded at startup using `BCryptPasswordEncoder` — not stored as a static hash. Replace with a proper `UserDetailsService` backed by the database for any real deployment.

---

## Known Limitations

**JWT is stateless — tokens cannot be revoked before expiry**

With pure stateless JWT there is no way to invalidate a token before its natural expiry without adding state somewhere. If a token is stolen it remains valid for up to 24 hours. Production solutions:
- Token blacklist in Redis: store revoked tokens until natural expiry, check on every request
- Short expiry (15 minutes) + refresh tokens: limits the blast radius of a stolen token
- Okta or another identity provider: enterprise-grade token management

**Floating point precision in rate calculations**

Java `double` cannot represent every decimal number exactly in binary. `25.0 × 1.15` produces `28.749999999999996` instead of `28.75`. Unit tests use `isCloseTo(value, within(0.01))` to handle this. Production monetary systems should use `BigDecimal` for exact decimal arithmetic.

**Demo credentials are hardcoded**

`AuthController` uses a single in-memory admin user. Production auth requires a `UserDetailsService` backed by a users table, proper registration/password-reset flows, and role-based access control.

**No token refresh mechanism**

Tokens expire after 24 hours with no refresh endpoint. Users must re-login. Production APIs implement refresh token rotation.

---

## Lessons Learned and Common Mistakes

This section documents real mistakes made during development. Understanding why something broke is as valuable as knowing how to fix it.

---

### 1. Missing controller file causes 403, not 404

**What happened:** `AuthController.java` was never created. `POST /auth/login` was in `permitAll()` so Spring Security passed it through. Spring MVC had no handler for the route so it forwarded to `/error`. The `/error` endpoint was not in `permitAll()` so Security returned 403.

**The misleading part:** The 403 looked like a security config problem. The actual problem was a missing file with nothing to do with security.

**The lesson:** When you get 403 on a `permitAll()` route, add `/error` to `permitAll()` temporarily. The real error will surface instead of being masked by a security response.

---

### 2. Spring Security 6 changed how `requestMatchers` works

**What happened:** Using `requestMatchers(String...)` in Spring Security 6 with Spring MVC present uses `MvcRequestMatcher` instead of `AntPathRequestMatcher`. This caused `permitAll()` routes to not match correctly, returning 403.

**The fix:** Use `AntPathRequestMatcher` explicitly:
```java
.requestMatchers(new AntPathRequestMatcher("/auth/login")).permitAll()
```

**The lesson:** Spring Security 6 introduced breaking changes from Spring Security 5. Always check the migration guide when upgrading major versions.

---

### 3. Hardcoded BCrypt hash that did not match

**What happened:** `AuthController` had a hardcoded BCrypt hash for the demo password. BCrypt hashes cannot be constructed manually — they must be generated. The hash did not match "password" so every login returned 401.

**The fix:** Encode the password at startup using the same `PasswordEncoder` bean:
```java
this.demoPassHash = passwordEncoder.encode("password");
```

**The lesson:** Never hardcode BCrypt hashes. Always generate them programmatically using the same encoder that will verify them.

---

### 4. `depends_on` does not wait for PostgreSQL to be ready

**What happened:** Docker Compose `depends_on` only waits for the container to start, not for PostgreSQL to be ready to accept connections. The app started connecting before PostgreSQL finished initializing, causing `UnknownHostException` and repeated crashes.

**The fix:** Add a healthcheck to the postgres service and use `condition: service_healthy` in `depends_on`.

**The lesson:** `depends_on` is not enough for databases. Always add a healthcheck when your app depends on a database being fully initialized.

---

### 5. `@Profile("!test")` required on custom Redis config

**What happened:** `application-test.yaml` excluded Redis autoconfiguration but `RedisConfig.java` is a custom `@Configuration` class. Spring loads all configuration classes regardless of profile. `RedisConfig` tried to inject `RedisConnectionFactory` which did not exist in the test context, crashing the Spring context.

**The fix:** Add `@Profile("!test")` to `RedisConfig`:
```java
@Profile("!test")
public class RedisConfig { ... }
```

**The lesson:** `autoconfigure.exclude` in yaml only excludes Spring Boot's auto-configuration. Your own `@Configuration` beans are always loaded unless explicitly guarded with `@Profile`.

---

### 6. Do not include angle brackets when pasting tokens in curl commands

**What happened:** Instructions said `Authorization: Bearer <token>` — the angle brackets are placeholders. Pasting the token WITH the brackets caused 403 because the token string was malformed.

**Correct:**
```bash
-H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

**Incorrect:**
```bash
-H "Authorization: Bearer <eyJhbGciOiJIUzM4NCJ9...>"
```

---

### 7. Floating point arithmetic in unit tests

**What happened:** `assertThat(response.baseRate).isEqualTo(28.75)` failed because `25.0 × 1.15 = 28.749999999999996` in Java double arithmetic.

**The fix:**
```java
assertThat(response.baseRate).isCloseTo(28.75, within(0.01));
```

**The lesson:** Never use exact equality assertions on `double` values from multiplication or division. For production financial systems use `BigDecimal`.

---

### 8. Caffeine cache cannot be inspected externally

Caffeine lives inside the JVM heap. Unlike Redis there is no CLI or external process to inspect. To observe Caffeine behavior add logging inside the cache lookup or enable `recordStats()` and expose via Actuator metrics at `/actuator/metrics`.

---

### 9. PAT exposed in error output

**What happened:** A `git push` error printed the full Personal Access Token in terminal output.

**The lesson:** PATs are passwords. If one appears in any log or output — revoke it immediately. Switch to SSH to avoid this entirely — SSH keys are never exposed in command output.

---

### 10. Running docker-compose from the wrong directory

**What happened:** `docker-compose up --build` returned `no configuration file provided: not found` because the command was run from the home directory instead of the project root.

**The lesson:** Docker Compose always looks for `docker-compose.yml` in the current directory. Always `cd` into the project root first.

---

## License

MIT
