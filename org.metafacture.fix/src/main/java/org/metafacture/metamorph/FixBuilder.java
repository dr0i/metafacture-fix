/*
 * Copyright 2013, 2019 Deutsche Nationalbibliothek and others
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

package org.metafacture.metamorph;

import org.metafacture.fix.fix.Do;
import org.metafacture.fix.fix.Expression;
import org.metafacture.fix.fix.Fix;
import org.metafacture.fix.fix.MethodCall;
import org.metafacture.fix.fix.Options;
import org.metafacture.metamorph.api.Collect;
import org.metafacture.metamorph.api.ConditionAware;
import org.metafacture.metamorph.api.InterceptorFactory;
import org.metafacture.metamorph.api.NamedValuePipe;
import org.metafacture.metamorph.api.NamedValueSource;
import org.metafacture.metamorph.collectors.Combine;
import org.metafacture.metamorph.functions.Compose;
import org.metafacture.metamorph.functions.Constant;
import org.metafacture.metamorph.functions.Lookup;
import org.metafacture.metamorph.functions.Replace;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.xbase.lib.Pair;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Builds a {@link Metafix} from a Fix DSL description
 *
 * @author Markus Michael Geipel (MorphBuilder)
 * @author Christoph Böhme (MorphBuilder)
 * @author Fabian Steeg (FixBuilder)
 *
 */
public class FixBuilder { // checkstyle-disable-line ClassDataAbstractionCoupling|ClassFanOutComplexity
    private final Deque<StackFrame> stack = new LinkedList<>();
    private final InterceptorFactory interceptorFactory;
    private final Metafix metafix;

    public FixBuilder(final Metafix metafix, final InterceptorFactory interceptorFactory) {
        this.metafix = metafix;
        this.interceptorFactory = interceptorFactory;

        stack.push(new StackFrame(metafix));
    }

    public void walk(final Fix fix, final Map<String, String> vars) {
        for (final Expression expression : fix.getElements()) {
            final EList<String> params = expression.getParams();
            if (expression instanceof Do) {
                processCollector(expression, params);
            }
            else {
                processExpression(expression, params);
            }
        }
    }

    private void processCollector(final Expression expression, final EList<String> params) {
        final String firstParam = resolvedAttribute(params, 1);
        final String secondParam = resolvedAttribute(params, 2);
        final Do theDo = (Do) expression;
        Collect collect = null;
        switch (expression.getName()) {
            case "combine":
                final Combine combine = new Combine();
                collect = combine;
                combine.setName(firstParam);
                combine.setValue(secondParam);
                break;
            default:
                break;
        }
        if (collect != null) {
            stack.push(new StackFrame(collect));
            for (final Expression doExpression : theDo.getElements()) {
                processExpression(doExpression, doExpression.getParams());
            }
            final StackFrame currentCollect = stack.pop();
            final NamedValuePipe tailPipe = currentCollect.getPipe();
            final NamedValuePipe interceptor = interceptorFactory.createNamedValueInterceptor();
            final NamedValuePipe delegate;
            if (interceptor == null || tailPipe instanceof Entity) {
                // The result of entity collectors cannot be intercepted
                // because they only use the receive/emit interface for
                // signaling while the actual data is transferred using
                // a custom mechanism. In order for this to work the Entity
                // class checks whether source and receiver are an
                // instances of Entity. If an interceptor is inserted between
                // entity elements this mechanism will break.
                delegate = tailPipe;
            }
            else {
                delegate = interceptor;
                delegate.addNamedValueSource(tailPipe);
            }
            exitData(delegate);
        }
    }

    private void processExpression(final Expression expression, final EList<String> params) {
        final String firstParam = resolvedAttribute(params, 1);
        final String secondParam = resolvedAttribute(params, 2);
        switch (expression.getName()) {
            case "map" :
                enterDataMap(params);
                exitData(getDelegate(stack.pop().getPipe()));
                break;
            case "add_field" :
                enterDataAdd(params);
                exitData(getDelegate(stack.pop().getPipe()));
                break;
            case "replace_all" :
                final Replace replace = new Replace();
                replace.setPattern(secondParam);
                replace.setWith(resolvedAttribute(params, 3)); // checkstyle-disable-line MagicNumber
                enterDataFunction(firstParam, replace);
                exitData(getDelegate(stack.pop().getPipe()));
                mapBack(firstParam);
                break;
            case "append" :
                compose(firstParam, "", secondParam);
                mapBack(firstParam);
                break;
            case "prepend" :
                compose(firstParam, secondParam, "");
                mapBack(firstParam);
                break;
            case "lookup" :
                final Lookup lookup = new Lookup();
                lookup.setMaps(metafix);
                lookup.setIn("inline");
                metafix.putMap("inline", buildMap(((MethodCall) expression).getOptions()));
                enterDataFunction(firstParam, lookup);
                exitData(getDelegate(stack.pop().getPipe()));
                mapBack(firstParam);
                break;
            default: break;
        }
    }

