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
 *  Testcontainers starts a real Docker container (PostgreSQL 17.2) before the test
 *  class runs and stops it after.  The container is declared as a static field so it
 *  is shared across all tests in the class — one container per class, not per test.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewRepositoryNoInMemoryTest {

  @Container
  static PostgreSQLContainer<?> container =
      new PostgreSQLContainer<>("postgres:17.2")
          .withDatabaseName("test")
          .withUsername("duke")
          .withPassword("s3cret");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", container::getJdbcUrl);
    registry.add("spring.datasource.password", container::getPassword);
    registry.add("spring.datasource.username", container::getUsername);
  }

  @Autowired private ReviewRepository cut;

  @Test
  @Sql(scripts = "/scripts/INIT_REVIEW_EACH_BOOK.sql")
  void shouldGetTwoReviewStatisticsWhenDatabaseContainsTwoBooksWithReview() {

    List<ReviewStatistic> result = cut.getReviewStatistics();

    assertEquals(3, cut.count());
    assertEquals(2, result.size());

    result.forEach(
        reviewStatistic -> {
          System.out.println("ReviewStatistic");
          System.out.println(reviewStatistic.getId());
          System.out.println(reviewStatistic.getAvg());
          System.out.println(reviewStatistic.getIsbn());
          System.out.println(reviewStatistic.getRatings());
          System.out.println();
        });

    assertEquals(2, result.get(0).getRatings());
    assertEquals(2, result.get(0).getId());
    assertEquals(new BigDecimal("3.00"), result.get(0).getAvg());
  }

  @Test
  void databaseShouldBeEmpty() {
    assertEquals(0, cut.count());
  }
}
