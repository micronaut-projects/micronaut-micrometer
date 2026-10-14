package io.micronaut.docs.metrics.testing

import io.micrometer.core.annotation.Timed
import io.micrometer.observation.annotation.Observed
import jakarta.inject.Singleton

@Singleton
open class BookService {

    @Timed("books.find")
    @Observed(name = "books.lookup")
    open fun find(title: String): String = title
}