    private Map<String, String> buildMap(final Options options) {
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < options.getKeys().size(); i += 1) {
            map.put(options.getKeys().get(i), options.getValues().get(i));
        }
        return map;
    }

    private void compose(final String field, final String prefix, final String postfix) {
        final Compose compose = new Compose();
        compose.setPrefix(prefix);
        compose.setPostfix(postfix);
        enterDataFunction(field, compose);
        exitData(getDelegate(stack.pop().getPipe()));
    }

    private void mapBack(final String name) {
        enterDataMap(new BasicEList<String>(Arrays.asList("@" + name, name)));
        exitData(getDelegate(stack.pop().getPipe()));
    }

    private void exitData(final NamedValueSource delegate) {
        final StackFrame parent = stack.peek();

        if (parent.isInEntityName()) {
            // Protected xsd schema and by assertion in enterName:
            ((Entity) parent.getPipe()).setNameSource(delegate);
        }
        else if (parent.isInCondition()) {
            // Protected xsd schema and by assertion in enterIf:
            ((ConditionAware) parent.getPipe()).setConditionSource(delegate);
        }
        else {
            parent.getPipe().addNamedValueSource(delegate);
        }
    }

    private void enterDataMap(final EList<String> params) {
        final String resolvedAttribute = resolvedAttribute(params, 2);
        if (resolvedAttribute != null && resolvedAttribute.contains(".")) {
            final String[] keyElements = resolvedAttribute.split("\\.");
            final Pair<Entity, Entity> firstAndLast = createEntities(keyElements);
            final Data data = new Data();
            data.setName(keyElements[keyElements.length - 1]);
            final String source = resolvedAttribute(params, 1);
            firstAndLast.getValue().addNamedValueSource(data);
            metafix.registerNamedValueReceiver(source, getDelegate(data));
            stack.push(new StackFrame(firstAndLast.getKey()));
        }
        else {
            final Data data = new Data();
            data.setName(resolvedAttribute(params, 2));
            final String source = resolvedAttribute(params, 1);
            metafix.registerNamedValueReceiver(source, getDelegate(data));
            stack.push(new StackFrame(data));
        }
    }

    private void enterDataAdd(final EList<String> params) {
        final String resolvedAttribute = resolvedAttribute(params, 1);
        if (resolvedAttribute.contains(".")) {
            addNestedField(params, resolvedAttribute);
        }
        else {
            final Data data = new Data();
            data.setName(resolvedAttribute);
            final Constant constant = new Constant();
            constant.setValue(resolvedAttribute(params, 2));
            data.addNamedValueSource(constant);
            metafix.registerNamedValueReceiver("_id", constant);
            stack.push(new StackFrame(data));
        }
    }

    private void enterDataFunction(final String fieldName, final NamedValuePipe interceptor) {
        final Data data = new Data();
        data.setName("@" + fieldName);
        metafix.registerNamedValueReceiver(fieldName, getDelegate(data, interceptor));
        stack.push(new StackFrame(data));
    }

    private void addNestedField(final EList<String> params, final String resolvedAttribute) {
        final String[] keyElements = resolvedAttribute.split("\\.");
        final Pair<Entity, Entity> firstAndLast = createEntities(keyElements);
        final Constant constant = new Constant();
        constant.setValue(resolvedAttribute(params, 2));
        final Data data = new Data();
        data.setName(keyElements[keyElements.length - 1]);
        data.addNamedValueSource(constant);
        firstAndLast.getValue().addNamedValueSource(data);
        metafix.registerNamedValueReceiver("_id", constant);
        stack.push(new StackFrame(firstAndLast.getKey()));
    }

    private Pair<Entity, Entity> createEntities(final String[] keyElements) {
        Entity firstEntity = null;
        Entity lastEntity = null;
        for (int i = 0; i < keyElements.length - 1; i = i + 1) {
            final Entity currentEntity = new Entity(() -> metafix.getStreamReceiver());
            currentEntity.setName(keyElements[i]);
            if (firstEntity == null) {
                firstEntity = currentEntity;
            }
            if (lastEntity != null) {
                lastEntity.addNamedValueSource(currentEntity);
            }
            lastEntity = currentEntity;
        }
        return new Pair<>(firstEntity, lastEntity);
    }

    private NamedValuePipe getDelegate(final NamedValuePipe data) {
        return getDelegate(data, interceptorFactory.createNamedValueInterceptor());
    }

    private NamedValuePipe getDelegate(final NamedValuePipe data, final NamedValuePipe interceptor) {
        final NamedValuePipe delegate;

        if (interceptor == null) {
            delegate = data;
        }
        else {
            delegate = interceptor;
            data.addNamedValueSource(delegate);
        }

        return delegate;
    }

    private String resolvedAttribute(final EList<String> params, final int i) {
        // TODO: resolve from vars/map/etc
        return params.size() < i ? null : params.get(i - 1);
    }

    private static class StackFrame {

        private final NamedValuePipe pipe;

        private boolean inCondition;
        private boolean inEntityName;

        private StackFrame(final NamedValuePipe pipe) {
            this.pipe = pipe;
        }

        public NamedValuePipe getPipe() {
            return pipe;
        }

        public void setInEntityName(final boolean inEntityName) {
            this.inEntityName = inEntityName;
        }

        public boolean isInEntityName() {
            return inEntityName;
        }

        public void setInCondition(final boolean inCondition) {
            this.inCondition = inCondition;
        }

        public boolean isInCondition() {
            return inCondition;
        }

    }

}
