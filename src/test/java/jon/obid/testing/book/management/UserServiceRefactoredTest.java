package jon.obid.testing.book.management;

/*
 * ── Phase 1: Clock Injection — The Clean Way to Mock Time ────────────────────
 *
 *  Topic  : Controlling time in tests through dependency injection
 *  Layer  : Unit (no Spring context)
 *  Tools  : JUnit 5, Mockito, java.time.Clock
 *
 *  This test is the preferred alternative to UserServiceTest.
 *
 *  The problem with static mocking (UserServiceTest approach):
 *   - Requires byte-code instrumentation — slow and thread-unsafe
 *   - Couples production code to a static, un-injectable dependency
 *
 *  The solution — inject java.time.Clock as a constructor dependency:
 *   1. UserServiceRefactored receives a Clock bean instead of calling
 *      LocalDateTime.now() directly
 *   2. In production, Spring provides Clock.systemDefaultZone() (a real clock)
 *   3. In tests, we @Mock the Clock and make it return any instant we choose
 *
 *  Result: no agents, no instrumentation, fully thread-safe, and the test reads
 *  like plain Mockito stubbing — which it is.
 *
 *  Rule of thumb: if you need to control time in a test, always prefer
 *  Clock injection over static mocking. The same principle applies to any other
 *  "environmental" value (random numbers, UUIDs, system properties).
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRefactoredTest {

  // Clock is now a regular @Mock — no byte-code tricks needed
  @Mock private Clock clock;

  @Mock private UserRepository userRepository;

  @InjectMocks private UserServiceRefactored cut;

  @Test
  void shouldIncludeCurrentDateTimeWhenCreatingNewUser() {

    when(userRepository.findByNameAndEmail("duke", "duke@spring.io")).thenReturn(null);
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> {
          User user = invocation.getArgument(0);
          user.setId(1L);
          return user;
        });

    LocalDateTime fixedDateTime = LocalDateTime.of(2020, 1, 1, 12, 0);

    // Clock.fixed() creates an immutable clock that always returns the same instant.
    // We then stub our mock Clock to mimic it — two stubs cover what Clock needs:
    //   instant()  → the point in time
    //   getZone()  → the timezone used when converting that instant to LocalDateTime
    Clock fixedClock = Clock.fixed(fixedDateTime.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
    when(clock.instant()).thenReturn(fixedClock.instant());
    when(clock.getZone()).thenReturn(fixedClock.getZone());

    User result = cut.getOrCreateUser("duke", "duke@spring.io");

    assertEquals(fixedDateTime, result.getCreatedAt());
  }
}
