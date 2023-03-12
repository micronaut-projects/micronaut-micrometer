package io.micronaut.configuration.metrics.serialization;

import java.util.List;
import java.util.Map;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class MetricsMicronautSerializationTest {

    @Test
    void testMetricsEndpointWithSerializationModule(@Client("/") HttpClient client) {
        var response = client.toBlocking().exchange("/metrics/", Map.class);
        Map result = response.body();

        Assertions.assertNotNull(result);

        Object namesObject = result.get("names");
        Assertions.assertTrue(namesObject instanceof List<?>);
        List<String> names = (List<String>) namesObject;
        Assertions.assertTrue(names.containsAll(List.of("executor.completed", "executor.queued")));
    }
}
