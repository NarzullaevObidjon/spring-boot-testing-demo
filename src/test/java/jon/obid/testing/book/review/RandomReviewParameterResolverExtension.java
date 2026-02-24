package jon.obid.testing.book.review;

/*
 * ── Custom JUnit 5 ParameterResolver Extension ───────────────────────────────
 *
 *  Topic  : Extending JUnit 5 with custom parameter injection
 *  Tools  : JUnit 5 Extension API (ParameterResolver)
 *
 *  JUnit 5 lets you define ParameterResolver extensions that inject arbitrary
 *  values into test method parameters — no Spring context required.
 *
 *  How it works:
 *   1. Implement ParameterResolver — two methods to implement:
 *       - supportsParameter() → return true if this resolver owns the parameter
 *       - resolveParameter()  → return the actual value to inject
 *   2. Mark eligible parameters with a custom annotation (@RandomReview here)
 *   3. Register the extension on the test class via @ExtendWith(...)
 *
 *  This extension is used in ReviewVerifierTest to inject a random review string
 *  into @RepeatedTest methods, demonstrating custom extensions and randomised
 *  test input at the same time.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static java.lang.annotation.ElementType.PARAMETER;

public class RandomReviewParameterResolverExtension implements ParameterResolver {

  // Sample review pool — a mix of bad-quality and borderline reviews
  private static final List<String> badReviews =
      List.of(
          "This book was shit I don't like it",
          "I was reading the book and I think the book is okay. I have read better books and I think I know what's good",
          "Good book with good agenda and good example. I can recommend for everyone");

  /**
   * Marker annotation — place this on any String test method parameter to
   * receive a randomly selected review from the pool above.
   *
   * <p>Retention must be RUNTIME so JUnit 5 can inspect it via reflection.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(PARAMETER)
  public @interface RandomReview {}

  /** Returns true only for parameters annotated with @RandomReview. */
  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.isAnnotated(RandomReview.class);
  }

  /** Picks a random review from the pool on every invocation. */
  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return badReviews.get(ThreadLocalRandom.current().nextInt(0, badReviews.size()));
  }
}
