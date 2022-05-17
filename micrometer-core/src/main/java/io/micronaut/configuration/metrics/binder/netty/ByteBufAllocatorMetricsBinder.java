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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufAllocatorMetric;
import io.netty.buffer.PoolArenaMetric;
import io.netty.buffer.PoolChunkListMetric;
import io.netty.buffer.PoolChunkMetric;
import io.netty.buffer.PoolSubpageMetric;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;
import io.netty.buffer.UnpooledByteBufAllocator;
import jakarta.inject.Inject;

import javax.annotation.PostConstruct;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ACTIVE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ALLOC;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ALLOCATION;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ARENA;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.AVAILABLE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.BYTE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.CACHE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.CHUNK;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.CHUNKLIST;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.DEALLOCATION;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.DIRECT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.HEAP;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.HUGE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.LOCAL;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.MAX;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.MEMORY;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.MIN;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NORMAL;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NUMBER;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PAGE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.POOLED;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SIZE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SMALL;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SUBPAGE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.THREAD;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.UNPOOLED;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.USAGE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.USED;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Metrics for Netty default ByteBufAllocators.
 *
 * @author Christophe Roudet
 * @since 2.0
 */
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
@Requires(classes = ByteBufAllocator.class)
@Context
@Internal
final class ByteBufAllocatorMetricsBinder {
    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private final Set<ByteBufAllocatorMetricKind> kinds;

    enum ByteBufAllocatorMetricKind {
        POOLED_ALLOCATOR, UNPOOLED_ALLOCATOR, POOLED_ARENAS, POOLED_ARENAS_SUBPAGES, POOLED_ARENAS_CHUNKLISTS, POOLED_ARENAS_CHUNKS,
    }

    /**
     * Adds metrics for Netty's default ByteBufAllocators.
     *
     * @param meterRegistryProvider The metric registry provider.
     * @param kinds The kinds of metrics to add.
     */
    @Inject
    public ByteBufAllocatorMetricsBinder(BeanProvider<MeterRegistry> meterRegistryProvider,
                                         @Value("${" + MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.metrics:null}") Set<ByteBufAllocatorMetricKind> kinds) {
        this.meterRegistryProvider = meterRegistryProvider;
        this.kinds = kinds == null || kinds.isEmpty() ? EnumSet.allOf(ByteBufAllocatorMetricKind.class) : kinds;
    }

    /**
     * Adds metrics for Netty's default ByteBufAllocators.
     */
    @PostConstruct
    public void configureNettyMetrics() {
        MeterRegistry meterRegistry = meterRegistryProvider.get();
        if (kinds.contains(ByteBufAllocatorMetricKind.POOLED_ALLOCATOR)) {
            PooledByteBufAllocatorMetric pooledMetric = PooledByteBufAllocator.DEFAULT.metric();

            Tags pooled = Tags.of(ALLOC, POOLED);
            Gauge.builder(dot(NETTY, ALLOC, MEMORY, USED), pooledMetric, ByteBufAllocatorMetric::usedHeapMemory)
                    .description("The number of the bytes of the heap memory.").tags(pooled.and(MEMORY, HEAP)).register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, MEMORY, USED), pooledMetric, ByteBufAllocatorMetric::usedDirectMemory)
                    .description("The number of the bytes of the directy memory.").tags(pooled.and(MEMORY, DIRECT)).register(meterRegistry);

