/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.binder.web.config;

/**
 * Http meter configuration.
 */
public abstract class HttpMeterConfig {

    private Double[] percentiles = new Double[]{};
    private Boolean histogram = false;
    private Double min = null;
    private Double max = null;
    private Double[] slos = new Double[]{};

    /**
     * Default is empty.
     * @return The percentiles. Specify in CSV format, ex: "0.95,0.99".
     */
    public Double[] getPercentiles() {
        return percentiles;
    }

    /**
     * Default is empty.
     * @param percentiles The percentiles. Specify in CSV format, ex: "0.95,0.99".
     */
    public void setPercentiles(Double[] percentiles) {
        this.percentiles = percentiles;
    }

    /**
     * Default: false.
     * @return If a histogram should be published.
     */
    public Boolean getHistogram() {
        return histogram;
    }

    /**
     * Default: false.
     * @param histogram If a histogram should be published.
     */
    public void setHistogram(Boolean histogram) {
        this.histogram = histogram;
    }

    /**
     * Default: Micrometer default value (0.001).
     * @return The minimum time (in s) value expected.
     */
    public Double getMin() {
        return min;
    }

    /**
     * Default: Micrometer default value (0.001).
     * @param min The minimum time (in s) value expected.
     */
    public void setMin(Double min) {
        this.min = min;
    }

    /**
     * Default: Micrometer default value (30).
     * @return The maximum time (in s) value expected.
     */
    public Double getMax() {
        return max;
    }

    /**
     * Default: Micrometer default value (30).
     * @param max The maximum time (in s) value expected.
     */
    public void setMax(Double max) {
        this.max = max;
    }

    /**
     * Default is empty.
     * @return The user-defined service levels objectives (in s) to create. Specify in CSV format, ex: "0.1,0.4".
     */
    public Double[] getSlos() {
        return slos;
    }

    /**
     * Default is empty.
     * @param slos The user-defined service levels objectives (in s) to create. Specify in CSV format, ex: "0.1,0.4".
     */
    public void setSlos(Double[] slos) {
        this.slos = slos;
    }
}
