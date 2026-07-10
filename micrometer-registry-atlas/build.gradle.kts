plugins {
    id("io.micronaut.build.internal.micrometer-registry")
}
dependencies {
    api(libs.micrometer.registry.atlas)
    constraints {
        implementation(libs.jackson2.databind)
    }
}
