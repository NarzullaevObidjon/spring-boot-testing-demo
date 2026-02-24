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
 *   WebSecurityConfig requires a JwtDecoder bean (created from Keycloak issuer-uri).
 *   No real Keycloak runs during tests, so we mock JwtDecoder to let the context start.
 *
 *  Spring Boot 2.x vs 4.x:
 *   @MockBean (2.x/3.x) replaces @MockitoBean (4.x) — same behaviour, different name.
 *
 *  
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

  @MockBean
  JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {
    // No assertions — a BeanCreationException from any misconfigured bean
    // fails this test automatically with a precise, actionable error message.
  }
}
