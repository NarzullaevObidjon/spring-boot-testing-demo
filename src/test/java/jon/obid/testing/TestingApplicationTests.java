package jon.obid.testing;

/*
 * ── Phase 3: @SpringBootTest — Full Application Context Integration Test ──────
 *
 *  Topic  : Verifying that the entire Spring application context starts correctly
 *  Layer  : Integration (full context) — loads all beans, configurations, and auto-configs
 *  Tools  : JUnit 5, Spring Boot Test, Testcontainers (via TestcontainersConfiguration)
 *
 *  @SpringBootTest loads the ENTIRE ApplicationContext, just like a production startup.
 *  If any bean fails to initialize, the test fails with a descriptive BeanCreationException.
 *
 *  The JwtDecoder problem:
 *   WebSecurityConfig configures a JWT resource server, which requires a JwtDecoder bean
 *   created from the Keycloak issuer-uri property.  During a full context test no real
 *   Keycloak runs, so Spring Boot cannot auto-configure JwtDecoder.
 *   Solution: @MockBean JwtDecoder — Spring replaces the auto-configured bean with a mock
 *   so the SecurityFilterChain starts without Keycloak.
 *
 *  Spring Boot 3.x vs 4.x:
 *   @MockBean (Boot 3.x) replaces @MockitoBean (Boot 4.x) — same behaviour, different name.
 *
 *  Author : Obidjon Sattarov <obidsattarovich3600@gmail.com>
 * ─────────────────────────────────────────────────────────────────────────────
 */

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TestingApplicationTests {

  // No real Keycloak runs during tests — mock JwtDecoder so the SecurityFilterChain starts.
  @MockBean
  JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {
    // No assertions needed: a BeanCreationException from any misconfigured bean
    // will fail this test automatically with a precise, actionable error message.
  }
}
