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
 *   - Controllers or services
 *   - Security configuration
 *   - The full application context
 *
 *  P6Spy proxy: wraps the H2 driver and logs every SQL statement to stdout,
 *  letting you see exactly what Hibernate generates without enabling
 *  spring.jpa.show-sql (which only logs JPQL, not native SQL).
 *
 *  Transaction isolation:
 *   @DataJpaTest wraps every test in a @Transactional block that ROLLS BACK after
 *   the test, so no data leaks between tests.  The @BeforeEach assertion confirms
 *   this guarantee.
 *
 *  @AutoConfigureTestDatabase(replace = NONE):
 *   Prevents @DataJpaTest from swapping the configured DataSource with its own H2,
 *   so our custom P6Spy URL is used instead.
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
      "spring.datasource.url=jdbc:p6spy:h2:mem:testing;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private ReviewRepository cut;
  @Autowired private DataSource dataSource;
  @Autowired private TestEntityManager testEntityManager;

  @BeforeEach
  void beforeEach() {
    assertEquals(0, cut.count());
  }

  @Test
  void notNull() throws SQLException {
    assertNotNull(entityManager);
    assertNotNull(cut);
    assertNotNull(testEntityManager);
    assertNotNull(dataSource);

    System.out.println(dataSource.getConnection().getMetaData().getDatabaseProductName());

    Review review = new Review();
    review.setContent("Duke");
    review.setTitle("Review 101");
    review.setCreatedAt(LocalDateTime.now());
    review.setRating(5);
    review.setBook(null);
    review.setUser(null);

    Review result = cut.save(review);

    System.out.println(result);
    assertNotNull(result.getId());
  }

  @Test
  void transactionalSupportTest() {
    // Saved here, but rolled back after the test — next test still sees an empty database.
    Review review = new Review();
    review.setContent("Duke");
    review.setTitle("Review 101");
    review.setCreatedAt(LocalDateTime.now());
    review.setRating(5);
    review.setBook(null);
    review.setUser(null);

    cut.save(review);
  }
}