            Gauge.builder(dot(NETTY, ALLOC, ARENA, COUNT), pooledMetric, PooledByteBufAllocatorMetric::numHeapArenas)
                    .description("The number of heap arenas.")
                    .tags(pooled.and(MEMORY, HEAP))
                    .register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, ARENA, COUNT), pooledMetric, PooledByteBufAllocatorMetric::numDirectArenas)
                    .description("The number of direct arenas.")
                    .tags(pooled.and(MEMORY, DIRECT))
                    .register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, THREAD, LOCAL, CACHE, COUNT), pooledMetric, PooledByteBufAllocatorMetric::numDirectArenas)
                    .description("The number of direct arenas.")
                    .tags(pooled)
                    .register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, CACHE, SIZE), pooledMetric, PooledByteBufAllocatorMetric::smallCacheSize)
                    .description("The size of the small cache.")
                    .tags(pooled.and(CACHE, SMALL))
                    .register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, CACHE, SIZE), pooledMetric, PooledByteBufAllocatorMetric::normalCacheSize)
                    .description("The size of the normat cache.")
                    .tags(pooled.and(CACHE, NORMAL))
                    .register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, CHUNK, SIZE), pooledMetric, PooledByteBufAllocatorMetric::chunkSize)
                    .description("The chunk size for an arena.")
                    .tags(pooled)
                    .register(meterRegistry);

            if (kinds.contains(ByteBufAllocatorMetricKind.POOLED_ARENAS)) {
                for (int i = 0; i < pooledMetric.directArenas().size(); i++) {
                    Tags tags = Tags.of(MEMORY, DIRECT)
                            .and(dot(ARENA, NUMBER), Integer.toString(i));

                    meterPoolArena(tags, pooledMetric.directArenas().get(i));
                }

                for (int i = 0; i < pooledMetric.heapArenas().size(); i++) {
                    Tags tags = Tags.of(MEMORY, HEAP)
                            .and(dot(ARENA, NUMBER), Integer.toString(i));

                    meterPoolArena(tags, pooledMetric.heapArenas().get(i));
                }
            }
        }

        if (kinds.contains(ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR)) {
            ByteBufAllocatorMetric unpooledMetric = UnpooledByteBufAllocator.DEFAULT.metric();
            Tags unpooled = Tags.of(ALLOC, UNPOOLED);

            Gauge.builder(dot(NETTY, ALLOC, MEMORY, USED), unpooledMetric, ByteBufAllocatorMetric::usedHeapMemory)
                    .description("The number of the bytes of the heap memory.").tags(unpooled.and(MEMORY, HEAP)).register(meterRegistry);
            Gauge.builder(dot(NETTY, ALLOC, MEMORY, USED), unpooledMetric, ByteBufAllocatorMetric::usedDirectMemory)
                    .description("The number of the bytes of the directy memory.").tags(unpooled.and(MEMORY, DIRECT)).register(meterRegistry);
        }
    }

    private void meterPoolArena(Tags tags, PoolArenaMetric pam) {
        MeterRegistry meterRegistry = meterRegistryProvider.get();

        Gauge.builder(dot(NETTY, ALLOC, ARENA, THREAD, CACHE, COUNT), pam, PoolArenaMetric::numThreadCaches)
                .description("Returns the number of thread caches backed by this arena.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, SUBPAGE, COUNT), pam, PoolArenaMetric::numSmallSubpages)
                .description("Returns the number of small sub-pages for the arena.")
                .tags(tags.and(SUBPAGE, SMALL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNKLIST, COUNT), pam, PoolArenaMetric::numChunkLists)
                .description("Returns the number of chunk lists for the arena.")
                .tags(tags)
                .register(meterRegistry);

        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT), pam, PoolArenaMetric::numAllocations)
                .description("Return the number of allocations done via the arena. This includes all sizes.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT), pam, PoolArenaMetric::numSmallAllocations)
                .description("Return the number of small allocations done via the arena.")
                .tags(tags.and(SIZE, SMALL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT), pam, PoolArenaMetric::numNormalAllocations)
                .description("Return the number of normal allocations done via the arena.")
                .tags(tags.and(SIZE, NORMAL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT), pam, PoolArenaMetric::numHugeAllocations)
                .description("Return the number of huge allocations done via the arena.")
                .tags(tags.and(SIZE, HUGE))
                .register(meterRegistry);

        Gauge.builder(dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT), pam, PoolArenaMetric::numDeallocations)
                .description("Return the number of deallocations done via the arena. This includes all sizes.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT), pam, PoolArenaMetric::numSmallDeallocations)
                .description("Return the number of small deallocations done via the arena.")
                .tags(tags.and(SIZE, SMALL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT), pam, PoolArenaMetric::numNormalDeallocations)
                .description("Return the number of normal deallocations done via the arena.")
                .tags(tags.and(SIZE, NORMAL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT), pam, PoolArenaMetric::numHugeDeallocations)
                .description("Return the number of huge deallocations done via the arena.")
                .tags(tags.and(SIZE, HUGE))
                .register(meterRegistry);

        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT), pam, PoolArenaMetric::numActiveAllocations)
                .description("Return the number of currently active allocations.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT), pam, PoolArenaMetric::numActiveSmallAllocations)
                .description("Return the number of currently active small allocations.")
                .tags(tags.and(SIZE, SMALL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT), pam, PoolArenaMetric::numActiveNormalAllocations)
                .description("Return the number of currently active normal allocations.")
                .tags(tags.and(SIZE, NORMAL))
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT), pam, PoolArenaMetric::numActiveHugeAllocations)
                .description("Return the number of currently active huge allocations.")
                .tags(tags.and(SIZE, HUGE))
                .register(meterRegistry);

        Gauge.builder(dot(NETTY, ALLOC, ARENA, ACTIVE, BYTE, COUNT), pam, PoolArenaMetric::numActiveBytes)
                .description("Return the number of active bytes that are currently allocated by the arena.")
                .tags(tags)
                .register(meterRegistry);

        if (kinds.contains(ByteBufAllocatorMetricKind.POOLED_ARENAS_SUBPAGES)) {
            for (int i = 0; i < pam.smallSubpages().size(); i++) {
                Tags tinySubpage = tags.and(SUBPAGE, SMALL)
                        .and(dot(SUBPAGE, NUMBER), Integer.toString(i));

                meterSubpage(tinySubpage, pam.smallSubpages().get(i));
            }
        }

        if (kinds.contains(ByteBufAllocatorMetricKind.POOLED_ARENAS_CHUNKLISTS)) {
            for (int i = 0; i < pam.chunkLists().size(); i++) {
                Tags chunkList = tags.and(dot(CHUNKLIST, NUMBER), Integer.toString(i));

                meterChunkList(chunkList, pam.chunkLists().get(i));
            }
        }
    }

    private void meterSubpage(Tags tags, PoolSubpageMetric psm) {
        MeterRegistry meterRegistry = meterRegistryProvider.get();

        Gauge.builder(dot(NETTY, ALLOC, ARENA, SUBPAGE, ELEMENT, MAX), psm, PoolSubpageMetric::maxNumElements)
                .description("Return the number of maximal elements that can be allocated out of the sub-page.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, SUBPAGE, AVAILABLE, COUNT), psm, PoolSubpageMetric::numAvailable)
                .description("Return the number of available elements to be allocated.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, SUBPAGE, ELEMENT, SIZE), psm, PoolSubpageMetric::elementSize)
                .description("Return the size (in bytes) of the elements that will be allocated.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, SUBPAGE, PAGE, SIZE), psm, PoolSubpageMetric::pageSize)
                .description("Return the size (in bytes) of this page.")
                .tags(tags)
                .register(meterRegistry);
    }

    private void meterChunkList(Tags tags, PoolChunkListMetric pclm) {
        MeterRegistry meterRegistry = meterRegistryProvider.get();

        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNKLIST, USAGE, MIN), pclm, PoolChunkListMetric::minUsage)
                .description("Return the minimum usage of the chunk list before which chunks are promoted to the previous list.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNKLIST, USAGE, MAX), pclm, PoolChunkListMetric::maxUsage)
                .description("Return the maximum usage of the chunk list after which chunks are promoted to the next list.")
                .tags(tags)
                .register(meterRegistry);
        if (kinds.contains(ByteBufAllocatorMetricKind.POOLED_ARENAS_CHUNKS)) {
            int index = 0;
            for (Iterator<PoolChunkMetric> i = pclm.iterator(); i.hasNext(); ++index) {
                meterChunk(tags.and(dot(CHUNK, NUMBER), Integer.toString(index)), i.next());
            }
        }
    }

    private void meterChunk(Tags tags, PoolChunkMetric pcm) {
        MeterRegistry meterRegistry = meterRegistryProvider.get();

        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNK, SIZE), pcm, PoolChunkMetric::chunkSize)
                .description("Return the size of the chunk in bytes, this is the maximum of bytes that can be served out of the chunk.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNK, USAGE), pcm, PoolChunkMetric::usage)
                .description("Return the percentage of the current usage of the chunk.")
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder(dot(NETTY, ALLOC, ARENA, CHUNK, SIZE, AVAILABLE), pcm, PoolChunkMetric::freeBytes)
                .description("Return the number of free bytes in the chunk.")
                .tags(tags)
                .register(meterRegistry);
    }
}
