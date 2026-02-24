# Spring Boot Testing Masterclass

> **Stack:** Spring Boot 2.4.13 · Java 17 · JUnit 5 · Mockito 5 · Testcontainers 1.21

This project is a structured, progressive guide to testing a Spring Boot application.
Every test file is a self-contained lesson. Work through them in the order listed below
and you will cover every major testing technique used in professional Spring Boot projects.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Overview](#project-overview)
3. [The Test Pyramid](#the-test-pyramid)
4. [Running the Tests](#running-the-tests)
5. [Phase 1 — Unit Tests (No Spring Context)](#phase-1--unit-tests-no-spring-context)
   - [Step 1: ReviewVerifierTest](#step-1-reviewverifiertest)
   - [Step 2: ReviewServiceTest](#step-2-reviewservicetest)
   - [Step 3: UserServiceTest](#step-3-userservicetest)
   - [Step 4: UserServiceRefactoredTest](#step-4-userservicerefactoredtest)
6. [Phase 2 — Slice Tests (Partial Spring Context)](#phase-2--slice-tests-partial-spring-context)
   - [Step 5: BookControllerTest](#step-5-bookcontrollertest)
   - [Step 6: ReviewControllerTest](#step-6-reviewcontrollertest)
   - [Step 7: ReviewRepositoryTest](#step-7-reviewrepositorytest)
   - [Step 8: ReviewRepositoryNoInMemoryTest](#step-8-reviewrepositorynoinmemorytest)
   - [Step 9: OpenLibraryApiClientTest](#step-9-openlibrayapiclienttest)
   - [Step 10: OpenLibraryRestTemplateApiClientTest](#step-10-openlibraryrestemplateapiclienttest)
7. [Phase 3 — Integration Test (Full Spring Context)](#phase-3--integration-test-full-spring-context)
   - [Step 11: TestingApplicationTests](#step-11-testingapplicationtests)
8. [Supporting Test Resources](#supporting-test-resources)
9. [Version Compatibility Notes](#version-compatibility-notes)
10. [Dependency Reference](#dependency-reference)

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 17+ | Text blocks and other Java 15+ features used in the project |
| Maven | 3.9+ | Use the included `mvnw` wrapper |
| Docker Desktop | latest | Required **only** for `ReviewRepositoryNoInMemoryTest` |

> **No Keycloak needed.** `JwtDecoder` is mocked in all test contexts so you never
> need a running identity provider to run the test suite.

---

## Project Overview

The application manages a book catalogue and review system:

```
src/main/java/jon/obid/testing/
├── book/
│   ├── management/
│   │   ├── Book.java                          Entity
│   │   ├── BookController.java                REST controller — GET /api/books
│   │   ├── BookManagementService.java         Service
│   │   ├── BookRepository.java                Spring Data JPA repository
│   │   ├── OpenLibraryApiClient.java          Reactive WebClient → openlibrary.org
│   │   ├── OpenLibraryRestTemplateApiClient.java  Synchronous RestTemplate alternative
│   │   ├── UserService.java                   Calls LocalDateTime.now() directly
│   │   └── UserServiceRefactored.java         Accepts java.time.Clock as dependency
│   └── review/
│       ├── ReviewController.java              REST controller — /api/books/reviews
│       ├── ReviewService.java                 Business logic
│       ├── ReviewRepository.java              JPA repository + native SQL query
│       └── ReviewVerifier.java                Pure POJO — content quality rules
└── config/
    └── WebSecurityConfig.java                 JWT resource server security
```

```
src/test/
├── java/
│   └── jon/obid/testing/
│       ├── book/management/
│       │   ├── BookControllerTest.java         @WebMvcTest
│       │   ├── UserServiceTest.java            mockStatic (LocalDateTime)
│       │   ├── UserServiceRefactoredTest.java  Clock injection
│       │   ├── OpenLibraryApiClientTest.java   MockWebServer + WebClient
│       │   └── OpenLibraryRestTemplateApiClientTest.java  @RestClientTest
│       └── book/review/
│           ├── ReviewVerifierTest.java         JUnit 5 features showcase
│           ├── ReviewServiceTest.java          Mockito mocking
│           ├── ReviewControllerTest.java       @WebMvcTest + JWT security
│           ├── ReviewRepositoryTest.java       @DataJpaTest + H2 + P6Spy
│           ├── ReviewRepositoryNoInMemoryTest.java  @DataJpaTest + Testcontainers
│           └── RandomReviewParameterResolverExtension.java  Custom JUnit 5 extension
└── resources/
    ├── badReview.csv                           Data for @CsvFileSource test
    ├── spy.properties                          P6Spy SQL logger configuration
    ├── scripts/
    │   └── INIT_REVIEW_EACH_BOOK.sql           @Sql fixture data
    └── stubs/openlibrary/
        └── success-9780596004651.json          JSON stub for HTTP client tests
```

---

## The Test Pyramid

```
         ╔══════════════════════╗
         ║   Phase 3            ║   1 test
         ║   @SpringBootTest    ║   Full context — expensive, catches wiring bugs
         ╠══════════════════════╣
         ║   Phase 2            ║   6 test classes
         ║   Slice Tests        ║   Partial context — focused and fast
         ║   @WebMvcTest        ║
         ║   @DataJpaTest       ║
         ║   @RestClientTest    ║
         ╠══════════════════════╣
         ║   Phase 1            ║   4 test classes
         ║   Unit Tests         ║   No Spring — milliseconds per test
         ║   Pure JUnit 5       ║
         ║   + Mockito          ║
         ╚══════════════════════╝
```

The further up the pyramid, the slower, more realistic, and more expensive the test.
Write most of your tests at the bottom and use higher layers only to verify integration
points that cannot be proven at a lower level.

---

## Running the Tests

```bash
# Run all tests (Docker required for Testcontainers test)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ReviewVerifierTest

# Run all tests except the Testcontainers one (no Docker needed)
./mvnw test -Dtest='!ReviewRepositoryNoInMemoryTest'

# Run with detailed output
./mvnw test -pl . --no-transfer-progress
```

> On Windows use `mvnw.cmd` instead of `./mvnw`.

---

## Phase 1 — Unit Tests (No Spring Context)

Unit tests load **no Spring context**. They test a single class in complete isolation.
Dependencies are replaced with Mockito mocks or simple stubs.
These tests typically finish in **under 100 ms** per class.

---

### Step 1: `ReviewVerifierTest`

**File:** `src/test/java/jon/obid/testing/book/review/ReviewVerifierTest.java`
**Class under test:** `ReviewVerifier` — a pure POJO with no dependencies

This is the foundation lesson. `ReviewVerifier` has no Spring beans, no I/O, no
database — it is instantiated directly with `new ReviewVerifier()` in `@BeforeEach`.

**JUnit 5 features demonstrated:**

| Annotation | Purpose | Where used |
|------------|---------|------------|
| `@Test` | A single test method | `shouldFailWhenReviewContainsSwearWord` |
| `@DisplayName` | Human-readable label in reports | `testLoremIpsum` |
| `@ParameterizedTest` + `@CsvFileSource` | Run once per row of a CSV file | `shouldFailWhenReviewIsOfBadQuality` |
| `@RepeatedTest(5)` | Run the same test N times | `shouldFailWhenRandomReviewQualityIsBad` |
| `@ExtendWith` | Register a custom extension | Class level |
| `@BeforeEach` | Setup before every test | `setup()` |

**Assertion libraries compared** — three tests verify the same behaviour with different
libraries so you can choose your preferred style:

```java
// JUnit 5 — simple and direct
assertTrue(result, "failure message");

// Hamcrest — composable matchers, excellent failure messages
MatcherAssert.assertThat(result, Matchers.equalTo(true));
MatcherAssert.assertThat(List.of(1,2,3,4,5), Matchers.hasSize(5));

// AssertJ — fluent chaining, rich IDE auto-complete
Assertions.assertThat(result).isTrue();
Assertions.assertThat(List.of(1,2,3,4,5)).contains(3).isNotEmpty();
```

**Data file:** `src/test/resources/badReview.csv`
```
This book was shit I don't like it
I was reading the book and I think the book is okay...
Good book with good agenda and good example...
```
Each line becomes one invocation of `shouldFailWhenReviewIsOfBadQuality`.

**Custom extension:** `RandomReviewParameterResolverExtension`
Implements JUnit 5's `ParameterResolver` to inject a randomly selected review string
into `@RepeatedTest` methods marked with `@RandomReview`. Demonstrates how to extend
JUnit 5 without any Spring involvement.

---

### Step 2: `ReviewServiceTest`

**File:** `src/test/java/jon/obid/testing/book/review/ReviewServiceTest.java`
**Class under test:** `ReviewService` — depends on four collaborators

This lesson introduces **Mockito** — the standard Java mocking library.

`ReviewService` depends on `ReviewVerifier`, `UserService`, `BookRepository`, and
`ReviewRepository`. In a unit test we do **not** want a database or Spring context —
we replace every collaborator with a Mockito mock.

**Mockito annotations:**
```java
@ExtendWith(MockitoExtension.class)      // activates Mockito's JUnit 5 integration
class ReviewServiceTest {

    @Mock private ReviewVerifier mockedReviewVerifier;  // creates a Mockito mock
    @Mock private UserService userService;
    @Mock private BookRepository bookRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private ReviewService cut;  // creates ReviewService + injects the mocks
}
```

**Mockito patterns demonstrated:**

| Pattern | Meaning | Example |
|---------|---------|---------|
| `when(...).thenReturn(...)` | Stub a return value | `when(bookRepository.findByIsbn(ISBN)).thenReturn(new Book())` |
| `when(...).thenAnswer(...)` | Stub with a lambda that receives call arguments | Simulate DB-assigned ID |
| `assertThrows(...)` | Verify an exception is thrown | Book not found → `IllegalArgumentException` |
| `verify(mock, times(0)).method()` | Assert a method was **never** called | Bad review must not be saved |

**Tests at a glance:**
1. Smoke test — `@Mock` + `@InjectMocks` wiring works
2. Missing book → `IllegalArgumentException`
3. Bad review content → `BadReviewQualityException`, repository never called
4. Good review → saved, returned ID verified

---

### Step 3: `UserServiceTest`

**File:** `src/test/java/jon/obid/testing/book/management/UserServiceTest.java`
**Class under test:** `UserService` — calls `LocalDateTime.now()` directly (a static method)

**The problem:** Standard Mockito mocks work through subclassing — you cannot override
a static method that way. `LocalDateTime.now()` is a static method, so it cannot be
intercepted by a regular `@Mock`.

**The solution — `Mockito.mockStatic()`:**
```java
try (MockedStatic<LocalDateTime> mockedLocalDateTime =
        Mockito.mockStatic(LocalDateTime.class)) {

    mockedLocalDateTime.when(LocalDateTime::now).thenReturn(defaultLocalDateTime);

    User result = cut.getOrCreateUser("duke", "duke@spring.io");

    assertEquals(defaultLocalDateTime, result.getCreatedAt());
}
// The real LocalDateTime.now() is restored as soon as the try block exits
```

**Why to avoid this when possible:**
- Requires a Mockito agent — adds startup overhead
- Thread-unsafe: parallel tests on the same static method will conflict
- Signals that the production code is hard to test by design

**Go to Step 4 for the preferred alternative.**

---

### Step 4: `UserServiceRefactoredTest`

**File:** `src/test/java/jon/obid/testing/book/management/UserServiceRefactoredTest.java`
**Class under test:** `UserServiceRefactored` — accepts `java.time.Clock` as a constructor dependency

**The clean solution to mocking time — Clock injection:**

Instead of calling `LocalDateTime.now()` directly, the service accepts a `Clock` bean:
```java
// Production code
LocalDateTime.now(this.clock)   // uses the injected clock
```

In production, Spring provides `Clock.systemDefaultZone()`.
In tests, `Clock` is a regular `@Mock` — no byte-code tricks needed:

```java
@Mock private Clock clock;  // plain Mockito mock

// Stub the two methods LocalDateTime.now(clock) calls internally:
Clock fixedClock = Clock.fixed(fixedDateTime.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
when(clock.instant()).thenReturn(fixedClock.instant());
when(clock.getZone()).thenReturn(fixedClock.getZone());
```

**Rule of thumb:** if you need to control time in a test, always prefer Clock injection
over `mockStatic`. The same principle applies to random numbers, UUIDs, and any other
"environmental" value — make them injectable dependencies.

---

## Phase 2 — Slice Tests (Partial Spring Context)

Spring Boot **test slices** load only the beans relevant to one architectural layer.
They are slower than unit tests (a context must start) but much faster than a full
`@SpringBootTest` because irrelevant beans are excluded.

---

### Step 5: `BookControllerTest`

**File:** `src/test/java/jon/obid/testing/book/management/BookControllerTest.java`
**Slice:** `@WebMvcTest` — loads the web layer only (controllers + MVC infrastructure)

**What `@WebMvcTest` loads:**
- The target controller (`BookController`)
- `DispatcherServlet`, `HandlerMapping`, Jackson, argument resolvers
- Spring Security (via `@Import` + `@ImportAutoConfiguration` in Boot 4.x)

**What it does NOT load:**
- JPA repositories or the database
- Service beans (replaced with `@MockitoBean`)
- Any other controllers

**`MockMvc` — the heart of web-layer testing:**
```java
this.mockMvc
    .perform(get("/api/books")
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
    .andExpect(status().is(200))
    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.size()", is(2)))
    .andExpect(jsonPath("$[0].isbn", is("42")))
    .andExpect(jsonPath("$[0].id").doesNotExist())  // @JsonIgnore verified
    .andDo(print());
```

**Tests at a glance:**
1. Empty book list → `200 []`
2. XML `Accept` header → `406 Not Acceptable`
3. Two books returned → JSON array with correct fields, `id` hidden

**Spring Boot 2.x note:** `@WebMvcTest` automatically includes Spring Security
auto-configuration — no `@ImportAutoConfiguration` is needed. Only the custom
`WebSecurityConfig` must be brought in explicitly via `@Import`:
```java
@WebMvcTest(BookController.class)
@Import(WebSecurityConfig.class)
```
> In Spring Boot 4.x this changes: security auto-configuration must be added manually via `@ImportAutoConfiguration`.

---

### Step 6: `ReviewControllerTest`

**File:** `src/test/java/jon/obid/testing/book/review/ReviewControllerTest.java`
**Slice:** `@WebMvcTest` — same as Step 5, but focused on **authorization rules**

This is the most security-intensive test class. It covers three authorization levels:

```
GET  /api/books/reviews               →  PUBLIC (no token needed)
GET  /api/books/reviews/statistics    →  AUTHENTICATED (any valid JWT)
POST /api/books/{isbn}/reviews        →  AUTHENTICATED + valid request body
DELETE /api/books/{isbn}/reviews/{id} →  ROLE_moderator required
```

**Key pattern — `.with(jwt())`:**
```java
// Any authenticated user
.with(jwt())

// Authenticated with custom JWT claims
.with(jwt().jwt(builder -> builder
    .claim("email", "duke@spring.io")
    .claim("preferred_username", "duke")))

// Authenticated with a specific role
.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_moderator")))
```

**Why NOT `@WithMockUser`:**
`@WithMockUser` sets the `SecurityContext` in the test thread. With STATELESS JWT
security (no HTTP session), the filter chain never reads from `SecurityContext` — it
reads from the JWT token. So `@WithMockUser` always results in `401`. Use `.with(jwt())`
instead.

**Tests at a glance:**
1. Public endpoint — no token → `200 OK`
2. Statistics — no token → `401 Unauthorized`, service never called
3. Statistics — with JWT → `200 OK`, service called once
4. Create review — valid JWT + valid body → `201 Created` + `Location` header
5. Create review — valid JWT + invalid body (rating: -1) → `400 Bad Request`
6. Delete review — authenticated without moderator role → `403 Forbidden`
7. Delete review — with `ROLE_moderator` → `200 OK`, service called once

---

### Step 7: `ReviewRepositoryTest`

**File:** `src/test/java/jon/obid/testing/book/review/ReviewRepositoryTest.java`
**Slice:** `@DataJpaTest` — loads JPA layer only (repositories + entities + H2)

**What `@DataJpaTest` loads:**
- Spring Data repositories
- JPA entities and `EntityManager`
- H2 in-memory database (by default)
- Hibernate DDL (`create-drop`)

**What it does NOT load:**
- Controllers, services, security configuration
- The full application context

**P6Spy — transparent SQL logging:**
P6Spy is a proxy that wraps any JDBC driver and logs every SQL statement. It is
configured by overriding the datasource in `@DataJpaTest` properties:
```java
@DataJpaTest(properties = {
    "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
    "spring.datasource.url=jdbc:p6spy:h2:mem:testing;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
```

Configuration file: `src/test/resources/spy.properties`
```properties
modulelist=com.p6spy.engine.spy.P6SpyFactory
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat
```

**Transaction isolation guarantee:**
`@DataJpaTest` wraps every test in a transaction that **rolls back** after the test.
The `@BeforeEach` assertion `assertEquals(0, cut.count())` proves this — if it ever
fails, a test is committing data outside its transaction.

---

### Step 8: `ReviewRepositoryNoInMemoryTest`

**File:** `src/test/java/jon/obid/testing/book/review/ReviewRepositoryNoInMemoryTest.java`
**Slice:** `@DataJpaTest` with a **real PostgreSQL** database via Testcontainers

> **Requires:** Docker Desktop running

**When H2 is not enough:**
- Native SQL queries using PostgreSQL-specific syntax
- Aggregate functions behaving differently across databases
- Flyway/Liquibase migration scripts targeting PostgreSQL
- JSON column types, window functions, `RETURNING` clauses

**Testcontainers in three annotations:**

```java
@Testcontainers(disabledWithoutDocker = true)  // skip gracefully when Docker is absent
class ReviewRepositoryNoInMemoryTest {

    @Container                                  // start/stop managed automatically
    static PostgreSQLContainer<?> container =
        new PostgreSQLContainer<>("postgres:17.2")
            .withDatabaseName("test")
            .withUsername("duke")
            .withPassword("s3cret");

    @DynamicPropertySource                      // wire runtime port into Spring before context starts
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.username", container::getUsername);
    }
}
```

**`@Sql` — declarative fixture loading:**
```java
@Test
@Sql(scripts = "/scripts/INIT_REVIEW_EACH_BOOK.sql")
void shouldGetTwoReviewStatisticsWhenDatabaseContainsTwoBooksWithReview() { ... }
```

The script inserts 1 user, 2 books, and 3 reviews before the test runs. See
`src/test/resources/scripts/INIT_REVIEW_EACH_BOOK.sql`.

---

### Step 9: `OpenLibraryApiClientTest`

**File:** `src/test/java/jon/obid/testing/book/management/OpenLibraryApiClientTest.java`
**Approach:** Plain Java — no Spring context; uses **OkHttp MockWebServer**

**The problem with mocking WebClient directly:**
`WebClient` has a deeply fluent API. Mocking every intermediate step (`builder → spec
→ response mono → body`) produces brittle tests that break whenever the production
code adds a single operator to the reactive chain.

**MockWebServer — a real local HTTP server:**
```java
@BeforeEach
void setup() throws IOException {
    this.mockWebServer = new MockWebServer();
    this.mockWebServer.start();           // binds to a random free port on localhost

    this.cut = new OpenLibraryApiClient(
        WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())  // point at the mock server
            .build());
}

@AfterEach
void shutdown() throws IOException {
    this.mockWebServer.shutdown();
}
```

**Enqueueing responses:**
```java
this.mockWebServer.enqueue(new MockResponse()
    .addHeader("Content-Type", "application/json; charset=utf-8")
    .setBody(VALID_RESPONSE));

Book result = cut.fetchMetadataForBook(ISBN);

// Inspect what the client actually sent
RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
assertEquals("/api/books?jscmd=data&format=json&bibkeys=" + ISBN, recordedRequest.getPath());
```

**Retry logic test:**
The client is configured with a maximum of 2 retries. The test validates all three
attempts by enqueuing them in order:
```
Attempt 1: 500 Internal Server Error     → retry
Attempt 2: 200 OK but body delayed 2 s   → 1 s read timeout fires → retry
Attempt 3: 200 OK immediately            → success
```

**JSON stub file:** `src/test/resources/stubs/openlibrary/success-9780596004651.json`
Loaded at class-load time in a `static {}` block to avoid repeated I/O across test runs.

---

### Step 10: `OpenLibraryRestTemplateApiClientTest`

**File:** `src/test/java/jon/obid/testing/book/management/OpenLibraryRestTemplateApiClientTest.java`
**Slice:** `@RestClientTest` — synchronous `RestTemplate` counterpart to MockWebServer

`@RestClientTest` auto-configures `MockRestServiceServer` — a Spring-managed server
that intercepts `RestTemplate` calls at the Spring level. No actual network activity
occurs.

**`RestTemplate` must be provided explicitly:**
```java
@RestClientTest(OpenLibraryRestTemplateApiClient.class)
class OpenLibraryRestTemplateApiClientTest {

    // RestTemplateAutoConfiguration registers RestTemplateBuilder, not a RestTemplate bean.
    // We provide the bean explicitly so MockRestServiceServer can wire into it.
    // Uses Boot 2.x / 3.x package: org.springframework.boot.web.client.RestTemplateBuilder
    // (moved to org.springframework.boot.restclient.RestTemplateBuilder in Boot 4.x)
    @TestConfiguration
    static class RestTemplateConfig {
        @Bean
        RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.build();
        }
    }
}
```

**MockRestServiceServer API:**
```java
// Declare what you expect and how to respond
this.mockRestServiceServer
    .expect(requestTo(Matchers.containsString(ISBN)))
    .andExpect(header("X-Custom-Auth", "Duke42"))   // verify request headers
    .andRespond(withSuccess(
        new ClassPathResource("/stubs/openlibrary/success-" + ISBN + ".json"),
        MediaType.APPLICATION_JSON));

// MockRestServiceServer verifies all expectations were met after the test
```

**Tests at a glance:**
1. Successful response → all fields parsed from JSON stub
2. Partial response (missing description/genre) → fallback `"n.A"` values
3. Server error (`500`) → `HttpServerErrorException` propagated
4. Request headers verified — `X-Custom-Auth` and `X-Customer-Id` present on every call

---

## Phase 3 — Integration Test (Full Spring Context)

### Step 11: `TestingApplicationTests`

**File:** `src/test/java/jon/obid/testing/TestingApplicationTests.java`
**Annotation:** `@SpringBootTest` — loads the **entire** ApplicationContext

`@SpringBootTest` is the most expensive test type. It boots the application exactly as
it would start in production — every `@Component`, `@Service`, `@Repository`,
`@Controller`, and `@Configuration` bean is instantiated and wired together.

**When to use it:**
- Verify the context starts correctly (catch wiring bugs, circular dependencies, missing properties)
- End-to-end integration tests that span multiple layers
- Keep the count small — prefer slice tests for focused concerns

**The `contextLoads` test has no assertions by design:**
```java
@Test
void contextLoads() {
    // If any bean fails to initialize, Spring throws BeanCreationException
    // which fails this test with a precise, actionable error message.
}
```

**JwtDecoder mock:**
```java
// No Keycloak runs during tests — JwtDecoder cannot be auto-configured from issuer-uri.
// @MockBean replaces the auto-configured bean with a Mockito mock so the
// SecurityFilterChain can start without a live identity provider.
// Note: renamed to @MockitoBean in Spring Boot 3.4+ / 4.x.
@MockBean
JwtDecoder jwtDecoder;
```

---

## Supporting Test Resources

```
src/test/resources/
├── badReview.csv                     One bad review per line; read by @CsvFileSource
├── spy.properties                    P6Spy logger config (routes SQL to SLF4J)
├── scripts/
│   └── INIT_REVIEW_EACH_BOOK.sql     @Sql fixture: 1 user, 2 books, 3 reviews
└── stubs/openlibrary/
    └── success-9780596004651.json    Realistic Open Library API response
```

**`badReview.csv`** — data source for `@CsvFileSource` parameterized tests.
Each line is passed as the `review` argument to `shouldFailWhenReviewIsOfBadQuality`.

**`INIT_REVIEW_EACH_BOOK.sql`** — inserts deterministic fixture data before
`ReviewRepositoryNoInMemoryTest.shouldGetTwoReviewStatisticsWhenDatabaseContainsTwoBooksWithReview`.
The data is rolled back after the test.

**`success-9780596004651.json`** — a real captured response from `openlibrary.org`
for ISBN `9780596004651` (*Head First Java*). Used as the stub body in both HTTP
client tests.

**`spy.properties`** — tells P6Spy to route all logged SQL through SLF4J (so it
appears in your test console alongside Spring's log output).

---

## Version Compatibility Notes

This project runs on Spring Boot **2.4.13** with **Java 17**. The test code was
originally written for Spring Boot 4.0.3 and has been adapted to 2.4.x. The table
below shows what changes when you move to newer versions — useful if you follow along
with a newer branch of this course.

| Area | Spring Boot 2.4.x (this branch) | Spring Boot 3.x | Spring Boot 4.x |
|------|---------------------------------|-----------------|-----------------|
| JPA / Validation annotations | `javax.persistence.*`, `javax.validation.*` | `jakarta.persistence.*`, `jakarta.validation.*` | `jakarta.*` |
| `@MockBean` | `@MockBean` | `@MockBean` (deprecated in 3.4) | `@MockitoBean` |
| `@WebMvcTest` + security | Auto-included | Auto-included | Must add `@ImportAutoConfiguration` explicitly |
| `RestTemplateBuilder` package | `org.springframework.boot.web.client` | `org.springframework.boot.web.client` | `org.springframework.boot.restclient` |
| Test slice starters | Bundled in `spring-boot-starter-test` | Bundled in `spring-boot-starter-test` | Separate starters per slice |
| Mockito (overridden) | **5.11.0** (override required — 3.6.x doesn't support Java 17/21) | 5.x (bundled) | 5.x (bundled) |
| ByteBuddy (overridden) | **1.14.18** (override required — 1.10.x missing `GraalImageCode`) | 1.14.x (bundled) | 1.14.x (bundled) |

---

## Dependency Reference

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-test` | JUnit 5, Mockito 5.11.0 (overridden), AssertJ, Hamcrest, MockMvc, `@WebMvcTest`, `@DataJpaTest`, `@RestClientTest` |
| `spring-security-test` | `.with(jwt())`, `SecurityMockMvcRequestPostProcessors` |
| `testcontainers-bom` + `postgresql` | Real PostgreSQL via Docker |
| `testcontainers:junit-jupiter` | `@Testcontainers`, `@Container` lifecycle management |
| `h2` | In-memory database for `@DataJpaTest` |
| `p6spy` | SQL query logging proxy wrapping H2 |
| `mockwebserver` (OkHttp 4.11) | Real local HTTP server for `WebClient` tests |
| `awaitility` | Assertion retries for async/event-driven code |
| `wiremock-standalone` | Alternative HTTP stub server (available for future phases) |
