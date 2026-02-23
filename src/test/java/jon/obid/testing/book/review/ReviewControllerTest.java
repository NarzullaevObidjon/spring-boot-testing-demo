package jon.obid.testing.book.review;

/*
 * ── Phase 2: @WebMvcTest with JWT/OAuth2 Security Testing ────────────────────
 *
 *  Topic  : Testing authorization rules — public, authenticated, and role-gated endpoints
 *  Layer  : Web (Spring MVC) — no JPA, no real services, no real Keycloak
 *  Tools  : JUnit 5, MockMvc, Mockito, Spring Security test support (.with(jwt()))
 *
 *  This test class covers three distinct authorization scenarios:
 *
 *   1. Public endpoint (no token required)
 *      GET /api/books/reviews → any caller may read reviews
 *
 *   2. Authenticated endpoint (any valid JWT required)
 *      GET /api/books/reviews/statistics → must be logged in
 *      POST /api/books/{isbn}/reviews    → must be logged in + valid payload
 *
 *   3. Role-gated endpoint (specific authority required)
 *      DELETE /api/books/{isbn}/reviews/{id} → requires ROLE_moderator
 *
 *  Key Spring Security testing pattern — .with(jwt()):
 *   - Injects a synthetic JWT token into the MockMvc request context.
 *   - No real Keycloak is needed — the token is created in-memory.
 *   - Custom JWT claims (preferred_username, email) can be set via the builder.
 *   - Authorities are injected directly: .jwt().authorities(new SimpleGrantedAuthority(...))
 *
 *  Why NOT @WithMockUser:
 *   @WithMockUser works only when the SecurityContext is set in the test thread.
 *   With STATELESS JWT security (no session, no SecurityContextHolder propagation),
 *   the filter chain never reads from the SecurityContext — it reads from the JWT token.
 *   Therefore @WithMockUser always results in 401. Use .with(jwt()) instead.
 *
 *  Spring Boot 4.x changes addressed here:
 *   - @WebMvcTest no longer auto-imports Spring Security auto-configurations → added explicitly
 *   - @MockitoBean replaces the deprecated @MockBean from Spring Boot 3.4+
 *   - Jackson 3.x uses tools.jackson.databind (not com.fasterxml.jackson.databind)
 *
 *  Author : Obidjon Sattarov <obidsattarovich3600@gmail.com>
 * ─────────────────────────────────────────────────────────────────────────────
 */

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jon.obid.testing.config.WebSecurityConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Spring Boot 4.x: @WebMvcTest no longer imports security auto-configs automatically.
// @Import(WebSecurityConfig.class) brings in the security rules.
// @ImportAutoConfiguration adds the three auto-configs that activate those rules.
@WebMvcTest(ReviewController.class)
@Import(WebSecurityConfig.class)
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class ReviewControllerTest {

  // ReviewService is not part of the web slice — replace it with a Mockito mock
  @MockitoBean private ReviewService reviewService;

  // WebSecurityConfig configures a JWT resource server, which requires a JwtDecoder bean.
  // No real Keycloak runs during tests, so we provide a mock to satisfy the dependency.
  @MockitoBean private JwtDecoder jwtDecoder;

  @Autowired private MockMvc mockMvc;

  // Jackson 3.x ObjectMapper — used to build JSON request/response bodies programmatically
  private ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    this.objectMapper = new ObjectMapper();
  }

  // ── Public endpoint ───────────────────────────────────────────────────────

  @Test
  void shouldReturnTwentyReviewsWithoutAnyOrderWhenNoParametersAreSpecified() throws Exception {
    // GET /api/books/reviews is a public endpoint — no authentication token is required.
    // Build a JSON array with a single review statistic and stub the service to return it.
    ArrayNode result = objectMapper.createArrayNode();

    ObjectNode statistic = objectMapper.createObjectNode();
    statistic.put("bookId", 1);
    statistic.put("isbn", "42");
    statistic.put("avg", 89.3);
    statistic.put("ratings", 2);
    result.add(statistic);

    when(reviewService.getAllReviews(20, "none")).thenReturn(result);

    this.mockMvc
        .perform(get("/api/books/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size()", Matchers.is(1)));
  }

  // ── Authenticated endpoints ───────────────────────────────────────────────

  @Test
  void shouldNotReturnReviewStatisticsWhenUserIsUnauthenticated() throws Exception {
    // No token → Spring Security must reject with 401 Unauthorized.
    // verifyNoInteractions() guarantees the service was never reached — security blocked early.
    this.mockMvc.perform(get("/api/books/reviews/statistics")).andExpect(status().isUnauthorized());

    verifyNoInteractions(reviewService);
  }

  @Test
  void shouldReturnReviewStatisticsWhenUserIsAuthenticated() throws Exception {
    // .with(jwt()) injects a synthetic JWT token — any authenticated user may access this endpoint.
    // The token contains no custom claims; authorization only checks "is the user authenticated?"
    this.mockMvc
        .perform(get("/api/books/reviews/statistics").with(jwt()))
        .andExpect(status().isOk());

    verify(reviewService).getReviewStatistics();
  }

  @Test
  void shouldCreateNewBookReviewForAuthenticatedUserWithValidPayload() throws Exception {
    // ReviewController extracts preferred_username and email from the JWT claims.
    // We inject those claims via the jwt() builder so the controller can read them.
    String requestBody =
        """
        {
          "reviewTitle": "Great Java Book!",
          "reviewContent": "I really like this book!",
          "rating": 4
        }
        """;

    when(reviewService.createBookReview(
            eq("42"), any(BookReviewRequest.class), eq("duke"), endsWith("spring.io")))
        .thenReturn(84L);

    this.mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", 42)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .claim("email", "duke@spring.io")
                                    .claim("preferred_username", "duke"))))
        .andExpect(status().isCreated())
        // On success the controller sets a Location header pointing at the new review resource
        .andExpect(header().exists("Location"))
        .andExpect(header().string("Location", Matchers.containsString("/books/42/reviews/84")));
  }

  @Test
  void shouldRejectNewBookReviewForAuthenticatedUsersWithInvalidPayload() throws Exception {
    // rating: -1 violates the @PositiveOrZero Bean Validation constraint on BookReviewRequest.
    // Spring MVC must respond with 400 Bad Request before the service is ever called.
    String requestBody =
        """
        {
          "reviewContent": "I really like this book!",
          "rating": -1
        }
        """;

    this.mockMvc
        .perform(
            post("/api/books/{isbn}/reviews", 42)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .claim("email", "duke@spring.io")
                                    .claim("preferred_username", "duke"))))
        .andExpect(status().isBadRequest())
        .andDo(MockMvcResultHandlers.print());
  }

  // ── Role-gated endpoint ───────────────────────────────────────────────────

  @Test
  void shouldNotAllowDeletingReviewsWhenUserIsAuthenticatedWithoutModeratorRole() throws Exception {
    // Authenticated but without ROLE_moderator → 403 Forbidden.
    // verifyNoInteractions() confirms security short-circuits before the service is reached.
    this.mockMvc
        .perform(delete("/api/books/{isbn}/reviews/{reviewId}", 42, 3).with(jwt()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(reviewService);
  }

  @Test
  void shouldAllowDeletingReviewsWhenUserIsAuthenticatedAndHasModeratorRole() throws Exception {
    // @WithMockUser does NOT work with STATELESS JWT security in Spring Security 7.
    // Reason: @WithMockUser populates the SecurityContext in the test thread, but the
    // STATELESS JWT filter chain never reads from SecurityContext — it reads the token.
    // Solution: inject the authority directly into the synthetic JWT via .jwt().authorities(...)
    this.mockMvc
        .perform(delete("/api/books/{isbn}/reviews/{reviewId}", 42, 3)
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_moderator"))))
        .andExpect(status().isOk());

    verify(reviewService).deleteReview("42", 3L);
  }
}
