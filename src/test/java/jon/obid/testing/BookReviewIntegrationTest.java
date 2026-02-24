package jon.obid.testing;

/*
 * ── Phase 4: @SpringBootTest — Full Stack Integration Tests ──────────────────
 *
 *  Topic  : End-to-end HTTP testing against a real PostgreSQL database
 *  Layer  : Full stack — controllers, services, repositories, real database
 *  Tools  : JUnit 5, MockMvc, Testcontainers (PostgreSQL 17.2), Spring Security test
 *
 *  Spring Boot 3.x notes:
 *   - @WebMvcTest includes Spring Security auto-configuration automatically — no
 *     @ImportAutoConfiguration needed, unlike Boot 4.x.
 *   - @MockBean is the standard annotation (Boot 3.x); @MockitoBean is the Boot 4.x replacement.
 *   - @AutoConfigureMockMvc is at org.springframework.boot.test.autoconfigure.web.servlet.
 *   - Jackson 2.x (com.fasterxml.jackson.databind) — not used directly in this test.
 *
 *  How this differs from the existing test classes:
 *
 *   ReviewControllerTest (@WebMvcTest):
 *     - Mocks the service layer entirely
 *     - Only verifies HTTP/security rules
 *     - Cannot catch service or database bugs
 *
 *   ReviewRepositoryNoInMemoryTest (@DataJpaTest + Testcontainers):
 *     - Tests SQL queries against real PostgreSQL in isolation
 *     - No controllers, no services
 *
 *   THIS CLASS (@SpringBootTest + MockMvc + Testcontainers):
 *     - No mocks for services or repositories — everything is real
 *     - Exercises the full HTTP → Spring MVC → Service → Repository → PostgreSQL path
 *     - Catches cross-layer bugs invisible to slice tests
 *
 *  Key decisions:
 *
 *   @Testcontainers(disabledWithoutDocker = true)
 *     Skips gracefully when Docker Desktop is not running instead of failing.
 *
 *   Static PostgreSQLContainer
 *     One container is shared across all tests in this class for speed.
 *     Spring Boot reuses the same ApplicationContext across tests that share
 *     the same context configuration, so the container is started only once.
 *
 *   @MockBean JwtDecoder
 *     WebSecurityConfig configures a JWT resource server. Without a real Keycloak
 *     running, Spring Boot cannot auto-configure JwtDecoder from issuer-uri.
 *     Providing a mock satisfies the dependency and lets the SecurityFilterChain start.
 *
 *   @ActiveProfiles("integration-test")
 *     Deactivates the "default" Spring profile, which prevents InitialBookCreator
 *     (annotated @Profile("default")) from running at startup. Without this,
 *     InitialBookCreator would try to call the external Open Library API during
 *     context startup, which is undesirable in automated tests.
 *
 *   @BeforeEach database cleanup
 *     Reviews are deleted before books to respect the FK constraint
 *     (reviews.book_id → books.id). This guarantees full test isolation without
 *     relying on @Transactional rollback (which is not active in @SpringBootTest).
 *
 *  Scenarios covered:
 *
 *   1. Public catalog      — GET /api/books returns books without authentication
 *   2. Public reviews      — GET /api/books/reviews returns reviews without authentication
 *   3. Security 401        — /api/books/reviews/statistics rejects unauthenticated requests
 *   4. Security 401        — POST /api/books/{isbn}/reviews rejects unauthenticated requests
 *   5. Security 403        — DELETE without ROLE_moderator returns 403 Forbidden
 *   6. Full lifecycle      — create review → read by id → moderator deletes → 404 confirmed
 *   7. Bean Validation 400 — negative rating and missing title are rejected before the service
 *   8. Quality 418         — short content fails ReviewVerifier → 418 I Am A Teapot
 *   9. Quality 418         — Lorem ipsum content fails ReviewVerifier → 418 I Am A Teapot
 *  10. Statistics          — authenticated user sees aggregated ratings for a book
 *  11. Not found 404       — GET non-existent review returns 404
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */

import jon.obid.testing.book.management.Book;
import jon.obid.testing.book.management.BookRepository;
import jon.obid.testing.book.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration-test")
class BookReviewIntegrationTest {

  // Static → shared across all tests in this class; started once, stopped after the class.
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.2")
          .withDatabaseName("test")
          .withUsername("duke")
          .withPassword("s3cret");

  // Runs once before the Spring context is created — wires the container's runtime
  // JDBC URL, username, and password into Spring's Environment so DataSource picks them up.
  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  // No real Keycloak available → mock the JwtDecoder bean so the SecurityFilterChain starts.
  // Boot 3.x: @MockBean (replaced by @MockitoBean in Boot 4.x).
  @MockBean
  JwtDecoder jwtDecoder;

  @Autowired MockMvc mockMvc;
  @Autowired BookRepository bookRepository;
  @Autowired ReviewRepository reviewRepository;

