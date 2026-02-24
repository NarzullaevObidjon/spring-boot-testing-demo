package jon.obid.testing.book.management;

/*
 * ── Phase 2: @RestClientTest — Testing a Synchronous RestTemplate Client ──────
 *
 *  Topic  : Slice testing an HTTP client that uses RestTemplate
 *  Layer  : Infrastructure (outbound HTTP) — minimal Spring context
 *  Tools  : JUnit 5, Spring @RestClientTest, MockRestServiceServer
 *
 *  @RestClientTest is the synchronous counterpart to MockWebServer.
 *  Instead of starting a real local HTTP server, it intercepts RestTemplate calls
 *  at the Spring level — no actual network activity occurs at all.
 *
 *  How it works:
 *   1. @RestClientTest loads only the target client bean and auto-configures
 *      MockRestServiceServer as a Spring bean in the test context.
 *   2. MockRestServiceServer installs itself as a ClientHttpRequestFactory
 *      inside the RestTemplate, intercepting every outgoing request.
 *   3. We declare expectations with .expect(...) and configure what the
 *      mock server should respond with using .andRespond(...).
 *   4. After the test, the server verifies that all expected requests were made.
 *
 *  Spring Boot 4.x change — RestTemplate is no longer auto-registered:
 *   RestTemplateAutoConfiguration in Boot 4.x only registers RestTemplateBuilder,
 *   not a RestTemplate bean.  @RestClientTest needs a RestTemplate bean in the
 *   context to wire up MockRestServiceServer.
 *   Solution: declare an inner @TestConfiguration that builds a RestTemplate from
 *   the injected RestTemplateBuilder — Boot then wires MockRestServiceServer into it.
 *
 *  MockRestServiceServer API:
 *   .expect(requestTo(...))          — assert the request URL matches a pattern
 *   .andExpect(header("key","val"))  — assert a request header value
 *   .andRespond(withSuccess(...))    — respond with 200 + the given body
 *   .andRespond(withServerError())   — respond with 500
 *
 *  Stub files:
 *   JSON stubs live in src/test/resources/stubs/openlibrary/ and are loaded via
 *   ClassPathResource so they are read from the test classpath automatically.
 *
 *  
 * ─────────────────────────────────────────────────────────────────────────────
 */

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(OpenLibraryRestTemplateApiClient.class)
class OpenLibraryRestTemplateApiClientTest {

  // Spring Boot 4.x: RestTemplateAutoConfiguration only registers RestTemplateBuilder.
  // We must provide a RestTemplate bean explicitly so MockRestServiceServer can wire into it.
  @TestConfiguration
  static class RestTemplateConfig {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
      return builder.build();
    }
  }

  // Class under test — Spring wires this with the mock-intercepted RestTemplate
  @Autowired private OpenLibraryRestTemplateApiClient cut;

  // Auto-configured by @RestClientTest — intercepts all RestTemplate calls
  @Autowired private MockRestServiceServer mockRestServiceServer;

  private static final String ISBN = "9780596004651";

  // ── Smoke test ────────────────────────────────────────────────────────────

  @Test
  void shouldInjectBeans() {
    assertNotNull(cut);
    assertNotNull(mockRestServiceServer);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void shouldReturnBookWhenResultIsSuccess() {
    // Load the JSON stub from the classpath and respond with it as application/json
    this.mockRestServiceServer
        .expect(requestTo(Matchers.containsString(ISBN)))
        .andRespond(
            withSuccess(
                new ClassPathResource("/stubs/openlibrary/success-" + ISBN + ".json"),
                MediaType.APPLICATION_JSON));

    Book result = cut.fetchMetadataForBook(ISBN);

    // Verify all fields are correctly parsed from the stub JSON
    assertEquals("9780596004651", result.getIsbn());
    assertEquals("Head first Java", result.getTitle());
    assertEquals("https://covers.openlibrary.org/b/id/388761-S.jpg", result.getThumbnailUrl());
    assertEquals("Kathy Sierra", result.getAuthor());
    assertEquals("Your brain on Java--a learner's guide--Cover.Includes index.", result.getDescription());
    assertEquals("Java (Computer program language)", result.getGenre());
    assertEquals("O'Reilly", result.getPublisher());
    assertEquals(619, result.getPages());
    assertNull(result.getId()); // ID is assigned by the DB, not by the API client
  }

  @Test
  void shouldReturnBookWhenResultIsSuccessButLackingAllInformation() {
    // JSON missing the optional 'notes' (description) and 'subjects' (genre) fields.
    // The client must gracefully fall back to the sentinel value "n.A" for both.
    String response =
        """
        {
          "9780596004651": {
            "publishers": [{ "name": "O'Reilly" }],
            "title": "Head second Java",
            "authors": [{ "url": "https://openlibrary.org/authors/OL1400543A/Kathy_Sierra", "name": "Kathy Sierra" }],
            "number_of_pages": 42,
            "cover": {
              "small": "https://covers.openlibrary.org/b/id/388761-S.jpg",
              "large": "https://covers.openlibrary.org/b/id/388761-L.jpg",
              "medium": "https://covers.openlibrary.org/b/id/388761-M.jpg"
            }
          }
        }
        """;

    this.mockRestServiceServer
        .expect(requestTo(Matchers.containsString("/api/books")))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    Book result = cut.fetchMetadataForBook(ISBN);

    assertEquals("n.A", result.getDescription()); // fallback — 'notes' field was absent
    assertEquals("n.A", result.getGenre());       // fallback — 'subjects' field was absent
    assertEquals(42, result.getPages());
    assertNull(result.getId());
  }

  // ── Error path ────────────────────────────────────────────────────────────

  @Test
  void shouldPropagateExceptionWhenRemoteSystemIsDown() {
    // withServerError() responds with 500 — the RestTemplate must throw HttpServerErrorException.
    // This verifies the client does not silently swallow server-side failures.
    assertThrows(
        HttpServerErrorException.class,
        () -> {
          this.mockRestServiceServer
              .expect(requestTo(Matchers.containsString("/api/books")))
              .andRespond(MockRestResponseCreators.withServerError());

          cut.fetchMetadataForBook(ISBN);
        });
  }

  // ── Request header verification ───────────────────────────────────────────

  @Test
  void shouldContainCorrectHeadersWhenRemoteSystemIsInvoked() {
    // Verifies that OpenLibraryRestTemplateApiClient sends the required custom authentication
    // headers on every request — a common contract enforced by third-party APIs.
    this.mockRestServiceServer
        .expect(requestTo(Matchers.containsString("/api/books")))
        .andExpect(MockRestRequestMatchers.header("X-Custom-Auth", "Duke42"))
        .andExpect(MockRestRequestMatchers.header("X-Customer-Id", "42"))
        .andRespond(
            withSuccess(
                new ClassPathResource("/stubs/openlibrary/success-" + ISBN + ".json"),
                MediaType.APPLICATION_JSON));

    Book result = cut.fetchMetadataForBook(ISBN);

    assertNotNull(result);
  }
}
