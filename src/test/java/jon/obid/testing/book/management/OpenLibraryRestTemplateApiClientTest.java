package jon.obid.testing.book.management;

/*
 * ── Phase 2: @RestClientTest — Testing a Synchronous RestTemplate Client ──────
 *
 *  Topic  : Slice testing an HTTP client that uses RestTemplate
 *  Layer  : Infrastructure (outbound HTTP) — minimal Spring context
 *  Tools  : JUnit 5, Spring @RestClientTest, MockRestServiceServer
 *
 *  MockRestServiceServer intercepts RestTemplate calls at the Spring level —
 *  no actual network activity occurs.
 *
 *  Spring Boot 2.x vs 4.x:
 *   RestTemplateAutoConfiguration registers RestTemplateBuilder in all versions,
 *   not a RestTemplate bean.  The @TestConfiguration inner class builds the
 *   RestTemplate so MockRestServiceServer can wire into it.
 *   Package change in Boot 4.x: RestTemplateBuilder moved to
 *     org.springframework.boot.restclient.RestTemplateBuilder
 *   Here we use the Boot 2.x / 3.x location:
 *     org.springframework.boot.web.client.RestTemplateBuilder
 *
 *  Author : Obidjon Sattarov <obidsattarovich3600@gmail.com>
 * ─────────────────────────────────────────────────────────────────────────────
 */

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
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

  @TestConfiguration
  static class RestTemplateConfig {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
      return builder.build();
    }
  }

  @Autowired private OpenLibraryRestTemplateApiClient cut;
  @Autowired private MockRestServiceServer mockRestServiceServer;

  private static final String ISBN = "9780596004651";

  @Test
  void shouldInjectBeans() {
    assertNotNull(cut);
    assertNotNull(mockRestServiceServer);
  }

  @Test
  void shouldReturnBookWhenResultIsSuccess() {
    this.mockRestServiceServer
        .expect(requestTo(Matchers.containsString(ISBN)))
        .andRespond(
            withSuccess(
                new ClassPathResource("/stubs/openlibrary/success-" + ISBN + ".json"),
                MediaType.APPLICATION_JSON));

    Book result = cut.fetchMetadataForBook(ISBN);

    assertEquals("9780596004651", result.getIsbn());
    assertEquals("Head first Java", result.getTitle());
    assertEquals("https://covers.openlibrary.org/b/id/388761-S.jpg", result.getThumbnailUrl());
    assertEquals("Kathy Sierra", result.getAuthor());
    assertEquals("Your brain on Java--a learner's guide--Cover.Includes index.", result.getDescription());
    assertEquals("Java (Computer program language)", result.getGenre());
    assertEquals("O'Reilly", result.getPublisher());
    assertEquals(619, result.getPages());
    assertNull(result.getId());
  }

  @Test
  void shouldReturnBookWhenResultIsSuccessButLackingAllInformation() {

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

    assertEquals("n.A", result.getDescription());
    assertEquals("n.A", result.getGenre());
    assertEquals(42, result.getPages());
    assertNull(result.getId());
  }

  @Test
  void shouldPropagateExceptionWhenRemoteSystemIsDown() {
    assertThrows(
        HttpServerErrorException.class,
        () -> {
          this.mockRestServiceServer
              .expect(requestTo(Matchers.containsString("/api/books")))
              .andRespond(MockRestResponseCreators.withServerError());

          cut.fetchMetadataForBook(ISBN);
        });
  }

  @Test
  void shouldContainCorrectHeadersWhenRemoteSystemIsInvoked() {
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
