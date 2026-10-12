# Micronaut Micrometer benchmarks

JMH benchmarks measuring the overhead of the Micrometer integrations (HTTP server and client metrics,
`@Timed`, `@Counted`, `@Observed`, the observation HTTP filters). This project is **not published**: it
applies only the `io.micronaut.build.internal.micrometer-base` convention (neither the module nor the
publishing conventions), so it is not part of the BOM, japicmp, the aggregated javadoc or a release.
`./gradlew check` only compiles it (`jmhClasses`), it never runs it.

The benchmarks live in `src/jmh/java` and use the [JMH Gradle plugin](https://github.com/melix/jmh-gradle-plugin).
The Gradle path is `:benchmarks` (excluded from the standardized `micronaut-micrometer-*` project names).

## Modes

Every benchmark that starts an `ApplicationContext` takes a `mode` parameter (`MetricsMode`). Each
benchmark only lists the modes that make sense for it. Every mode except `METRICS_PROMETHEUS` disables the
Prometheus registry, so metrics are recorded into the default `CompositeMeterRegistry` wrapping a
`SimpleMeterRegistry`.

| `mode` | Configuration |
|---|---|
| `NONE` | `micronaut.metrics.enabled=false` and `micrometer.observation.http.{server,client}.enabled=false`: no meter registry, no HTTP filter, no `@Timed`/`@Counted` interceptor. Baseline. |
| `OBSERVATION_NOOP` | `micronaut.metrics.enabled=false` only: what an application gets when it turns metrics off with `micronaut-micrometer-observation-http` on the classpath. The observation filters are present but the `ObservationRegistry` has no handler (no-op). |
| `NOOP_HANDLER` | `OBSERVATION_NOOP` plus an `ObservationHandler` that does nothing: the observation pipeline runs for real, nothing is recorded. |
| `METRICS` | The classic metrics filters (`ServerMetricsFilter` / `ClientMetricsFilter`, enabled by `micrometer.observation.http.*.enabled=false`) and the `@Timed`/`@Counted` interceptors. |
| `METRICS_HISTOGRAM` | `METRICS` with `micronaut.metrics.binders.web.{server,client}.percentiles=0.5,0.95,0.99` and `histogram=true`. |
| `METRICS_PROMETHEUS` | `METRICS` recording into the Prometheus registry. |
| `OBSERVATION` | The observation integration with its defaults: `ObservationServerFilter` / `ObservationClientFilter` and the `DefaultMeterObservationHandler` (the classic filters are off, the default with `micronaut-micrometer-observation-http` on the classpath). |
| `OBSERVATION_NO_LTT` | `OBSERVATION` with `micrometer.observations.long-task-timer.enabled=false`: no long task timer started per observation. |

Each trial checks that the mode wires what it claims (filter and interceptor beans present or absent,
meter registered after a warm-up call), so a configuration change cannot silently turn a benchmark into a
no-op.

## Benchmarks

| Benchmark | Modes | What it measures |
|---|---|---|
| `HttpServerBenchmark.{templated,completionStage,stream}` | `NONE`, `OBSERVATION_NOOP`, `NOOP_HANDLER`, `METRICS`, `METRICS_HISTOGRAM`, `METRICS_PROMETHEUS`, `OBSERVATION`, `OBSERVATION_NO_LTT` | A `GET /bench/{id}` (sync), `/bench/async/{id}` (`CompletionStage`) and `/bench/stream/{id}` (3 element `Flux`, chunked) through the full server filter chain (embedded Netty, random port), sent by the JDK `HttpClient` over a kept-alive HTTP/1.1 connection. The client is not instrumented. |
| `HttpClientBenchmark.{declarative,lowLevel}` | `NONE`, `OBSERVATION_NOOP`, `NOOP_HANDLER`, `METRICS`, `OBSERVATION`, `OBSERVATION_NO_LTT` | A request sent by the Micronaut HTTP client (declarative `@Client` with a templated path, and the low-level blocking client without a template) to a server running in a separate `NONE` context. Only the client is instrumented. |
| `TimedBenchmark.{sync,syncExtraTags,syncMeterTag,syncMeterTagExpression,syncHistogram,completionStage,mono,flux}` | `NONE`, `METRICS`, `METRICS_PROMETHEUS` | A call of a `@Timed` method. `syncExtraTags` has 2 `extraTags`, `syncMeterTag` a `@MeterTag` parameter (`toString()`), `syncMeterTagExpression` a method `@MeterTag` with an expression over the arguments, `syncHistogram` has `histogram = true` and 3 percentiles. Reactive results are consumed, the `Flux` has 3 elements. |
| `CountedBenchmark.{sync,syncExtraTags,syncMeterTag,completionStage,mono,flux}` | `NONE`, `METRICS`, `METRICS_PROMETHEUS` | The same for `@Counted`. |
| `ObservedBenchmark.{plain,sync,syncKeyValues,completionStage,mono,flux}` | `NONE`, `NOOP_HANDLER`, `OBSERVATION`, `OBSERVATION_NO_LTT` | A call of an `@Observed` method without a parent observation. The `ObservedInterceptor` cannot be disabled (the `ObservationRegistry` bean is unconditional), so in `NONE` it runs against a registry without handlers; `plain` is an unannotated method of the same bean (the zero baseline). |
| `ObservationServerFilterBenchmark.filter` | `OBSERVATION_NOOP`, `NOOP_HANDLER`, `OBSERVATION`, `OBSERVATION_NO_LTT` | `ObservationServerFilter.request` + `response` called directly (no server), with a request carrying a matched route. |
| `ServerMetricsFilterBenchmark.{resolvePath,filter}` | `METRICS`, `METRICS_HISTOGRAM`, `METRICS_PROMETHEUS` | The `ServerMetricsFilter` bean called directly: the private URI template resolution, and `onRequest` + `onResponse`. |
| `WebMetricsHelperBenchmark.{record,timerLookup,timerRecord}` | `registry=COMPOSITE,SIMPLE` | The recording part of the HTTP filters split up: `WebMetricsHelper.record` (tags + lookup + record), Micrometer's `registry.timer(name, tags)` lookup alone with prebuilt tags, and the record alone. |
| `SanitizePathBenchmark.{clean,trailingSlash,duplicateSlashes}` | - | `WebMetricsHelper.sanitizePath`, run for the `uri` tag of every request. |

The micro-benchmarks of package-private classes live in `io.micronaut.configuration.metrics.binder.web`.

## Running

```bash
# everything, short defaults: 1 fork, 3 x 2s warmup, 5 x 2s measurement (about 40 minutes)
./gradlew :benchmarks:jmh

# a subset (regular expressions, comma separated) with allocation profiling
./gradlew :benchmarks:jmh -Pjmh.includes=HttpServerBenchmark -Pjmh.profilers=gc

# restrict a parameter (values separated by |, parameters by ;)
./gradlew :benchmarks:jmh -Pjmh.includes='TimedBenchmark.sync$' -Pjmh.params='mode=NONE|METRICS' -Pjmh.profilers=gc

# allocation hot spots with JFR (one profile.jfr per benchmark and parameter set under /tmp/jfr)
./gradlew :benchmarks:jmh -Pjmh.includes='HttpServerBenchmark.templated' -Pjmh.params='mode=METRICS' \
    -Pjmh.profilers='jfr:dir=/tmp/jfr;configName=profile'
jfr view --width 200 allocation-by-site /tmp/jfr/*METRICS*/profile.jfr

# longer runs
./gradlew :benchmarks:jmh -Pjmh.fork=3 -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.warmup=5s -Pjmh.timeOnIteration=5s
```

All the properties: `jmh.includes`, `jmh.excludes` (comma separated regular expressions), `jmh.params`
(`name=v1|v2;other=v3`), `jmh.profilers` (comma separated, e.g. `gc`, `jfr`, `stack`), `jmh.fork`,
`jmh.warmupIterations`, `jmh.iterations`, `jmh.warmup`, `jmh.timeOnIteration` and `jmh.resultsFile`.

Results are written to `benchmarks/build/results/jmh/results.json` (or `-Pjmh.resultsFile`). Compare
`gc.alloc.rate.norm` (bytes allocated per operation, "B/op"): it is far more stable than throughput on a
busy machine. For the HTTP benchmarks it counts every thread of the JVM, so it includes the client (JDK or
Micronaut) and the Netty event loops; the uninstrumented side is a constant offset across the modes, so
compare against `NONE`.

## Comparing two branches

```bash
# baseline
git checkout <base-branch>
./gradlew :benchmarks:jmh -Pjmh.includes=HttpServerBenchmark -Pjmh.profilers=gc -Pjmh.resultsFile=/tmp/base.json

# candidate
git checkout <candidate-branch>
./gradlew :benchmarks:jmh -Pjmh.includes=HttpServerBenchmark -Pjmh.profilers=gc -Pjmh.resultsFile=/tmp/candidate.json

# side by side: benchmark, mode, ops/s and B/op of each run
for f in /tmp/base.json /tmp/candidate.json; do
  jq -r '.[] | [.benchmark, (.params.mode // .params.registry // "-"), (.primaryMetric.score|floor),
               (.secondaryMetrics["gc.alloc.rate.norm"].score|floor)] | @tsv' "$f" > "$f.tsv"
done
paste /tmp/base.json.tsv /tmp/candidate.json.tsv | awk -F'\t' '{printf "%-70s %-20s %12s %12s | %10s %10s\n", $1, $2, $3, $7, $4, $8}'
```

Or load both JSON files into [jmh.morethan.io](https://jmh.morethan.io) for a visual diff. Use a git
worktree per branch to run them back to back without rebuilding the other. Run both on the same machine, at
the same time of day, with nothing else running if you care about throughput; B/op differences of more than
a few bytes are real even on a busy machine.
