package jon.obid.testing.book.management;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("default")
public class InitialBookCreator {

    private static final Logger LOG = LoggerFactory.getLogger(InitialBookCreator.class.getName());

    private final BookRepository bookRepository;
    private final OpenLibraryApiClient openLibraryApiClient;

    public InitialBookCreator(BookRepository bookRepository, OpenLibraryApiClient openLibraryApiClient) {
        this.bookRepository = bookRepository;
        this.openLibraryApiClient = openLibraryApiClient;
    }

    @EventListener
    public void initialize(ApplicationReadyEvent event) {
        LOG.info("InitialBookCreator running ...");
        if (bookRepository.count() == 0) {
            LOG.info("Going to initialize first set of books");
            for (String isbn : List.of("9780321751041", "9780321160768", "9780596004651")) {
                try {
                    Book book = openLibraryApiClient.fetchMetadataForBook(isbn);
                    bookRepository.save(book);
                    LOG.info("Saved book with ISBN: {}", isbn);
                } catch (Exception e) {
                    LOG.error("Failed to fetch book with ISBN: {}", isbn, e);
                }
            }
        } else {
            LOG.info("No need to pre-populate books as database already contains some");
        }
    }
}
