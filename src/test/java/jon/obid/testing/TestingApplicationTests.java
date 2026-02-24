package jon.obid.testing;

/*
 * ── Phase 3: @SpringBootTest — Full Application Context Integration Test ──────
 *
 *  Topic  : Verifying that the entire Spring application context starts correctly
 *  Layer  : Integration (full context) — loads all beans, configurations, and auto-configs
 *  Tools  : JUnit 5, Spring Boot Test, Testcontainers (via TestcontainersConfiguration)
 *
 *  @SpringBootTest differs from slice tests (@WebMvcTest, @DataJpaTest, etc.):
 *   - It loads the ENTIRE ApplicationContext, just like a production startup.
 *   - All @Component, @Service, @Repository, @Controller, and @Configuration
 *     beans are instantiated and wired together.
 *   - If any bean fails to initialize, the test fails with a clear error pointing
 *     to the broken bean — invaluable for catching misconfigured properties,
 *     missing dependencies, or circular references.
 *
 *  Testcontainers integration:
 *   @Import(TestcontainersConfiguration.class) brings in service containers
 *   (e.g. PostgreSQL, Keycloak) needed for a full context startup.
 *   The containers are started before Spring creates the ApplicationContext,
 *   and their connection details are injected via @DynamicPropertySource.
 *
 *  The JwtDecoder problem:
 *   WebSecurityConfig configures a JWT resource server, which requires a JwtDecoder
 *   bean created from the Keycloak issuer-uri property.  During a full context test
 *   no real Keycloak runs, so Spring Boot cannot auto-configure the JwtDecoder.
 *   Solution: declare a @MockitoBean JwtDecoder — Spring replaces the auto-configured
 *   bean with a Mockito mock, so the SecurityFilterChain can start without Keycloak.
 *
 *  Rule of thumb:
 *   Keep the number of @SpringBootTest classes small — each one starts a full context
 *   and is expensive.  Use slice tests for focused testing; use @SpringBootTest only
 *   to verify that all the pieces fit together at startup.
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TestingApplicationTests {

  // No real Keycloak runs during tests, so JwtDecoder cannot be auto-configured from issuer-uri.
  // @MockitoBean registers a Mockito mock in the context — the SecurityFilterChain starts normally
  // without requiring a live identity provider.
  @MockitoBean
  JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {
    // This test has no assertions.  If the Spring context fails to start for any reason
    // (missing bean, bad configuration, circular dependency), the test fails with a
    // descriptive BeanCreationException that pinpoints the exact misconfiguration.
  }
}
