package jon.obid.testing.book.management;

/*
 * ── Phase 1: Static Method Mocking with Mockito.mockStatic ───────────────────
 *
 *  Topic  : Mocking static methods — when you can't change the production code
 *  Layer  : Unit (no Spring context)
 *  Tools  : JUnit 5, Mockito (MockedStatic)
 *
 *  UserService calls LocalDateTime.now() directly — a static method.
 *  Static methods cannot be intercepted by standard Mockito mocks because mocks
 *  work through subclassing, and you can't override a static method that way.
 *
 *  Mockito.mockStatic() solves this by using byte-code instrumentation to
 *  intercept static calls within the scope of a try-with-resources block.
 *  Once the block exits, the original static method is fully restored.
 *
 *  WARNING — when to avoid this approach:
 *   - mockStatic() requires a Mockito agent and can be slow in large test suites
 *   - It is thread-unsafe — parallel tests on the same static method will conflict
 *   - It signals that the production code is hard to test by design
 *
 *  PREFERRED ALTERNATIVE → see UserServiceRefactoredTest:
 *   Inject java.time.Clock as a Spring bean so time can be controlled via a
 *   simple @Mock — no byte-code tricks, fully thread-safe, and much simpler.
 *
 *  Author : Obidjon Sattarov <obidsattarovich3600@gmail.com>
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserService cut;

  @Test
  void shouldIncludeCurrentDateTimeWhenCreatingNewUser() {

    when(userRepository.findByNameAndEmail("duke", "duke@spring.io")).thenReturn(null);
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> {
          User user = invocation.getArgument(0);
          user.setId(1L);
          return user;
        });

    LocalDateTime defaultLocalDateTime = LocalDateTime.of(2020, 1, 1, 12, 0);

    System.out.println("Before mock: " + LocalDateTime.now()); // real wall-clock time

    // MockedStatic intercepts all LocalDateTime.now() calls inside the try block.
    // As soon as the try block ends, the real implementation is restored automatically.
    try (MockedStatic<LocalDateTime> mockedLocalDateTime =
        Mockito.mockStatic(LocalDateTime.class)) {

      mockedLocalDateTime.when(LocalDateTime::now).thenReturn(defaultLocalDateTime);

      System.out.println("Inside mock: " + LocalDateTime.now()); // always 2020-01-01T12:00

      User result = cut.getOrCreateUser("duke", "duke@spring.io");

      assertEquals(defaultLocalDateTime, result.getCreatedAt());
    }

    System.out.println("After mock: " + LocalDateTime.now()); // real wall-clock time again
  }
}