  @BeforeEach
  void setUp() {
    // Delete in FK order: reviews reference books, so reviews must go first.
    reviewRepository.deleteAll();
    bookRepository.deleteAll();
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  /** Persists a minimal {@link Book} and returns the saved entity with its generated ID. */
  private Book createBook(String isbn, String title) {
    Book book = new Book();
    book.setIsbn(isbn);
    book.setTitle(title);
    book.setAuthor("Test Author");
    book.setGenre("Software Engineering");
    book.setThumbnailUrl("http://example.com/thumbnail.png");
    book.setDescription("A test book used in integration tests.");
    book.setPublisher("TestPublisher");
    book.setPages(300L);
    return bookRepository.save(book);
  }

  // ── Scenario 1: Public book catalog ──────────────────────────────────────

  @Test
  void shouldReturnEmptyBookCatalogPubliclyWhenNoBooksExist() throws Exception {
    // GET /api/books is public. Database is empty after setUp() → empty JSON array.
    mockMvc
        .perform(get("/api/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void shouldReturnSeededBooksPubliclyWithoutAuthentication() throws Exception {
    // Seed two books via the repository, then verify both ISBN values appear in the response.
    createBook("9781234567890", "Spring Boot Testing");
    createBook("9780987654321", "Clean Code");

    mockMvc
        .perform(get("/api/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[*].isbn", containsInAnyOrder("9781234567890", "9780987654321")));
  }

  // ── Scenario 2: Public review listing ─────────────────────────────────────

  @Test
  void shouldReturnEmptyReviewListPubliclyWhenNoReviewsExist() throws Exception {
    // GET /api/books/reviews is public. No reviews in DB → empty JSON array.
    mockMvc
        .perform(get("/api/books/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  // ── Scenario 3: Security — statistics requires authentication ─────────────

  @Test
  void shouldReturn401ForStatisticsEndpointWhenNoTokenProvided() throws Exception {
    // GET /api/books/reviews/statistics is behind the security filter.
    // A request without a JWT must be rejected with 401 Unauthorized.
    mockMvc
        .perform(get("/api/books/reviews/statistics"))
        .andExpect(status().isUnauthorized());
  }

  // ── Scenario 4: Security — review creation requires authentication ─────────

  @Test
  void shouldReturn401WhenCreatingReviewWithoutJwt() throws Exception {
    // POST /api/books/{isbn}/reviews requires an authenticated JWT.
    // The security filter chain rejects the request before the service is reached.
    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewTitle": "Good read",
                      "reviewContent": "This book explains Spring Boot testing patterns very well.",
                      "rating": 4
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  // ── Scenario 5: Security — delete requires ROLE_moderator ─────────────────

  @Test
  void shouldReturn403WhenDeletingReviewWithoutModeratorAuthority() throws Exception {
    // DELETE /api/books/{isbn}/reviews/{id} is guarded by @PreAuthorize("hasAuthority('ROLE_moderator')").
    // An authenticated user without that authority receives 403 Forbidden.
    mockMvc
        .perform(
            delete("/api/books/{isbn}/reviews/{reviewId}", "9781234567890", 1L)
                .with(jwt()))
        .andExpect(status().isForbidden());
  }

  // ── Scenario 6: Full review lifecycle ──────────────────────────────────────

  @Test
  void shouldCreateReadAndDeleteReviewInFullLifecycle() throws Exception {
    // Seed a book so ReviewService.createBookReview() can find it by ISBN.
    createBook("9781234567890", "Spring Boot Testing");

    // The JWT processor used for the authenticated user throughout this test.
    var userJwt =
        jwt()
            .jwt(
                builder ->
                    builder
                        .claim("preferred_username", "duke")
                        .claim("email", "duke@spring.io"));

    // ── Step 1: Create a review ──────────────────────────────────────────────
    // Content must pass ReviewVerifier: >10 words, no "Lorem ipsum",
    // fewer than 5 uses of "I", fewer than 3 uses of "good", no "shit".
    String createPayload =
        """
        {
          "reviewTitle": "Excellent Testing Reference",
          "reviewContent": "This book covers testing in Spring Boot extremely well and I enjoyed reading it thoroughly.",
          "rating": 5
        }
        """;

    String locationHeader =
        mockMvc
            .perform(
                post("/api/books/{isbn}/reviews", "9781234567890")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload)
                    .with(userJwt))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", containsString("/books/9781234567890/reviews/")))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    // Extract the auto-generated review ID from the Location header.
    String reviewId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

    // ── Step 2: Fetch the review and verify its fields ──────────────────────
    mockMvc
        .perform(
            get("/api/books/{isbn}/reviews/{reviewId}", "9781234567890", reviewId)
                .with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewTitle", is("Excellent Testing Reference")))
        .andExpect(jsonPath("$.reviewContent", containsString("Spring Boot")))
        .andExpect(jsonPath("$.rating", is(5)))
        .andExpect(jsonPath("$.bookIsbn", is("9781234567890")))
        .andExpect(jsonPath("$.bookTitle", is("Spring Boot Testing")))
        .andExpect(jsonPath("$.submittedBy", is("duke")));

    // ── Step 3: Delete the review as a moderator ────────────────────────────
    mockMvc
        .perform(
            delete("/api/books/{isbn}/reviews/{reviewId}", "9781234567890", reviewId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_moderator"))))
        .andExpect(status().isOk());

    // ── Step 4: Confirm the review no longer exists ──────────────────────────
    // ReviewService.getReviewById throws ReviewNotFoundException → @ResponseStatus(404).
    mockMvc
        .perform(
            get("/api/books/{isbn}/reviews/{reviewId}", "9781234567890", reviewId)
                .with(jwt()))
        .andExpect(status().isNotFound());
  }

  // ── Scenario 7: Bean Validation — 400 on invalid payload ──────────────────

  @Test
  void shouldReturn400WhenReviewPayloadFailsBeanValidation() throws Exception {
    // rating: -1 violates @PositiveOrZero.
    // reviewTitle is missing, violating @NotEmpty.
    // Spring MVC intercepts the constraint violation → 400 Bad Request before the service runs.
    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewContent": "This book covers testing thoroughly.",
                      "rating": -1
                    }
                    """)
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .claim("preferred_username", "duke")
                                    .claim("email", "duke@spring.io"))))
        .andExpect(status().isBadRequest());
  }

  // ── Scenario 8: Quality — 418 on short content ─────────────────────────────

  @Test
  void shouldReturn418WhenReviewContentIsTooShort() throws Exception {
    // ReviewVerifier rejects content with 10 words or fewer.
    // ReviewService throws BadReviewQualityException → @ResponseStatus(418 I Am A Teapot).
    createBook("9781234567890", "Spring Boot Testing");

    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewTitle": "Meh",
                      "reviewContent": "Not great at all.",
                      "rating": 2
                    }
                    """)
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .claim("preferred_username", "duke")
                                    .claim("email", "duke@spring.io"))))
        .andExpect(status().is(418)); // 418 I Am A Teapot from @ResponseStatus on BadReviewQualityException
  }

  // ── Scenario 9: Quality — 418 on Lorem ipsum content ──────────────────────

  @Test
  void shouldReturn418WhenReviewContentContainsLoremIpsum() throws Exception {
    // ReviewVerifier explicitly bans "Lorem ipsum" regardless of word count.
    createBook("9781234567890", "Spring Boot Testing");

    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewTitle": "Placeholder",
                      "reviewContent": "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempore.",
                      "rating": 3
                    }
                    """)
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .claim("preferred_username", "duke")
                                    .claim("email", "duke@spring.io"))))
        .andExpect(status().is(418)); // 418 I Am A Teapot from @ResponseStatus on BadReviewQualityException
  }

  // ── Scenario 10: Statistics ────────────────────────────────────────────────

  @Test
  void shouldReturnAggregatedStatisticsForAuthenticatedUser() throws Exception {
    // Seed a book and post two reviews with ratings 5 and 3.
    // The statistics endpoint should return 1 row: 2 ratings, average = 4.00.
    createBook("9781234567890", "Spring Boot Testing");

    var userJwt =
        jwt()
            .jwt(
                builder ->
                    builder
                        .claim("preferred_username", "duke")
                        .claim("email", "duke@spring.io"));

    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewTitle": "Outstanding Book",
                      "reviewContent": "This book covers testing in Spring Boot extremely well with many practical examples.",
                      "rating": 5
                    }
                    """)
                .with(userJwt))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", "9781234567890")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reviewTitle": "Very Informative",
                      "reviewContent": "Excellent coverage of unit integration and end to end testing patterns throughout.",
                      "rating": 3
                    }
                    """)
                .with(userJwt))
        .andExpect(status().isCreated());

    // Statistics aggregates across all reviews for each book.
    // ROUND(AVG(5, 3), 2) = 4.00 → serialized as JSON number.
    mockMvc
        .perform(get("/api/books/reviews/statistics").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].isbn", is("9781234567890")))
        .andExpect(jsonPath("$[0].ratings", is(2)))
        .andExpect(jsonPath("$[0].avg", closeTo(4.0, 0.01)));
  }

  // ── Scenario 11: Not found ──────────────────────────────────────────────────

  @Test
  void shouldReturn404WhenFetchingNonExistentReview() throws Exception {
    // The database is empty after setUp(). Any review ID will be unknown.
    // ReviewService.getReviewById throws ReviewNotFoundException → @ResponseStatus(404).
    mockMvc
        .perform(
            get("/api/books/{isbn}/reviews/{reviewId}", "9781234567890", 99999L)
                .with(jwt()))
        .andExpect(status().isNotFound());
  }
}
