/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.serialization

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification


class MetricsMicronautSerialization extends Specification {

    void "test metrics endpoint with micronaut-serialization module"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder()
                .overrideConfigLocations().run(EmbeddedServer)
        URL server = embeddedServer.getURL()
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, server)

        when:
        def response = client.toBlocking().exchange("/metrics/", Map)
        Map result = response.body() as Map

        then:
        result["names"]
        List names = result["names"] as List
        !names.isEmpty()
        names.containsAll("executor.completed", "executor.queued")
        cleanup:
        client.close()
        embeddedServer.close()
    }
}
