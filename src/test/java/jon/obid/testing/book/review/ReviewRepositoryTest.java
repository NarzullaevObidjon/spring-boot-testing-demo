package jon.obid.testing.book.review;

/*
 * ── Phase 2: @DataJpaTest — Testing the Persistence Layer in Isolation ────────
 *
 *  Topic  : Slice testing JPA repositories without loading the full application
 *  Layer  : Persistence (JPA/Hibernate) — no controllers, no services, no real HTTP
 *  Tools  : JUnit 5, Spring Data JPA, H2 (in-memory), P6Spy (SQL logger)
 *
 *  @DataJpaTest is a Spring Boot test slice that loads ONLY the persistence layer.
 *  P6Spy wraps the H2 driver and logs every SQL statement to stdout.
 *  @DataJpaTest wraps every test in a @Transactional block that rolls back after
 *  the test — the @BeforeEach assertion confirms this guarantee.
 *
 *  Spring Boot 2.x vs 4.x:
 *   Package paths moved in Boot 4.x:
 *     Boot 2.x: org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
 *     Boot 4.x: org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
 *   Here we use the Boot 2.x (and 3.x) location.
 *
 *   EntityManager uses javax.persistence in Boot 2.x (jakarta.persistence in Boot 3.x+).
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

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
