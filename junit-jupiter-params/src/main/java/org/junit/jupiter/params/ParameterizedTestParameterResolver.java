/*
 * Copyright 2015-2018 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.params;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.DefaultArgumentsAccessor;
import org.junit.jupiter.params.converter.ArgumentConverter;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.DefaultArgumentConverter;
import org.junit.jupiter.params.support.AnnotationConsumerInitializer;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.StringUtils;

/**
 * @since 5.0
 */
class ParameterizedTestParameterResolver implements ParameterResolver {

	private final Object[] arguments;

	ParameterizedTestParameterResolver(Object[] arguments) {
		this.arguments = arguments;
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Executable declaringExecutable = parameterContext.getParameter().getDeclaringExecutable();
		Method testMethod = extensionContext.getTestMethod().orElse(null);
		return declaringExecutable.equals(testMethod) && (parameterContext.getIndex() < this.arguments.length
				|| isAggregate(parameterContext.getParameter()));
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		return isAggregate(parameterContext.getParameter()) ? aggregate(parameterContext, extensionContext)
				: convert(parameterContext, extensionContext);
	}

	private Object convert(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Parameter parameter = parameterContext.getParameter();
		Object argument = this.arguments[parameterContext.getIndex()];
		Optional<ConvertWith> annotation = AnnotationUtils.findAnnotation(parameter, ConvertWith.class);
		// @formatter:off
		ArgumentConverter argumentConverter = annotation.map(ConvertWith::value)
				.map(clazz -> (ArgumentConverter) ReflectionUtils.newInstance(clazz))
				.map(converter -> AnnotationConsumerInitializer.initialize(parameter, converter))
				.orElse(DefaultArgumentConverter.INSTANCE);
		// @formatter:on
		try {
			return argumentConverter.convert(argument, parameterContext);
		}
		catch (Exception ex) {
			String message = "Error resolving parameter at index " + parameterContext.getIndex();
			if (StringUtils.isNotBlank(ex.getMessage())) {
				message += ": " + ex.getMessage();
			}
			throw new ParameterResolutionException(message, ex);
		}
	}

	public static boolean isAggregate(Parameter parameter) {
		return ArgumentsAccessor.class.isAssignableFrom(parameter.getType())
				|| AnnotationUtils.isAnnotated(parameter, AggregateWith.class);
	}

	private Object aggregate(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Parameter parameter = parameterContext.getParameter();
		Optional<AggregateWith> annotation = AnnotationUtils.findAnnotation(parameter, AggregateWith.class);
		ArgumentsAccessor accessor = new DefaultArgumentsAccessor(this.arguments);
		try {
			// @formatter:off
			return annotation.map(AggregateWith::value)
					.map(clazz -> ReflectionUtils.newInstance(clazz))
					.map(aggregator -> aggregator.aggregateArguments(accessor, parameterContext))
					.orElse(accessor);
			// @formatter:on
		}
		catch (Exception ex) {
			String message = "Error aggregating arguments for parameter at index " + parameterContext.getIndex();
			if (StringUtils.isNotBlank(ex.getMessage())) {
				message += ": " + ex.getMessage();
			}
			throw new ParameterResolutionException(message, ex);
		}
	}

}
