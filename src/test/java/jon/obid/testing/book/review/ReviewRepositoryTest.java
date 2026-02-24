package jon.obid.testing.book.review;

/*
 * ── Phase 2: @DataJpaTest — Testing the Persistence Layer in Isolation ────────
 *
 *  Topic  : Slice testing JPA repositories without loading the full application
 *  Layer  : Persistence (JPA/Hibernate) — no controllers, no services, no real HTTP
 *  Tools  : JUnit 5, Spring Data JPA, H2 (in-memory), P6Spy (SQL logger)
 *
 *  @DataJpaTest is a Spring Boot test slice that loads ONLY the persistence layer:
 *   - Spring Data repositories
 *   - JPA entities and the EntityManager
 *   - Hibernate DDL auto-creation (create-drop by default)
 *
 *  It does NOT load:
 *   - Controllers or services (swap those with @MockitoBean if needed)
 *   - Security configuration
 *   - The full application context
 *
 *  Database choices in this test:
 *   - H2 in-memory: ultra-fast, ephemeral — perfect for CI pipelines
 *   - P6Spy proxy: wraps the H2 driver and logs every SQL statement to stdout,
 *     letting you see exactly what Hibernate generates without enabling
 *     spring.jpa.show-sql (which only logs the JPQL, not the native SQL)
 *
 *  Transaction isolation:
 *   @DataJpaTest annotates every test method with @Transactional by default.
 *   After each test the transaction is ROLLED BACK, so tests are fully isolated:
 *   data saved in one test is invisible to all other tests.  The @BeforeEach
 *   assertion below confirms this guarantee holds.
 *
 *  @AutoConfigureTestDatabase(replace = NONE):
 *   By default, @DataJpaTest replaces your configured DataSource with its own
 *   embedded H2. Replace.NONE disables that substitution so our custom P6Spy URL
 *   is used instead.
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// spring.datasource.driver-class-name=P6SpyDriver  →  all SQL logged via P6Spy
// spring.datasource.url uses jdbc:p6spy:h2:... prefix — P6Spy delegates to H2 underneath
// @AutoConfigureTestDatabase(replace = NONE) prevents @DataJpaTest from swapping our DataSource
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
      "spring.datasource.url=jdbc:p6spy:h2:mem:testing;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewRepositoryTest {

  // Standard JPA EntityManager — useful for flushing or running JPQL directly
  @Autowired private EntityManager entityManager;

  // ReviewRepository is the class under test
  @Autowired private ReviewRepository cut;

  // DataSource — inspected here to confirm P6Spy / H2 is active
  @Autowired private DataSource dataSource;

  // TestEntityManager is a test-specific wrapper with additional helpers like persistAndFlush()
  @Autowired private TestEntityManager testEntityManager;

  @BeforeEach
  void beforeEach() {
    // Verifies that the @Transactional rollback between tests leaves the database empty.
    // If this assertion ever fails, a test is committing data outside its transaction.
    assertEquals(0, cut.count());
  }

  @Test
  void notNull() throws SQLException {
    // Smoke test — confirms all the injected dependencies are properly wired
    assertNotNull(entityManager);
    assertNotNull(cut);
    assertNotNull(testEntityManager);
    assertNotNull(dataSource);

    // Print the database product name — confirms P6Spy is wrapping H2 (not a real DB)
    System.out.println(dataSource.getConnection().getMetaData().getDatabaseProductName());

    // Persist a minimal Review and verify that the repository assigns an auto-generated ID
    Review review = new Review();
    review.setContent("Duke");
    review.setTitle("Review 101");
    review.setCreatedAt(LocalDateTime.now());
    review.setRating(5);
    review.setBook(null);
    review.setUser(null);

    Review result = cut.save(review);

    System.out.println(result);
    assertNotNull(result.getId()); // ID is assigned by the database sequence on INSERT
  }

  @Test
  void transactionalSupportTest() {
    // Demonstrates @DataJpaTest's built-in transaction rollback:
    // this review is saved within the current transaction, but the transaction is
    // rolled back after the test — so the next test still sees an empty database.
    Review review = new Review();
    review.setContent("Duke");
    review.setTitle("Review 101");
    review.setCreatedAt(LocalDateTime.now());
    review.setRating(5);
    review.setBook(null);
    review.setUser(null);

    cut.save(review);
    // No assertion needed — the point is to show the rollback guarantee (checked in @BeforeEach)
  }
}
