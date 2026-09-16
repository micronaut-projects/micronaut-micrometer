# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples under `test-suite-python/src/test/python/micronaut/docs`
that are disabled, reduced, or carry a workaround because the direct port of the Java example does not
compile or does not behave like the Java example yet. It is the bug-fixing task list for the Python compiler
(`micronaut-inject-python` / `micronaut-context-python`); every row references a `TODO(python)` comment in
the sources.

The Python examples are compiled by every build and their tests run with
`./gradlew pythonCheck -Ppython-ci` (the "Python CI" GitHub workflow).

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets.
  Standard Micronaut and library annotations are imported from their Java package
  (`micronaut.configuration.metrics.annotation`, `io.micrometer.core.annotation`, `jakarta.inject`, ...).
- Java classes are imported from their package (`from micronaut.http.server.exceptions import ExceptionHandler`,
  `from java.lang import RuntimeException`, `from reactor.core.publisher import Mono`), never aliased with
  `java.type("...")`.
- Micrometer types live in the `io.micrometer` package, which cannot be imported directly from Python yet
  (`io` is the standard library module): they are imported by name inside a `try:` block with an
  `except ImportError` fallback to the generated `micrometer.core...` shim packages
  (`try: from io.micrometer.core.instrument import MeterRegistry` / `except ImportError: from micrometer.core.instrument import MeterRegistry`).
- A Python test class is a `@MicronautTest` with `@Test` methods and plain `assert` statements; every test
  asserts on the meters recorded in the injected `MeterRegistry`, so a missing interceptor (`@Timed`) or an
  ignored filter/configurer fails the test instead of passing vacuously.

## Active `@Disabled` Tests

None.

## Workarounds in the Sources

| Target | Reason |
| --- | --- |
| `metrics.annotation.MetricOptionsFilterTaggersExample` | A class imported inside the `try:` import block (`from .MethodNameTagger import MethodNameTagger`, relative or absolute) is emitted **without its package** in the class-valued annotation member `taggers=[MethodNameTagger]` (`AnnotationClassValue` of the simple name `MethodNameTagger`), so the tagger filter never matches; the same import at module level is emitted fully qualified. The tagger import is kept outside the `try:` block. |

## java.type usages

None: every Java class is imported (`io.micrometer` types through the `try:`/`except ImportError` form above).
