package jon.obid.testing.book.review;

/*
 * ── Phase 1: Mockito Unit Test — Mocking Dependencies ────────────────────────
 *
 *  Topic  : Isolating the class under test by replacing its dependencies with mocks
 *  Layer  : Unit (no Spring context — pure Java + Mockito)
 *  Tools  : JUnit 5, Mockito (via MockitoExtension)
 *
 *  ReviewService depends on four collaborators: ReviewVerifier, UserService,
 *  BookRepository, and ReviewRepository.  In a unit test we do NOT want to start
 *  a database or a Spring context — we replace every collaborator with a Mockito
 *  mock and control exactly what each one returns.
 *
 *  Mockito annotations used:
 *   @Mock         — creates a mock (stub) of the annotated type
 *   @InjectMocks  — creates the class under test and injects all @Mock fields
 *                   into it via constructor, setter, or field injection
 *
 *  Mockito patterns demonstrated:
 *   when(...).thenReturn(...)    — stub a method to return a fixed value
 *   when(...).thenAnswer(...)    — stub with a lambda that receives the actual
 *                                  call arguments (useful for simulating ID assignment)
 *   assertThrows(...)            — verify that the code throws the expected exception
 *   verify(..., times(0)).save() — assert that a method was NEVER called
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */

import jon.obid.testing.book.management.Book;
import jon.obid.testing.book.management.BookRepository;
import jon.obid.testing.book.management.User;
import jon.obid.testing.book.management.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  // Each @Mock field is automatically created as a Mockito mock before each test
  @Mock private ReviewVerifier mockedReviewVerifier;
  @Mock private UserService userService;
  @Mock private BookRepository bookRepository;
  @Mock private ReviewRepository reviewRepository;

  // @InjectMocks creates ReviewService and injects the four mocks above into it
  @InjectMocks private ReviewService cut; // cut = class under test

  // Shared test data — defined as constants to keep tests DRY and readable
  private static final String EMAIL    = "duke@spring.io";
  private static final String USERNAME = "duke";
  private static final String ISBN     = "42";

  @Test
  void shouldNotBeNull() {
    // Smoke test — confirms @Mock and @InjectMocks wiring works as expected
    assertNotNull(reviewRepository);
    assertNotNull(mockedReviewVerifier);
    assertNotNull(userService);
    assertNotNull(bookRepository);
    assertNotNull(cut);
  }

  @Test
  @DisplayName("Should throw exception when the reviewed book does not exist")
  void shouldThrowExceptionWhenReviewedBookIsNotExisting() {
    // Stub: bookRepository returns null → book not found in the database
    when(bookRepository.findByIsbn(ISBN)).thenReturn(null);

    // ReviewService must throw IllegalArgumentException when the book is missing
    assertThrows(
        IllegalArgumentException.class, () -> cut.createBookReview(ISBN, null, USERNAME, EMAIL));
  }

  @Test
  void shouldRejectReviewWhenReviewQualityIsBad() {
    // Arrange — set up the scenario
    BookReviewRequest bookReviewRequest = new BookReviewRequest("Title", "BADCONTENT!", 1);
    when(bookRepository.findByIsbn(ISBN)).thenReturn(new Book());
    when(mockedReviewVerifier.doesMeetQualityStandards(bookReviewRequest.getReviewContent()))
        .thenReturn(false); // simulate: verifier rejects the content

    // Act & Assert — the service must throw and must NOT persist anything
    assertThrows(
        BadReviewQualityException.class,
        () -> cut.createBookReview(ISBN, bookReviewRequest, USERNAME, EMAIL));

    // verify(mock, times(0)) asserts the method was never called — critical here
    // because saving a bad review to the database would be a data-integrity bug
    verify(reviewRepository, times(0)).save(ArgumentMatchers.any(Review.class));
  }

  @Test
  void shouldStoreReviewWhenReviewQualityIsGoodAndBookIsPresent() {
    BookReviewRequest bookReviewRequest = new BookReviewRequest("Title", "GOOD CONTENT!", 1);

    when(bookRepository.findByIsbn(ISBN)).thenReturn(new Book());
    when(mockedReviewVerifier.doesMeetQualityStandards(bookReviewRequest.getReviewContent()))
        .thenReturn(true);
    when(userService.getOrCreateUser(USERNAME, EMAIL)).thenReturn(new User());

    // thenAnswer gives us access to the actual argument passed to save().
    // We use it to simulate the database assigning an auto-generated ID,
    // so the return value of createBookReview() can be verified.
    when(reviewRepository.save(any(Review.class)))
        .thenAnswer(invocation -> {
          Review reviewToSave = invocation.getArgument(0);
          reviewToSave.setId(42L);
          return reviewToSave;
        });

    Long result = cut.createBookReview(ISBN, bookReviewRequest, USERNAME, EMAIL);

    assertEquals(42L, result);
  }
}
