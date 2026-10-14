package io.micronaut.docs.metrics.testing

import io.micrometer.core.annotation.Timed
import io.micrometer.observation.annotation.Observed
import jakarta.inject.Singleton

@Singleton
class BookService {

    @Timed("books.find")
    @Observed(name = "books.lookup")
    String find(String title) {
        title
    }
}
