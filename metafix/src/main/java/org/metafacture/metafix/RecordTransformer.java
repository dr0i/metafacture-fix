/*
 * Copyright 2021 Fabian Steeg, hbz
 *
 * Licensed under the Apache License, Version 2.0 the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.metafacture.metafix;

import org.metafacture.commons.StringUtil;
import org.metafacture.commons.reflection.ReflectionUtil;
import org.metafacture.framework.MetafactureException;
import org.metafacture.metafix.api.FixFunction;
import org.metafacture.metafix.api.FixPredicate;
import org.metafacture.metafix.fix.Do;
import org.metafacture.metafix.fix.ElsIf;
import org.metafacture.metafix.fix.Else;
import org.metafacture.metafix.fix.Expression;
import org.metafacture.metafix.fix.Fix;
import org.metafacture.metafix.fix.If;
import org.metafacture.metafix.fix.MethodCall;
import org.metafacture.metafix.fix.Options;
import org.metafacture.metafix.fix.Unless;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transform a record using a {@link Fix}.
 *
 * @author Fabian Steeg
 *
 */
class RecordTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(RecordTransformer.class);

    private final Metafix metafix;
    private final Fix fix;

    private Record record;

    RecordTransformer(final Metafix metafix, final Fix fix) {
        this.metafix = metafix;
        this.fix = fix;

        record = metafix.getCurrentRecord().shallowClone();
    }

    Record transform() {
        processSubexpressions(fix.getElements());
        return record;
    }

    Record getRecord() {
        return record;
    }

    private String resolveVars(final String string) {
        return string == null ? null : StringUtil.format(string, Metafix.VAR_START, Metafix.VAR_END, false, metafix.getVars());
    }

    private List<String> resolveParams(final List<String> params) {
        return params.stream().map(this::resolveVars).collect(Collectors.toList());
    }

    private void processSubexpressions(final List<Expression> expressions) {
        for (final Expression sub : expressions) {
            final List<String> params = resolveParams(sub.getParams());
            if (sub instanceof Do) {
                processBind((Do) sub, params);
            }
            else if (sub instanceof If) {
                processIf((If) sub, params);
            }
            else if (sub instanceof Unless) {
                processUnless((Unless) sub, params);
            }
            else {
                processFunction(sub, params);
            }
        }
    }

    private void processBind(final Do theDo, final List<String> params) {
        if (theDo.getName().equals("list")) { // TODO impl multiple binds via FixBind enum
            final Map<String, String> options = options(theDo.getOptions());
            record.findList(options.get("path"), a -> {
                for (int i = 0; i < a.size(); ++i) {
                    final Value value = a.get(i);
                    final String scopeVariable = options.get("var");

                    // with var -> keep full record in scope, add the var:
                    if (scopeVariable != null) {
                        record.put(scopeVariable, value);
                        processSubexpressions(theDo.getElements());
                        record.remove(scopeVariable);
                    }
                    // w/o var -> use the currently bound value as the record:
                    else {
                        final int index = i;

                        value.matchType()
                            .ifHash(h -> {
                                final Record fullRecord = record;
                                record = new Record();
                                record.addAll(h);
                                processSubexpressions(theDo.getElements());
                                a.set(index, new Value(record));
                                record = fullRecord;
                            })
                            // TODO: bind to arrays (if that makes sense) and strings (access with '.')
                            .orElseThrow();
                    }
                }
            });
        }
        else {
            LOG.warn("Unprocessed bind: {}", theDo);
            // TODO, possibly: use morph collectors here
            // final CollectFactory collectFactory = new CollectFactory();
            // final Map<String, String> attributes = resolvedAttributeMap(params, theDo.getOptions());
            // final Collect collect = collectFactory.newInstance(expression.getName(), attributes);
        }
    }

    private void processIf(final If ifExp, final List<String> params) {
        final ElsIf elsIfExp = ifExp.getElseIf();
        final Else elseExp = ifExp.getElse();
        if (testConditional(ifExp.getName(), params)) {
            processSubexpressions(ifExp.getElements());
        }
        else if (elsIfExp != null && testConditional(elsIfExp.getName(), resolveParams(elsIfExp.getParams()))) {
            processSubexpressions(elsIfExp.getElements());
        }
        else if (elseExp != null) {
            processSubexpressions(elseExp.getElements());
        }
    }

    private void processUnless(final Unless unless, final List<String> params) {
        if (!testConditional(unless.getName(), params)) {
            processSubexpressions(unless.getElements());
        }
    }

    private boolean testConditional(final String conditional, final List<String> params) {
        LOG.debug("<IF>: {} parameters: {}", conditional, params);

        try {
            final FixPredicate predicate = getInstance(conditional, FixPredicate.class, FixConditional::valueOf);
            return predicate.test(metafix, record, params, options(null)); // TODO: options
        }
        catch (final IllegalArgumentException e) {
            throw new MetafactureException(e);
        }

        // TODO, possibly: use morph functions here (& in processFunction):
        // final FunctionFactory functionFactory = new FunctionFactory();
        // functionFactory.registerClass("not_equals", NotEquals.class);
        // functionFactory.registerClass("replace_all", Replace.class);
        // final Function function = functionFactory.newInstance(conditional,
        // resolvedAttributeMap(params, theIf.getOptions()));
    }

    private void processFunction(final Expression expression, final List<String> params) {
        try {
            final FixFunction function = getInstance(expression.getName(), FixFunction.class, FixMethod::valueOf);
            function.apply(metafix, record, params, options(((MethodCall) expression).getOptions()));
        }
        catch (final IllegalArgumentException e) {
            throw new MetafactureException(e);
        }
    }

    private <T> T getInstance(final String name, final Class<T> baseType, final Function<String, ? extends T> enumFunction) {
        return name.contains(".") ? ReflectionUtil.loadClass(name, baseType).newInstance() : enumFunction.apply(name);
    }

    private Map<String, String> options(final Options options) {
        final Map<String, String> map = new LinkedHashMap<>();
        if (options != null) {
            for (int i = 0; i < options.getKeys().size(); i += 1) {
                map.put(options.getKeys().get(i), options.getValues().get(i));
            }
        }
        return map;
    }

}
