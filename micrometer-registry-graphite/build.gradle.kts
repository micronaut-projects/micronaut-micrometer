plugins {
    id("io.micronaut.build.internal.micrometer-registry")
}

dependencies {
    api(libs.micrometer.registry.graphite)
}

configurations.configureEach {
    resolutionStrategy.force("com.rabbitmq:amqp-client:5.33.0") // fixes CVE-2026-61634
}
