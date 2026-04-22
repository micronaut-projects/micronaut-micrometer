package io.micronaut.configuration.metrics.micrometer.stackdriver

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.stackdriver.StackdriverMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_CONFIG
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_ENABLED

class StackdriverMeterRegistryFactorySpec extends Specification {

    private static String MOCK_WAVEFRONT_PROJECTID = "stackdriverProjectId"
    private static final String GOOGLE_CLOUD_PROJECT_PROPERTY = "GOOGLE_CLOUD_PROJECT"

    void "verify StackdriverMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'StackdriverMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(StackdriverMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([StackdriverMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify StackdriverMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg)                              : setting,
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        then:
        context.findBean(StackdriverMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        STACKDRIVER_ENABLED       | true    | true
        STACKDRIVER_ENABLED       | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED)              : true,
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then: "default properties are used"
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.enabled()
        stackdriverMeterRegistry.get().config.numThreads() == 2
        stackdriverMeterRegistry.get().config.projectId() == MOCK_WAVEFRONT_PROJECTID
        stackdriverMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED)               : true,
                (STACKDRIVER_CONFIG + ".numThreads"): "77",
                (STACKDRIVER_CONFIG + ".projectId") : MOCK_WAVEFRONT_PROJECTID,
                (STACKDRIVER_CONFIG + ".step")      : "PT2M",
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then:
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.enabled()
        stackdriverMeterRegistry.get().config.numThreads() == 77
        stackdriverMeterRegistry.get().config.projectId() == MOCK_WAVEFRONT_PROJECTID
        stackdriverMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

    void "verify projectId can be inferred from Google Cloud defaults"() {
        given:
        String originalProjectId = System.getProperty(GOOGLE_CLOUD_PROJECT_PROPERTY)
        System.setProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, "inferred-stackdriver-project-id")

        when:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED): true,
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then:
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.projectId() == "inferred-stackdriver-project-id"

        cleanup:
        context?.stop()
        restoreSystemProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, originalProjectId)
    }

    void "verify app engine projectId strips the application prefix"() {
        given:
        String originalProjectId = System.getProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        System.setProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, "appengine:app-engine-project-id")

        expect:
        new StackdriverMeterRegistryFactory().getAppEngineProjectId() == "app-engine-project-id"

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, originalProjectId)
    }

    void "verify projectId can be inferred from service account credentials"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-credentials", ".json")
        Files.writeString(credentialsFile, '{"type":"service_account","project_id":"service-account-project-id"}')
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == "service-account-project-id"

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)
    }

    void "verify projectId can be inferred from the active gcloud configuration"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-config")
        Files.writeString(configDirectory.resolve("active_config"), "named-config\n")
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_named-config"), "[core]\nproject = active-gcloud-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "active-gcloud-project-id"

        cleanup:
        deleteDirectory(configDirectory)
    }

    void "verify legacy gcloud properties fallback is used when the active config is absent"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-legacy-config")
        Files.writeString(configDirectory.resolve("properties"), "project = legacy-gcloud-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "legacy-gcloud-project-id"

        cleanup:
        deleteDirectory(configDirectory)
    }

    void "verify metadata projectId is used when gcloud configuration is unavailable"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-empty-config")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "metadata-project-id"

        cleanup:
        deleteDirectory(configDirectory)
    }

    @Unroll
    void "verify gcloud project line parsing for '#line'"() {
        expect:
        new StackdriverMeterRegistryFactory().getProjectIdFromConfigLine(line) == projectId

        where:
        line                     | projectId
        "project = sample"       | "sample"
        "project=sample"         | "sample"
        "project =   "           | null
        "account = sample"       | null
        "project sample"         | null
    }

    private static void restoreSystemProperty(String propertyName, String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName)
        } else {
            System.setProperty(propertyName, propertyValue)
        }
    }

    private static StackdriverMeterRegistryFactory stackdriverFactory(Path configDirectory, String metadataProjectId) {
        return new StackdriverMeterRegistryFactory() {
            @Override
            Path getGoogleCloudConfigDirectory() {
                return configDirectory
            }

            @Override
            String getMetadataProjectId() {
                return metadataProjectId
            }
        }
    }

    private static void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return
        }
        directory.toFile().deleteDir()
    }

}
