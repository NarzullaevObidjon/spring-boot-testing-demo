package jon.obid.testing.book.review;

/*
 * ── Phase 2: @WebMvcTest with JWT/OAuth2 Security Testing ────────────────────
 *
 *  Topic  : Testing authorization rules — public, authenticated, and role-gated endpoints
 *  Layer  : Web (Spring MVC) — no JPA, no real services, no real Keycloak
 *  Tools  : JUnit 5, MockMvc, Mockito, Spring Security test support (.with(jwt()))
 *
 *  Authorization levels tested:
 *   1. Public    — GET /api/books/reviews (no token required)
 *   2. Auth      — GET /api/books/reviews/statistics (any valid JWT)
 *   3. Auth      — POST /api/books/{isbn}/reviews (valid JWT + valid body)
 *   4. Role      — DELETE /api/books/{isbn}/reviews/{id} (ROLE_moderator required)
 *
 *  Key pattern — .with(jwt()):
 *   Injects a synthetic JWT token into the MockMvc request context.
 *   No real Keycloak needed — the token is created in-memory.
 *   Custom claims and authorities can be set via the builder API.
 *
 *  Why NOT @WithMockUser:
 *   @WithMockUser sets the SecurityContext in the test thread.
 *   With STATELESS JWT security, the filter chain reads from the JWT token, not the
 *   SecurityContext — so @WithMockUser always results in 401. Use .with(jwt()) instead.
 *
 *  Spring Boot 2.x vs 4.x:
 *   - @MockBean (2.x) replaces @MockitoBean (4.x) — same behaviour, different name
 *   - No @ImportAutoConfiguration needed — security is auto-included in 2.x slices
 *   - Jackson 2.x: com.fasterxml.jackson (not tools.jackson from Boot 4.x)
 *
 *  Author : Obidjon Sattarov <obidsattarovich3600@gmail.com>
 * ─────────────────────────────────────────────────────────────────────────────
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jon.obid.testing.config.WebSecurityConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import(WebSecurityConfig.class)
class ReviewControllerTest {

  @MockBean private ReviewService reviewService;
  @MockBean private JwtDecoder jwtDecoder;

  @Autowired private MockMvc mockMvc;

  private ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() {
    this.objectMapper = new ObjectMapper();
  }

  // ── Public endpoint ───────────────────────────────────────────────────────

  @Test
  void shouldReturnTwentyReviewsWithoutAnyOrderWhenNoParametersAreSpecified() throws Exception {
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
    this.mockMvc.perform(get("/api/books/reviews/statistics")).andExpect(status().isUnauthorized());

    verifyNoInteractions(reviewService);
  }

  @Test
  void shouldReturnReviewStatisticsWhenUserIsAuthenticated() throws Exception {
    this.mockMvc
        .perform(get("/api/books/reviews/statistics").with(jwt()))
        .andExpect(status().isOk());

    verify(reviewService).getReviewStatistics();
  }

  @Test
  void shouldCreateNewBookReviewForAuthenticatedUserWithValidPayload() throws Exception {
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
        .andExpect(header().exists("Location"))
        .andExpect(header().string("Location", Matchers.containsString("/books/42/reviews/84")));
  }

  @Test
  void shouldRejectNewBookReviewForAuthenticatedUsersWithInvalidPayload() throws Exception {
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
    this.mockMvc
        .perform(delete("/api/books/{isbn}/reviews/{reviewId}", 42, 3).with(jwt()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(reviewService);
  }

  @Test
  void shouldAllowDeletingReviewsWhenUserIsAuthenticatedAndHasModeratorRole() throws Exception {
    // @WithMockUser does NOT work with STATELESS JWT security — use .with(jwt()) instead.
    this.mockMvc
        .perform(delete("/api/books/{isbn}/reviews/{reviewId}", 42, 3)
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_moderator"))))
        .andExpect(status().isOk());

    verify(reviewService).deleteReview("42", 3L);
  }
}
