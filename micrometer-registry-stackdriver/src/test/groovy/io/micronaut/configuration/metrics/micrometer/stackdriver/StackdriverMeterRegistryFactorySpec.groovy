package io.micronaut.configuration.metrics.micrometer.stackdriver

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.stackdriver.StackdriverMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Isolated
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_CONFIG
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_ENABLED

@Isolated("mutates system properties")
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

    void "verify blank configured projectId falls back to Google Cloud defaults"() {
        given:
        String originalProjectId = System.getProperty(GOOGLE_CLOUD_PROJECT_PROPERTY)
        System.setProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, "inferred-stackdriver-project-id")

        when:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED)              : true,
                (STACKDRIVER_CONFIG + ".projectId"): "  ",
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then:
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.projectId() == "inferred-stackdriver-project-id"

        cleanup:
        context?.stop()
        restoreSystemProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, originalProjectId)
    }

    void "verify inferred projectId is cached"() {
        given:
        String projectId = "initial-project-id"
        StackdriverMeterRegistryFactory factory = new StackdriverMeterRegistryFactory(null, null, {
            it == StackdriverMeterRegistryFactory.GOOGLE_CLOUD_PROJECT ? projectId : null
        })
        def stackdriverConfig = factory.stackdriverConfig(new Properties())

        expect:
        stackdriverConfig.projectId() == "initial-project-id"

        when:
        projectId = "updated-project-id"

        then:
        stackdriverConfig.projectId() == "initial-project-id"
    }

    void "verify unresolved projectId is cached"() {
        given:
        int environmentCalls = 0
        StackdriverMeterRegistryFactory factory = new StackdriverMeterRegistryFactory(null, null, {
            environmentCalls++
            null
        })
        def stackdriverConfig = factory.stackdriverConfig(new Properties())

        expect:
        stackdriverConfig.projectId() == null
        stackdriverConfig.projectId() == null
        environmentCalls == 4
    }

    void "verify projectId falls back to GCLOUD_PROJECT when GOOGLE_CLOUD_PROJECT is absent"() {
        given:
        String originalGoogleCloudProject = System.getProperty(GOOGLE_CLOUD_PROJECT_PROPERTY)
        String originalGcloudProject = System.getProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT)
        System.setProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, "")
        System.setProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, "legacy-gcloud-project-id")

        expect:
        isolatedStackdriverFactory("metadata-project-id").getDefaultProjectId() == "legacy-gcloud-project-id"

        cleanup:
        restoreSystemProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, originalGoogleCloudProject)
        restoreSystemProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, originalGcloudProject)
    }

    void "verify projectId falls back to App Engine when cloud project properties are absent"() {
        given:
        String originalGoogleCloudProject = System.getProperty(GOOGLE_CLOUD_PROJECT_PROPERTY)
        String originalGcloudProject = System.getProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT)
        String originalAppEngineProjectId = System.getProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        System.setProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, "")
        System.setProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, "")
        System.setProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, "appengine:app-engine-project-id")

        expect:
        isolatedStackdriverFactory("metadata-project-id").getDefaultProjectId() == "app-engine-project-id"

        cleanup:
        restoreSystemProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, originalGoogleCloudProject)
        restoreSystemProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, originalGcloudProject)
        restoreSystemProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, originalAppEngineProjectId)
    }

    void "verify projectId falls back to service account credentials when cloud and App Engine ids are absent"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-default-credentials", ".json")
        Files.writeString(credentialsFile, '{"type":"service_account","project_id":"service-account-project-id"}')
        String originalGoogleCloudProject = System.getProperty(GOOGLE_CLOUD_PROJECT_PROPERTY)
        String originalGcloudProject = System.getProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT)
        String originalAppEngineProjectId = System.getProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, "")
        System.setProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, "")
        System.clearProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        isolatedStackdriverFactory("metadata-project-id").getDefaultProjectId() == "service-account-project-id"

        cleanup:
        restoreSystemProperty(GOOGLE_CLOUD_PROJECT_PROPERTY, originalGoogleCloudProject)
        restoreSystemProperty(StackdriverMeterRegistryFactory.GCLOUD_PROJECT, originalGcloudProject)
        restoreSystemProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, originalAppEngineProjectId)
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)
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

    void "verify app engine projectId keeps the configured id when there is no prefix"() {
        given:
        String originalProjectId = System.getProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        System.setProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, "app-engine-project-id")

        expect:
        new StackdriverMeterRegistryFactory().getAppEngineProjectId() == "app-engine-project-id"

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, originalProjectId)
    }

    void "verify app engine projectId is ignored when it is absent"() {
        given:
        String originalProjectId = System.getProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)
        System.clearProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID)

        expect:
        new StackdriverMeterRegistryFactory().getAppEngineProjectId() == null

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.APP_ENGINE_APPLICATION_ID, originalProjectId)
    }

    void "verify projectId can be inferred from service account credentials"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-credentials", ".json")
        Files.writeString(credentialsFile, '{"type":"service_account","project_id":  "service-account-project-id"}')
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == "service-account-project-id"

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)
    }

    void "verify service account credentials are ignored when they are absent"() {
        given:
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, "")

        expect:
        isolatedStackdriverFactory(null).getServiceAccountProjectId() == null

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
    }

    void "verify escaped projectId can be inferred from service account credentials"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-escaped-credentials", ".json")
        Files.writeString(credentialsFile, $/{"type":"service_account","project_id":"service-account-\"project-id"}/$)
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == 'service-account-"project-id'

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)
    }

    void "verify missing service account credentials are ignored"() {
        given:
        Path missingCredentialsFile = Path.of(System.getProperty("java.io.tmpdir"), "missing-stackdriver-credentials.json")
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, missingCredentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == null

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
    }

    void "verify credentials without a project id are ignored"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-credentials-without-project", ".json")
        Files.writeString(credentialsFile, '{"type":"service_account"}')
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == null

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)
    }

    @Unroll
    void "verify malformed service account credentials '#credentialsJson' are ignored"() {
        given:
        Path credentialsFile = Files.createTempFile("stackdriver-malformed-credentials", ".json")
        Files.writeString(credentialsFile, credentialsJson)
        String originalCredentialsPath = System.getProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS)
        System.setProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, credentialsFile.toString())

        expect:
        new StackdriverMeterRegistryFactory().getServiceAccountProjectId() == null

        cleanup:
        restoreSystemProperty(StackdriverMeterRegistryFactory.GOOGLE_APPLICATION_CREDENTIALS, originalCredentialsPath)
        Files.deleteIfExists(credentialsFile)

        where:
        credentialsJson << [
                '{"type":"service_account","project_id" "missing-colon"}',
                '{"type":"service_account","project_id":123}',
                '{"type":"service_account","project_id":"unterminated}'
        ]
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

    void "verify missing active gcloud config falls back to the default configuration"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-missing-active-config")
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_default"), "[core]\nproject = default-gcloud-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "default-gcloud-project-id"

        cleanup:
        deleteDirectory(configDirectory)
    }

    void "verify blank active gcloud config falls back to the default configuration"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-default-config")
        Files.writeString(configDirectory.resolve("active_config"), "\n")
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_default"), "[core]\nproject = default-gcloud-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "default-gcloud-project-id"

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

    void "verify gcloud configuration ignores projects outside core sections"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-non-core-config")
        Files.writeString(configDirectory.resolve("active_config"), "named-config\n")
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_named-config"), "[auth]\nproject = ignored-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "metadata-project-id"

        cleanup:
        deleteDirectory(configDirectory)
    }

    void "verify gcloud configuration ignores comments and non-core sections"() {
        given:
        Path configDirectory = Files.createTempDirectory("stackdriver-gcloud-multi-section-config")
        Files.writeString(configDirectory.resolve("active_config"), "named-config\n")
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_named-config"), "; comment\n\n[auth]\nproject = ignored-project-id\n[core]\naccount = test-account\nproject = core-project-id\n")

        expect:
        stackdriverFactory(configDirectory, "metadata-project-id").getGoogleCloudProjectId() == "core-project-id"

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
    void "verify blank #environmentName environment value is ignored"() {
        given:
        Path userHome = Files.createTempDirectory("stackdriver-blank-env-home")
        Path configDirectory = Files.createDirectories(userHome.resolve(".config").resolve("gcloud"))
        Path configurationDirectory = Files.createDirectories(configDirectory.resolve("configurations"))
        Files.writeString(configurationDirectory.resolve("config_default"), "[core]\nproject = default-gcloud-project-id\n")
        String originalUserHome = System.getProperty("user.home")
        String originalOsName = System.getProperty("os.name")
        System.setProperty("user.home", userHome.toString())
        if (windows) {
            System.setProperty("os.name", "Windows 11")
        }
        StackdriverMeterRegistryFactory factory = new StackdriverMeterRegistryFactory(null, "metadata-project-id", {
            it == environmentName ? environmentValue : null
        })

        expect:
        factory.getGoogleCloudProjectId() == "default-gcloud-project-id"

        cleanup:
        restoreSystemProperty("user.home", originalUserHome)
        restoreSystemProperty("os.name", originalOsName)
        deleteDirectory(userHome)

        where:
        environmentName                                  | environmentValue | windows
        StackdriverMeterRegistryFactory.CLOUDSDK_CONFIG  | ""               | false
        StackdriverMeterRegistryFactory.CLOUDSDK_CONFIG  | "  "             | false
        StackdriverMeterRegistryFactory.APPDATA          | ""               | true
        StackdriverMeterRegistryFactory.APPDATA          | "  "             | true
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
        return new StackdriverMeterRegistryFactory(configDirectory, metadataProjectId)
    }

    private static StackdriverMeterRegistryFactory isolatedStackdriverFactory(String metadataProjectId) {
        return new StackdriverMeterRegistryFactory(null, metadataProjectId, ignored -> null)
    }

    private static void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return
        }
        directory.toFile().deleteDir()
    }

}
