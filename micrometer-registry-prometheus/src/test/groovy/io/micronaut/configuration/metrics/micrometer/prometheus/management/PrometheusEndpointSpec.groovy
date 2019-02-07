/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.micrometer.prometheus.management

import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrometheusEndpointSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'endpoints.prometheus.sensitive':false,
            'micronaut.metrics.export.prometheus.descriptions':false
    ])
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext
                                                    .createBean(RxHttpClient, embeddedServer.getURL())


    void "test prometheus scrape"() {
        expect:
        client.retrieve('/prometheus').blockingFirst().contains('jvm_memory_used')
    }

    @NotYetImplemented
    void "test prometheus scrape no descriptions"() {
        given:
        def result = client.retrieve('/prometheus').blockingFirst()
        expect:
        result.contains('jvm_memory_used')
        !result.contains('# TYPE')
    }
}
