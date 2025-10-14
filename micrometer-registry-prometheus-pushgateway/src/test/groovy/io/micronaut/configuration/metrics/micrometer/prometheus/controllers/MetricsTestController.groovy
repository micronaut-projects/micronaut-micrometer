package io.micronaut.configuration.metrics.micrometer.prometheus.controllers

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Put

@Controller("/metrics/job/test")
class MetricsTestController {

    private static String authHeader = ""

    @Get
    String hello() {
        return authHeader
    }

    @Put
    @Consumes(MediaType.TEXT_PLAIN)
    String test(@Body String body, @Header("Authorization") auth) {
        MetricsTestController.authHeader = auth
        return "OK"
    }
}
