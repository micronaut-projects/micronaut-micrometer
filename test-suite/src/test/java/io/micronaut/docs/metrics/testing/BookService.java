package io.micronaut.docs.metrics.testing;

import io.micrometer.core.annotation.Timed;
import io.micrometer.observation.annotation.Observed;
import jakarta.inject.Singleton;

@Singleton
public class BookService {

    @Timed("books.find")
    @Observed(name = "books.lookup")
    public String find(String title) {
        return title;
    }
}
