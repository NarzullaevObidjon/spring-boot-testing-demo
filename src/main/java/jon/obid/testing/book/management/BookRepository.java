package jon.obid.testing.book.management;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
  Book findByIsbn(String isbn);
}
