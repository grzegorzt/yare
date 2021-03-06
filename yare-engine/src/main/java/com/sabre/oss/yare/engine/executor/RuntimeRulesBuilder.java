/*
 * MIT License
 *
 * Copyright 2018 Sabre GLBL Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sabre.oss.yare.engine.executor;

import com.sabre.oss.yare.core.call.ConsequenceFactory;
import com.sabre.oss.yare.core.call.FunctionFactory;
import com.sabre.oss.yare.core.call.ProcessingContext;
import com.sabre.oss.yare.core.invocation.Invocation;
import com.sabre.oss.yare.core.model.Attribute;
import com.sabre.oss.yare.core.model.Expression;
import com.sabre.oss.yare.core.model.Fact;
import com.sabre.oss.yare.core.model.Rule;
import com.sabre.oss.yare.engine.executor.runtime.operator.logical.False;
import com.sabre.oss.yare.engine.executor.runtime.operator.logical.True;
import com.sabre.oss.yare.engine.executor.runtime.predicate.Predicate;
import com.sabre.oss.yare.engine.executor.runtime.predicate.PredicateFactory;
import com.sabre.oss.yare.engine.executor.runtime.predicate.PredicateFactoryContext;
import com.sabre.oss.yare.engine.executor.runtime.validator.RuntimeInputValidator;
import com.sabre.oss.yare.engine.executor.runtime.value.ValueProvider;
import com.sabre.oss.yare.engine.executor.runtime.value.ValueProviderFactory;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class RuntimeRulesBuilder implements RuleComponentsFactoryFacade {
    private static final Logger log = LoggerFactory.getLogger(RuntimeRulesBuilder.class);

    private final PredicateFactory predicateFactory;
    private final FunctionFactory functionFactory;
    private final ConsequenceFactory consequenceFactory;

    public RuntimeRulesBuilder(PredicateFactory predicateFactory, FunctionFactory functionFactory, ConsequenceFactory consequenceFactory) {
        this.predicateFactory = predicateFactory;
        this.functionFactory = requireNonNull(functionFactory);
        this.consequenceFactory = requireNonNull(consequenceFactory);
    }

    public RuntimeRules build(Collection<Rule> rules) {
        Map<Type, String> factNames = new HashMap<>();
        List<RuntimeRules.ExecutableRule> executableRules = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            factNames.putAll(rule.getFacts().stream()
                    .collect(Collectors.toMap(Fact::getType, Fact::getIdentifier)));
            RuntimeRules.ExecutableRule runtimeRule = build(rule);
            executableRules.add(runtimeRule);
        }
        return new RuntimeRules(executableRules, factNames);
    }

    @Override
    public Predicate createPredicate(PredicateFactoryContext context, Expression expression) {
        if (expression instanceof Expression.Value) {
            Expression.Value value = (Expression.Value) expression;
            if (value.getValue() == null) {
                return ValueProviderFactory.constantNull();
            }
            if (!(value.getValue() instanceof Boolean)) {
                throw new IllegalArgumentException("Only boolean values can be translated directly to predicate");
            }
            return (Boolean) value.getValue() ? new True() : new False();
        }
        if (expression instanceof Expression.Reference) {
            ValueProvider valueProvider = createValueProvider(context, expression);
            Type type = valueProvider.getType();
            if (!type.equals(Boolean.class) && !type.equals(Boolean.TYPE) && !type.equals(Object.class)) {
                throw new IllegalArgumentException("Only references of boolean type can be translated directly to predicate");
            }
            return valueProvider;
        }
        if (expression instanceof Expression.Operator) {
            Expression.Operator operator = (Expression.Operator) expression;
            return predicateFactory.create(context, operator);
        }
        if (expression instanceof Expression.Function) {
            if (!Boolean.class.equals(expression.getType())) {
                throw new IllegalArgumentException("Only boolean functions can be translated directly to predicate");
            }
            return createValueProvider(context, expression);
        }
        throw new IllegalArgumentException(String.format("Unsupported expression: %s", expression));
    }

    @Override
    public ValueProvider createValueProvider(PredicateFactoryContext context, Expression expression) {
        if (expression instanceof Expression.Value) {
            Expression.Value value = (Expression.Value) expression;
            return ValueProviderFactory.createFromConstant(value.getValue());
        }
        if (expression instanceof Expression.Function) {
            Expression.Function function = (Expression.Function) expression;
            Invocation<ProcessingContext, Object> invocation = functionFactory.create((String) context.getRule().getAttribute("ruleName").getValue(), function);
            return ValueProviderFactory.createFromInvocation(invocation);
        }
        if (expression instanceof Expression.Reference) {
            Expression.Reference reference = (Expression.Reference) expression;
            return ValueProviderFactory.createFromPath(TypeUtils.getRawType(reference.getReferenceType(), null), reference.getReference(),
                    TypeUtils.getRawType(reference.getType(), null), reference.getPath());
        }
        throw new IllegalArgumentException(String.format("Unsupported expression: %s", expression));
    }

    private RuntimeRules.ExecutableRule build(Rule rule) {
        Attribute ruleNameAttr = rule.getAttribute("ruleName");
        Attribute priorityAttr = rule.getAttribute("priority");
        String ruleName = ruleNameAttr != null ? (String) ruleNameAttr.getValue() : null;
        Number priority = priorityAttr != null ? (Number) priorityAttr.getValue() : 0;
        if (Objects.isNull(ruleName)) {
            ruleName = "id-" + Long.toHexString(System.identityHashCode(rule));
            log.warn("'ruleName' property not defined. Created identifier {}", ruleName);
        }
        PredicateFactoryContext factoryContext = new PredicateFactoryContext(rule, this);
        Predicate predicate = RuntimeInputValidator.of(rule.getFacts(), createPredicate(factoryContext, rule.getPredicate()));
        Invocation<ProcessingContext, Void> consequence = prepareConsequence(rule);

        return RuntimeRules.ExecutableRule.of(ruleName, predicate, consequence, priority.longValue());
    }

    private Invocation<ProcessingContext, Void> prepareConsequence(Rule rule) {
        String ruleName = (String) rule.getAttribute("ruleName").getValue();
        return consequenceFactory.createConsequence(ruleName, rule.getActions());
    }
}
