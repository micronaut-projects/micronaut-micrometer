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
package io.micronaut.configuration.metrics.binder.netty;

/**
 * Defines netty metric.
 *
 * @author croudet
 * @since 2.0
 */
final class NettyMetrics {

    static final String NETTY = "netty";
    static final String ALLOC = "alloc";
    static final String POOLED = "pooled";
    static final String UNPOOLED = "unpooled";
    static final String QUEUE = "queue";

    static final String MEMORY = "memory";
    static final String DIRECT = "direct";
    static final String HEAP = "heap";

    static final String ARENA = "arena";

    static final String CHUNK = "chunk";

    static final String THREAD = "thread";
    static final String LOCAL = "local";

    static final String CACHE = "cache";
    static final String SIZE = "size";
    static final String TINY = "tiny";
    static final String SMALL = "small";
    static final String NORMAL = "normal";
    static final String HUGE = "huge";

    static final String SUBPAGE = "subpage";
    static final String CHUNKLIST = "chunklist";
    static final String NUMBER = "number";

    static final String USED = "used";
    static final String COUNT = "count";
    static final String GLOBAL = "global";

    static final String ALLOCATION = "allocation";
    static final String DEALLOCATION = "deallocation";
    static final String ACTIVE = "active";
    static final String BYTE = "byte";

    static final String ELEMENT = "element";
    static final String MAX = "max";
    static final String MIN = "min";
    static final String AVAILABLE = "available";
    static final String PAGE = "page";
    static final String USAGE = "usage";

    static final String WAIT_TIME = "wait.time";
    static final String EXECUTION_TIME = "execution.time";

    static final String GROUP = "group";
    static final String PARENT = "parent";
    static final String WORKER = "worker";

    private NettyMetrics() {

    }

    static String dot(String... strings) {
        return String.join(".", strings);
    }
}
