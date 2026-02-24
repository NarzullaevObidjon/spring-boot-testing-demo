package jon.obid.testing.book.management;

/*
 * ── Phase 2: MockWebServer — Testing a Reactive WebClient Without Spring ──────
 *
 *  Topic  : Testing an HTTP client by starting a real local server
 *  Layer  : Infrastructure (outbound HTTP) — no Spring context
 *  Tools  : JUnit 5, OkHttp MockWebServer, Spring WebClient, Reactor Netty
 *
 *  The problem with mocking WebClient directly:
 *   WebClient has a deeply fluent API (builder → spec → response mono → body).
 *   Mocking every intermediate step produces brittle, unreadable tests that break
 *   whenever the production code adds a single operator to the reactive chain.
 *
 *  The MockWebServer approach:
 *   MockWebServer (from OkHttp) starts a real HTTP server on localhost on a random
 *   port.  The client under test connects to it just like to a real remote API.
 *   We enqueue pre-canned responses and inspect recorded requests after the fact.
 *
 *  Benefits over WebClient mocking:
 *   - Tests the full reactive pipeline (serialization, headers, timeout, retry)
 *   - Request assertions are possible: path, query params, headers
 *   - No Spring context needed — starts in milliseconds
 *   - No byte-code instrumentation — fully thread-safe
 *
 *  Retry logic test:
 *   OpenLibraryApiClient is configured with up to 2 retries.
 *   The shouldRetryWhenRemoteSystemIsSlowOrFailing test validates all three cases:
 *    Attempt 1 — 500 Internal Server Error      → retry
 *    Attempt 2 — 200 OK but body delayed 2 s    → exceeds 1 s read timeout → retry
 *    Attempt 3 — 200 OK immediately             → success
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

// No Spring context — plain Java test, starts and stops MockWebServer around each test
class OpenLibraryApiClientTest {

  private MockWebServer mockWebServer;
  private OpenLibraryApiClient cut;

  private static final String ISBN = "9780596004651";

  // Load the JSON stub from test/resources/stubs/openlibrary/ at class-load time.
  // Reading once in a static initializer avoids repeated I/O across test runs.
  private static String VALID_RESPONSE;

  static {
    try {
      VALID_RESPONSE =
          new String(
              OpenLibraryApiClientTest.class
                  .getClassLoader()
                  .getResourceAsStream("stubs/openlibrary/success-" + ISBN + ".json")
                  .readAllBytes());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @BeforeEach
  void setup() throws IOException {
    // Configure Netty with short timeouts so "slow server" tests complete quickly.
    // CONNECT_TIMEOUT_MILLIS: max time to establish the TCP connection.
    // ReadTimeoutHandler(1):  max time to wait for data after the connection is open.
    // WriteTimeoutHandler(1): max time to flush a write to the server.
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_000)
            .doOnConnected(
                connection ->
                    connection
                        .addHandlerLast(new ReadTimeoutHandler(1))
                        .addHandlerLast(new WriteTimeoutHandler(1)));

    this.mockWebServer = new MockWebServer();
    this.mockWebServer.start(); // binds to a random free port on localhost

    // Point the client under test at the MockWebServer's base URL.
    // All requests the client makes will be captured by the MockWebServer.
    this.cut =
        new OpenLibraryApiClient(
            WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(mockWebServer.url("/").toString())
                .build());
  }

  @AfterEach
  void shutdown() throws IOException {
    // Release the port — always run even when the test fails
    this.mockWebServer.shutdown();
  }

  // ── Smoke test ────────────────────────────────────────────────────────────

  @Test
  void notNull() {
    assertNotNull(cut);
    assertNotNull(mockWebServer);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void shouldReturnBookWhenResultIsSuccess() throws InterruptedException {
    // Enqueue a response — MockWebServer returns it on the next incoming request
    MockResponse mockResponse =
        new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(VALID_RESPONSE);

    this.mockWebServer.enqueue(mockResponse);

    Book result = cut.fetchMetadataForBook(ISBN);

    // Verify every field parsed from the JSON stub
    assertEquals("9780596004651", result.getIsbn());
    assertEquals("Head first Java", result.getTitle());
    assertEquals("https://covers.openlibrary.org/b/id/388761-S.jpg", result.getThumbnailUrl());
    assertEquals("Kathy Sierra", result.getAuthor());
    assertEquals("Your brain on Java--a learner's guide--Cover.Includes index.", result.getDescription());
    assertEquals("Java (Computer program language)", result.getGenre());
    assertEquals("O'Reilly", result.getPublisher());
    assertEquals(619, result.getPages());
    assertNull(result.getId()); // ID is assigned by the DB, not by the API client

    // takeRequest() dequeues the request the client actually sent — allows path/header assertions
    RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
    assertEquals("/api/books?jscmd=data&format=json&bibkeys=" + ISBN, recordedRequest.getPath());
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

    this.mockWebServer.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(200)
            .setBody(response));

    Book result = cut.fetchMetadataForBook(ISBN);

    assertEquals("9780596004651", result.getIsbn());
    assertEquals("Head second Java", result.getTitle());
    assertEquals("n.A", result.getDescription()); // fallback — 'notes' field was absent
    assertEquals("n.A", result.getGenre());       // fallback — 'subjects' field was absent
    assertEquals(42, result.getPages());
    assertNull(result.getId());
  }

  // ── Error paths ───────────────────────────────────────────────────────────

  @Test
  void shouldPropagateExceptionWhenRemoteSystemIsDown() {
    // A 500 response with no retries left must surface as a RuntimeException.
    // This verifies that errors are not silently swallowed by the reactive chain.
    assertThrows(
        RuntimeException.class,
        () -> {
          this.mockWebServer.enqueue(
              new MockResponse().setResponseCode(500).setBody("Sorry, system is down :("));

          cut.fetchMetadataForBook(ISBN);
        });
  }

  @Test
  void shouldRetryWhenRemoteSystemIsSlowOrFailing() {
    // Validates the retry chain configured in OpenLibraryApiClient (max 2 retries):
    //
    //  Attempt 1: 500 Internal Server Error   → triggers retry
    //  Attempt 2: 200 OK with a 2-second body delay
    //             The 1-second ReadTimeout fires first → triggers retry
    //  Attempt 3: 200 OK immediately          → success; Book is returned
    this.mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody("Sorry, system is down :("));

    this.mockWebServer.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(200)
            .setBody(VALID_RESPONSE)
            .setBodyDelay(2, TimeUnit.SECONDS)); // exceeds the 1 s read timeout

    this.mockWebServer.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(200)
            .setBody(VALID_RESPONSE));

    Book result = cut.fetchMetadataForBook(ISBN);

    assertEquals("9780596004651", result.getIsbn());
    assertNull(result.getId());
  }
}
