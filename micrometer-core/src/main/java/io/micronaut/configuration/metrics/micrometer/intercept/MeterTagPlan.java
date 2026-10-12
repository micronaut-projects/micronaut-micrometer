/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.configuration.metrics.micrometer.intercept;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.aop.MeterTags;
import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@link MeterTag} annotations of a method, resolved once per method.
 *
 * <p>A {@link MeterTag} on a parameter tags the meter with a value computed from the argument. A {@link MeterTag} on
 * the method tags it with a value computed from the result of the invocation. The value is computed, in order of
 * precedence, by the {@link MeterTag#resolver()}, by the {@link MeterTag#expression()} or with
 * {@link Object#toString()}; {@code null} becomes an empty string. An expression is either a Micronaut evaluated
 * expression ({@code #{...}}), compiled with the application and evaluated against the invocation (the arguments are
 * available by name, for example {@code #{ #order.id }}), or any other expression, which is resolved against the
 * argument or result by the {@link ValueExpressionResolver} bean.</p>
 */
final class MeterTagPlan {

    private static final Logger LOG = LoggerFactory.getLogger(MeterTagPlan.class);
    private static final String MEMBER_KEY = "key";
    private static final String MEMBER_EXPRESSION = "expression";
    private static final String MEMBER_RESOLVER = "resolver";
    private static final int RESULT = -1;

    private final TagSource[] parameterTags;
    private final TagSource[] resultTags;

    private MeterTagPlan(TagSource[] parameterTags, TagSource[] resultTags) {
        this.parameterTags = parameterTags;
        this.resultTags = resultTags;
    }

    /**
     * Resolves the {@link MeterTag} annotations of a method.
     *
     * @param context The invocation context
     * @param beanContext The bean context used to look up the resolvers, or {@code null}
     * @return The plan, or {@code null} if the method has no {@link MeterTag} annotations
     */
    static @Nullable MeterTagPlan of(MethodInvocationContext<Object, Object> context, @Nullable BeanContext beanContext) {
        List<TagSource> parameterTags = null;
        Set<String> parameterKeys = new HashSet<>();
        Argument<?>[] arguments = context.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            AnnotationMetadata argumentMetadata = arguments[i].getAnnotationMetadata();
            if (argumentMetadata.hasAnnotation(MeterTag.class) || argumentMetadata.hasAnnotation(MeterTags.class)) {
                for (AnnotationValue<MeterTag> meterTag : argumentMetadata.getAnnotationValuesByType(MeterTag.class)) {
                    TagSource source = TagSource.of(meterTag, i, beanContext);
                    // the first tag with a key wins, as in Micrometer's MeterTagAnnotationHandler
                    if (parameterKeys.add(source.key)) {
                        if (parameterTags == null) {
                            parameterTags = new ArrayList<>();
                        }
                        parameterTags.add(source);
                    }
                }
            }
        }
        List<TagSource> resultTags = null;
        AnnotationMetadata methodMetadata = context.getExecutableMethod().getAnnotationMetadata();
        if (methodMetadata.hasAnnotation(MeterTag.class) || methodMetadata.hasAnnotation(MeterTags.class)) {
            Set<String> resultKeys = new HashSet<>();
            for (AnnotationValue<MeterTag> meterTag : methodMetadata.getAnnotationValuesByType(MeterTag.class)) {
                TagSource source = TagSource.of(meterTag, RESULT, beanContext);
                if (resultKeys.add(source.key)) {
                    if (resultTags == null) {
                        resultTags = new ArrayList<>();
                    }
                    resultTags.add(source);
                }
            }
        }
        if (parameterTags == null && resultTags == null) {
            return null;
        }
        return new MeterTagPlan(
            parameterTags == null ? new TagSource[0] : parameterTags.toArray(new TagSource[0]),
            resultTags == null ? new TagSource[0] : resultTags.toArray(new TagSource[0])
        );
    }

    /**
     * @param context The invocation context
     * @return The tags computed from the arguments
     */
    List<Tag> parameterTags(MethodInvocationContext<Object, Object> context) {
        List<Tag> tags = new ArrayList<>(parameterTags.length);
        addTags(tags, parameterTags, context, null);
        return tags;
    }

    /**
     * @param context The invocation context
     * @param result The result of the invocation, or {@code null} if it failed
     * @return The tags computed from the arguments and the result
     */
    List<Tag> tags(MethodInvocationContext<Object, Object> context, @Nullable Object result) {
        List<Tag> tags = new ArrayList<>(parameterTags.length + resultTags.length);
        addTags(tags, parameterTags, context, result);
        addTags(tags, resultTags, context, result);
        return tags;
    }

    private static void addTags(List<Tag> tags,
                                TagSource[] sources,
                                MethodInvocationContext<Object, Object> context,
                                @Nullable Object result) {
        if (sources.length == 0) {
            return;
        }
        Object[] parameterValues = context.getParameterValues();
        for (TagSource source : sources) {
            Object value = source.argumentIndex == RESULT ? result : parameterValues[source.argumentIndex];
            tags.add(Tag.of(source.key, source.resolve(value, context, parameterValues)));
        }
    }

    /**
     * How the value of one tag is computed.
     */
    private static final class TagSource {
        private final int argumentIndex;
        private final String key;
        private final @Nullable ValueResolver resolver;
        private final @Nullable String expression;
        private final @Nullable ValueExpressionResolver expressionResolver;
        private final @Nullable EvaluatedAnnotationValue<MeterTag> evaluatedExpression;
        private final boolean unresolvable;

        private TagSource(int argumentIndex,
                          String key,
                          @Nullable ValueResolver resolver,
                          @Nullable String expression,
                          @Nullable ValueExpressionResolver expressionResolver,
                          @Nullable EvaluatedAnnotationValue<MeterTag> evaluatedExpression,
                          boolean unresolvable) {
            this.argumentIndex = argumentIndex;
            this.key = key;
            this.resolver = resolver;
            this.expression = expression;
            this.expressionResolver = expressionResolver;
            this.evaluatedExpression = evaluatedExpression;
            this.unresolvable = unresolvable;
        }

        static TagSource of(AnnotationValue<MeterTag> meterTag, int argumentIndex, @Nullable BeanContext beanContext) {
            String key = meterTag.stringValue().filter(StringUtils::isNotEmpty)
                .or(() -> meterTag.stringValue(MEMBER_KEY))
                .orElse("");
            Class<?> resolverType = meterTag.classValue(MEMBER_RESOLVER).orElse(NoOpValueResolver.class);
            if (resolverType != NoOpValueResolver.class) {
                ValueResolver resolver = resolver(resolverType, beanContext);
                return new TagSource(argumentIndex, key, resolver, null, null, null, resolver == null);
            }
            if (meterTag.getValues().get(MEMBER_EXPRESSION) instanceof EvaluatedExpression) {
                if (meterTag instanceof EvaluatedAnnotationValue<MeterTag> evaluated) {
                    return new TagSource(argumentIndex, key, null, null, null, evaluated, false);
                }
                LOG.error("The expression of @MeterTag(\"{}\") cannot be evaluated, the tag value is empty", key);
                return new TagSource(argumentIndex, key, null, null, null, null, true);
            }
            String expression = meterTag.stringValue(MEMBER_EXPRESSION).filter(StringUtils::isNotEmpty).orElse(null);
            if (expression != null) {
                ValueExpressionResolver expressionResolver = beanContext != null
                    ? beanContext.findBean(ValueExpressionResolver.class).orElse(null)
                    : null;
                if (expressionResolver == null) {
                    LOG.error("@MeterTag(\"{}\") declares the expression '{}' but there is no {} bean, the tag value is empty. Use a #{{...}} expression or register a {} bean.",
                        key, expression, ValueExpressionResolver.class.getSimpleName(), ValueExpressionResolver.class.getName());
                }
                return new TagSource(argumentIndex, key, null, expression, expressionResolver, null, false);
            }
            return new TagSource(argumentIndex, key, null, null, null, null, false);
        }

        private static @Nullable ValueResolver resolver(Class<?> resolverType, @Nullable BeanContext beanContext) {
            Object resolver = beanContext != null ? beanContext.findBean(resolverType).orElse(null) : null;
            if (resolver == null) {
                resolver = InstantiationUtils.tryInstantiate(resolverType).orElse(null);
            }
            if (resolver instanceof ValueResolver valueResolver) {
                return valueResolver;
            }
            LOG.error("The @MeterTag resolver {} is neither a bean nor instantiable, the tag value is empty", resolverType.getName());
            return null;
        }

        String resolve(@Nullable Object value, MethodInvocationContext<Object, Object> context, Object[] parameterValues) {
            String resolved;
            if (resolver != null) {
                resolved = resolver.resolve(value);
            } else if (evaluatedExpression != null) {
                resolved = evaluatedExpression.withArguments(context.getTarget(), parameterValues)
                    .stringValue(MEMBER_EXPRESSION)
                    .orElse(null);
            } else if (expression != null) {
                resolved = expressionResolver != null ? expressionResolver.resolve(expression, value) : null;
            } else if (unresolvable) {
                resolved = null;
            } else {
                resolved = value != null ? value.toString() : null;
            }
            return resolved == null ? "" : resolved;
        }
    }
}
