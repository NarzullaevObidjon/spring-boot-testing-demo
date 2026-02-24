package jon.obid.testing.book.review;

/*
 * ── Phase 1: Pure Unit Test — JUnit 5 Feature Showcase ───────────────────────
 *
 *  Topic  : Testing a plain Java class with no external dependencies
 *  Layer  : Unit (no Spring context, no database, no network)
 *  Tools  : JUnit 5, Hamcrest, AssertJ, custom ParameterResolver extension
 *
 *  ReviewVerifier is a pure POJO — no Spring beans, no I/O — so we can
 *  instantiate it directly in @BeforeEach.  This is the fastest category of
 *  test: it starts in milliseconds and gives immediate feedback.
 *
 *  JUnit 5 features demonstrated:
 *   @Test              — the standard test method
 *   @DisplayName       — human-readable label shown in reports and IDEs
 *   @ParameterizedTest — run one test method with many different inputs
 *   @CsvFileSource     — load those inputs line-by-line from a CSV resource file
 *   @RepeatedTest      — run the same test N times (great with randomised data)
 *   @ExtendWith        — plug in a custom extension (parameter injection here)
 *   @BeforeEach        — setup that runs before every test method
 *
 *  Assertion libraries demonstrated (all produce the same pass/fail result,
 *  but differ in readability and the quality of failure messages):
 *   JUnit 5 Assertions — assertTrue / assertFalse    (built-in, simple)
 *   Hamcrest           — MatcherAssert.assertThat    (composable matcher DSL)
 *   AssertJ            — Assertions.assertThat       (fluent chaining, IDE-friendly)
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.util.List;

import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static jon.obid.testing.book.review.RandomReviewParameterResolverExtension.RandomReview;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Register our custom extension — enables @RandomReview parameter injection in this class
@ExtendWith(RandomReviewParameterResolverExtension.class)
class ReviewVerifierTest {

  private ReviewVerifier reviewVerifier;

  // @BeforeEach runs before every @Test / @ParameterizedTest / @RepeatedTest in this class
  @BeforeEach
  void setup() {
    reviewVerifier = new ReviewVerifier();
  }

  // ── Basic @Test ──────────────────────────────────────────────────────────────

  @Test
  void shouldFailWhenReviewContainsSwearWord() {
    String review = "This book is shit";
    System.out.println("Testing a review"); // visible in output to trace execution order

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    // JUnit 5 assertion — second argument is a failure message shown when the assertion fails
    assertFalse(result, "ReviewVerifier did not detect swear word");
  }

  // @DisplayName replaces the method name in test reports — useful for BDD-style descriptions
  @Test
  @DisplayName("Should fail when review contains 'lorem ipsum'")
  void testLoremIpsum() {
    String review =
        "Lorem ipsum dolor sit amet, consetetur sadipscing elitr,"
            + " sed diam nonumy eirmod tempor invidunt ut labore et "
            + "dolore magna aliquyam erat, sed diam voluptua. "
            + "At vero eos et accusam et justo duo dolores et ea rebum";

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    assertFalse(result, "ReviewVerifier did not detect lorem ipsum");
  }

  // ── @ParameterizedTest + @CsvFileSource ─────────────────────────────────────
  //
  // Runs once per line in /test/resources/badReview.csv.
  // Each CSV line becomes the 'review' argument — ideal for data-driven tests
  // without littering the test class with hard-coded string literals.

  @ParameterizedTest
  @CsvFileSource(resources = "/badReview.csv")
  void shouldFailWhenReviewIsOfBadQuality(String review) {
    boolean result = reviewVerifier.doesMeetQualityStandards(review);
    assertFalse(result, "ReviewVerifier did not detect bad review");
  }

  // ── @RepeatedTest + custom ParameterResolver ─────────────────────────────────
  //
  // Runs 5 times.  On each run, RandomReviewParameterResolverExtension injects
  // a different randomly selected review via the @RandomReview marker annotation.
  // Combining @RepeatedTest with random input is a lightweight fuzz-testing technique.

  @RepeatedTest(5)
  void shouldFailWhenRandomReviewQualityIsBad(@RandomReview String review) {
    System.out.println(review); // log which review was injected in this repetition

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    assertFalse(result, "ReviewVerifier did not detect random bad review");
  }

  // ── Assertion library comparison ─────────────────────────────────────────────
  //
  // All three tests below verify the same behaviour — a good review must pass.
  // They differ only in which assertion library is used, so you can compare
  // syntax and choose your preferred style.

  @Test
  void shouldPassWhenReviewIsGood() {
    String review =
        "I can totally recommend this book "
            + "who is interested in learning how to write Java code!";

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    // JUnit 5 style — simple and direct
    assertTrue(result, "ReviewVerifier did not pass a good review");
  }

  @Test
  void shouldPassWhenReviewIsGoodHamcrest() {
    // Hamcrest: assertThat(message, actual, matcher)
    // Matchers can be composed — hasSize, endsWith, anyOf, etc.
    // Failure messages read like: "Expected: a collection with size <5> but was: <3>"
    String review =
        "I can totally recommend this book "
            + "who is interested in learning how to write Java code!";

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    MatcherAssert.assertThat(
        "ReviewVerifier did not pass a good review", result, Matchers.equalTo(true));
    MatcherAssert.assertThat("Lorem ipsum", Matchers.endsWith("ipsum"));
    MatcherAssert.assertThat(List.of(1, 2, 3, 4, 5), Matchers.hasSize(5));
    MatcherAssert.assertThat(
        List.of(1, 2, 3, 4, 5), Matchers.anyOf(Matchers.hasSize(5), Matchers.emptyIterable()));
  }

  @Test
  void shouldPassWhenReviewIsGoodAssertJ() {
    // AssertJ: fluent chaining, rich IDE auto-complete, powerful collection assertions.
    // Chains read like English: assertThat(x).isTrue(), assertThat(list).contains(3)
    String review =
        "I can totally recommend this book "
            + "who is interested in learning how to write Java code!";

    boolean result = reviewVerifier.doesMeetQualityStandards(review);

    Assertions.assertThat(result)
        .withFailMessage("ReviewVerifier did not pass a good review")
        .isEqualTo(true)
        .isTrue();

    Assertions.assertThat(List.of(1, 2, 3, 4, 5)).hasSizeBetween(1, 10);
    Assertions.assertThat(List.of(1, 2, 3, 4, 5)).contains(3).isNotEmpty();
  }
}
