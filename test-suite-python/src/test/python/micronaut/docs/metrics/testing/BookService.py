from io.micrometer.core.annotation import Timed
from io.micrometer.observation.annotation import Observed
from jakarta.inject import Singleton


@Singleton
class BookService:

    @Timed("books.find")
    @Observed(name="books.lookup")
    def find(self, title: str) -> str:
        return title
