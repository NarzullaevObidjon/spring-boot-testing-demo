package jon.obid.testing.book.review;

/*
 * ── Phase 2: @DataJpaTest + Testcontainers — Real PostgreSQL in Tests ─────────
 *
 *  Topic  : Replacing H2 with a real database to test production-only SQL features
 *  Layer  : Persistence (JPA/Hibernate) — no controllers, no services
 *  Tools  : JUnit 5, Spring Data JPA, Testcontainers, PostgreSQL, @DynamicPropertySource
 *
 *  Why not always use H2?
 *   H2 is an excellent in-memory database, but it has gaps versus PostgreSQL:
 *   - Native SQL queries (e.g. window functions, RETURNING, jsonb) may not be supported
 *   - Dialect differences can hide bugs that only surface in production
 *   - Schema migrations (Flyway/Liquibase) should be tested against the real engine
 *
 *  Testcontainers solution:
 *   Testcontainers starts a real Docker container (PostgreSQL 17.2 here) before the
 *   test class runs and stops it after.  The container is declared as a static field
 *   so it is shared across all tests in this class — one container per class, not per test.
 *
 *  Key annotations:
 *   @Testcontainers(disabledWithoutDocker = true)
 *     — scans for @Container fields and manages their lifecycle.
 *       disabledWithoutDocker = true means the test is simply skipped (not failed)
 *       when Docker Desktop is not running — safe for machines without Docker.
 *
 *   @Container
 *     — tells Testcontainers which field holds the container to manage.
 *       Static field → shared container for all tests in the class.
 *
 *   @DynamicPropertySource
 *     — injects the container's JDBC URL, username, and password into Spring's
 *       Environment at test startup, BEFORE the application context is created.
 *       This is how we wire the runtime port (assigned by Docker) into Spring.
 *
 *   @Sql(scripts = "...")
 *     — executes the given SQL script before the annotated test method runs.
 *       Used here to populate the database with fixture data for statistics tests.
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

// @AutoConfigureTestDatabase(replace = NONE) prevents Spring Boot from swapping our
// PostgreSQL DataSource with its default embedded H2.
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewRepositoryNoInMemoryTest {

  // Static → one shared container for the entire test class (faster than per-test).
  // Testcontainers starts and stops it automatically around the class lifecycle.
  @Container
  static PostgreSQLContainer<?> container =
      new PostgreSQLContainer<>("postgres:17.2")
          .withDatabaseName("test")
          .withUsername("duke")
          .withPassword("s3cret");

  // Runs once before the Spring context is created — wires the container's runtime
  // coordinates into Spring's environment so DataSource picks them up automatically.
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", container::getJdbcUrl);
    registry.add("spring.datasource.password", container::getPassword);
    registry.add("spring.datasource.username", container::getUsername);
  }

  @Autowired private ReviewRepository cut;

  @Test
  // @Sql inserts fixture data before this test runs.
  // The script creates two books with reviews so getReviewStatistics() returns non-empty results.
  @Sql(scripts = "/scripts/INIT_REVIEW_EACH_BOOK.sql")
  void shouldGetTwoReviewStatisticsWhenDatabaseContainsTwoBooksWithReview() {

    List<ReviewStatistic> result = cut.getReviewStatistics();

    assertEquals(3, cut.count());  // 3 reviews total across 2 books
    assertEquals(2, result.size()); // 2 distinct book/statistic rows

    // Print each statistic for visibility during development
    result.forEach(
        reviewStatistic -> {
          System.out.println("ReviewStatistic");
          System.out.println(reviewStatistic.getId());
          System.out.println(reviewStatistic.getAvg());
          System.out.println(reviewStatistic.getIsbn());
          System.out.println(reviewStatistic.getRatings());
          System.out.println("");
        });

    // Verify the aggregated data for the first result row
    assertEquals(2, result.get(0).getRatings());             // 2 ratings for book id=2
    assertEquals(2, result.get(0).getId());                  // book id
    assertEquals(new BigDecimal("3.00"), result.get(0).getAvg()); // average rating
  }

  @Test
  void databaseShouldBeEmpty() {
    // @DataJpaTest wraps tests in a rolled-back transaction by default, so data from
    // the @Sql script in the previous test does NOT leak into this test.
    assertEquals(0, cut.count());
  }
}
