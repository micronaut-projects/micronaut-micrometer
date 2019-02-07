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
package io.micronaut.configuration.metrics.binder.datasource


import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderFactorySpec extends Specification {

    def "test getting the beans manually"() {
        given:
        DataSourcePoolMetricsBinderFactory dataSourcePoolMetricsBinderFactory = new DataSourcePoolMetricsBinderFactory()

        when:
        def meterBinder = dataSourcePoolMetricsBinderFactory.dataSourceMeterBinder("foo", new Foo())

        then:
        meterBinder
    }

    class Foo implements DataSourcePoolMetadata {

        @Override
        DataSource getDataSource() {
            return null
        }

        @Override
        Integer getIdle() {
            return null
        }

        @Override
        Float getUsage() {
            return null
        }

        @Override
        Integer getActive() {
            return null
        }

        @Override
        Integer getMax() {
            return null
        }

        @Override
        Integer getMin() {
            return null
        }

        @Override
        String getValidationQuery() {
            return null
        }

        @Override
        Boolean getDefaultAutoCommit() {
            return null
        }
    }
}
