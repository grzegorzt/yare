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

package com.sabre.oss.yare.performance.suits;

import com.sabre.oss.yare.core.RulesEngineBuilder;
import com.sabre.oss.yare.dsl.RuleDsl;
import com.sabre.oss.yare.core.model.Rule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.List;

import static com.sabre.oss.yare.dsl.RuleDsl.*;
import static com.sabre.oss.yare.engine.MethodCallMetadata.method;

public class CollectionsPredicateTest extends AbstractPerformanceTest {

    public CollectionsPredicateTest() {
        Context.setRulesEngineProvider(config -> new RulesEngineBuilder()
                .withRulesRepository(i -> config.getRules())
                .withActionMapping("collect", method(new ResultCollectingAction(), (action) -> action.collect(null, null, null)))
                .build());
    }

    @Benchmark
    public void benchmarkTest(Context benchmarkContext) {
        super.benchmarkTest(benchmarkContext);
    }

    @State(Scope.Benchmark)
    public static class Context extends AbstractPerformanceTest.Context {

        @Setup
        public void setup() {
            super.setup();
        }

        @Override
        protected List<Rule> getRules() {
            List<Rule> rules = new ArrayList<>(2);
            rules.add(RuleDsl.ruleBuilder()
                    .name("ruleName")
                    .fact("collectionFact", CollectionFact.class)
                    .predicate(
                            contains(
                                    castToCollection(field("collectionFact.strings", List.class), String.class),
                                    values(String.class, value("string1"), value("string2"))
                            )
                    )
                    .action("collect",
                            param("context",
                                    reference("ctx", List.class)),
                            param("ruleName",
                                    value("name", String.class)),
                            param("fact", reference("collectionFact")))
                    .build()
            );
            rules.add(RuleDsl.ruleBuilder()
                    .name("ruleName2")
                    .fact("collectionFact", CollectionFact.class)
                    .predicate(
                            containsAny(
                                    castToCollection(field("collectionFact.strings", List.class), String.class),
                                    values(String.class, value("string1500"), value("string1"))
                            )
                    )
                    .action("collect",
                            param("context",
                                    reference("ctx", List.class)),
                            param("ruleName",
                                    value("name", String.class)),
                            param("fact", reference("collectionFact")))
                    .build()
            );

            return rules;
        }

        @Override
        protected Object getFact() {
            List<String> strings = new ArrayList<>(2);
            strings.add("string1");
            strings.add("string2");

            return new CollectionFact(strings);
        }

    }

    private static class CollectionFact {
        private final List<String> strings;

        CollectionFact(List<String> strings) {
            this.strings = strings;
        }

        public List<String> getStrings() {
            return strings;
        }
    }
}
