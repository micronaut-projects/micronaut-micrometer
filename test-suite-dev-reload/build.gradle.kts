plugins {
    java
    id("io.micronaut.build.internal.micrometer-base")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautMicrometerCore)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    // the Logback binder is under test
    testImplementation(mnLogging.logback.classic)
}
