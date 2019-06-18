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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.service.OperationCanceledError;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.metafacture.fix.FixStandaloneSetup;
import org.metafacture.fix.fix.Expression;
import org.metafacture.fix.fix.Fix;
import org.metafacture.fix.interpreter.FixInterpreter;
import org.metafacture.framework.StandardEventNames;
import org.metafacture.framework.StreamPipe;
import org.metafacture.framework.StreamReceiver;
import org.metafacture.framework.helpers.DefaultStreamReceiver;
import org.metafacture.mangling.StreamFlattener;
import org.metafacture.metamorph.api.FlushListener;
import org.metafacture.metamorph.api.InterceptorFactory;
import org.metafacture.metamorph.api.Maps;
import org.metafacture.metamorph.api.MorphErrorHandler;
import org.metafacture.metamorph.api.NamedValuePipe;
import org.metafacture.metamorph.api.NamedValueReceiver;
import org.metafacture.metamorph.api.NamedValueSource;
import org.metafacture.metamorph.api.SourceLocation;

import com.google.common.io.CharStreams;
import com.google.inject.Injector;

/**
 * Transforms a data stream send via the {@link StreamReceiver} interface. Use
 * {@link FixBuilder} to create an instance based on a Fix DSL description.
 *
 * @author Markus Michael Geipel (Metamorph)
 * @author Christoph Böhme (Metamorph)
 * @author Fabian Steeg (Metafix)
 */

public class Metafix implements StreamPipe<StreamReceiver>, NamedValuePipe, Maps {

	public static final String ELSE_KEYWORD = "_else";
	public static final char FEEDBACK_CHAR = '@';
	public static final char ESCAPE_CHAR = '\\';
	public static final String METADATA = "__meta";
	public static final String VAR_START = "$[";
	public static final String VAR_END = "]";

	private static final String ENTITIES_NOT_BALANCED = "Entity starts and ends are not balanced";

	private static final InterceptorFactory NULL_INTERCEPTOR_FACTORY = new NullInterceptorFactory();
	private static final Map<String, String> NO_VARS = Collections.emptyMap();

	private final Registry<NamedValueReceiver> dataRegistry = new WildcardRegistry<>();
	private final List<NamedValueReceiver> elseSources = new ArrayList<>();

	private final Map<String, Map<String, String>> maps = new HashMap<>();
	private final List<Closeable> resources = new ArrayList<>();
	private final StreamFlattener flattener = new StreamFlattener();

	private final Deque<Integer> entityCountStack = new LinkedList<>();
	private int entityCount;
	private int currentEntityCount;

	private StreamReceiver outputStreamReceiver;
	private MorphErrorHandler errorHandler = new DefaultErrorHandler();
	private int recordCount;
	private final List<FlushListener> recordEndListener = new ArrayList<>();

	public List<Expression> expressions = new ArrayList<>();

	public Metafix() {
		init();
	}

	public Metafix(final String fixDef) {
		this(fixDef, NO_VARS);
	}

	public Metafix(final String fixDef, final Map<String, String> vars) {
		this(fixDef, vars, NULL_INTERCEPTOR_FACTORY);
	}

	public Metafix(final String fixDef, final InterceptorFactory interceptorFactory) {
		this(fixDef, NO_VARS, interceptorFactory);
	}

	public Metafix(final String fixDef, final Map<String, String> vars, final InterceptorFactory interceptorFactory) {
		this(new StringReader(fixDef), vars, interceptorFactory);
	}

	public Metafix(final Reader morphDef) {
		this(morphDef, NO_VARS);
	}

	public Metafix(final Reader fixDef, final Map<String, String> vars) {
		this(fixDef, vars, NULL_INTERCEPTOR_FACTORY);
	}

	public Metafix(final Reader fixDef, final InterceptorFactory interceptorFactory) {
		this(fixDef, NO_VARS, interceptorFactory);
	}

	public Metafix(final Reader fixDef, final Map<String, String> vars, final InterceptorFactory interceptorFactory) {
		buildPipeline(fixDef, vars, interceptorFactory);
		init();
	}

	public Metafix(final InputStream fixDef) {
		this(fixDef, NO_VARS);
	}

	public Metafix(final InputStream fixDef, final Map<String, String> vars) {
		this(fixDef, vars, NULL_INTERCEPTOR_FACTORY);
	}

	public Metafix(final InputStream fixDef, final InterceptorFactory interceptorFactory) {
		this(fixDef, NO_VARS, interceptorFactory);
	}

	public Metafix(final InputStream fixDef, final Map<String, String> vars,
			final InterceptorFactory interceptorFactory) {
		this(new InputStreamReader(fixDef), vars, interceptorFactory);
	}

	private void buildPipeline(Reader fixDef, Map<String, String> vars, InterceptorFactory interceptorFactory) {
		Fix fix = parseFix(fixDef);
		// TODO: unify FixInterpreter and FixBuilder
		new FixInterpreter().run(this, fix);
		new FixBuilder(this, interceptorFactory).walk(fix, vars);
	}

	private Fix parseFix(Reader fixDef) throws OperationCanceledError {
		// TODO: do this only once per application
		Injector injector = new FixStandaloneSetup().createInjectorAndDoEMFRegistration();
		FixStandaloneSetup.doSetup();
		try {
			XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
			URI modelFileUri = URI.createFileURI(tempFile(fixDef).getAbsolutePath());
			Resource resource = resourceSet.getResource(modelFileUri, true);
			IResourceValidator validator = ((XtextResource) resource).getResourceServiceProvider()
					.getResourceValidator();
			List<Issue> issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
			for (Issue issue : issues) {
				System.err.println(issue.getMessage());
			}
			return (Fix) resource.getContents().get(0);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private File tempFile(Reader fixDef) throws IOException, FileNotFoundException {
		// TODO: avoid temp file creation
		File tmpFile = Files.createTempFile("test0_", ".fix").toFile();
		tmpFile.deleteOnExit();
		try (PrintWriter out = new PrintWriter(new FileWriter(tmpFile))) {
			out.println(CharStreams.toString(fixDef));
		}
		return tmpFile;
	}

	private void init() {
		flattener.setReceiver(new DefaultStreamReceiver() {
			@Override
			public void literal(final String name, final String value) {
				dispatch(name, value, getElseSources());
			}
		});
	}

	protected List<NamedValueReceiver> getElseSources() {
		return elseSources;
	}

	protected void setEntityMarker(final String entityMarker) {
		flattener.setEntityMarker(entityMarker);
	}

	public void setErrorHandler(final MorphErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	protected void registerNamedValueReceiver(final String source, final NamedValueReceiver data) {
		if (ELSE_KEYWORD.equals(source)) {
			elseSources.add(data);
		} else {
			dataRegistry.register(source, data);
		}
	}

	@Override
	public void startRecord(final String identifier) {
		flattener.startRecord(identifier);
		entityCountStack.clear();

		entityCount = 0;
		currentEntityCount = 0;

		++recordCount;
		recordCount %= Integer.MAX_VALUE;

		entityCountStack.add(Integer.valueOf(entityCount));

		final String identifierFinal = identifier;

		outputStreamReceiver.startRecord(identifierFinal);
		dispatch(StandardEventNames.ID, identifierFinal, null);
	}

	@Override
	public void endRecord() {

		for (final FlushListener listener : recordEndListener) {
			listener.flush(recordCount, currentEntityCount);
		}

		outputStreamReceiver.endRecord();
		entityCountStack.removeLast();
		if (!entityCountStack.isEmpty()) {
			throw new IllegalStateException(ENTITIES_NOT_BALANCED);
		}

		flattener.endRecord();
	}

	@Override
	public void startEntity(final String name) {
		if (name == null) {
			throw new IllegalArgumentException("Entity name must not be null.");
		}

		++entityCount;
		currentEntityCount = entityCount;
		entityCountStack.push(Integer.valueOf(entityCount));

		flattener.startEntity(name);

	}

	@Override
	public void endEntity() {
		dispatch(flattener.getCurrentPath(), "", null);
		currentEntityCount = entityCountStack.pop().intValue();
		flattener.endEntity();

	}

	@Override
	public void literal(final String name, final String value) {
		flattener.literal(name, value);

	}

	@Override
	public void resetStream() {
		// TODO: Implement proper reset handling
		outputStreamReceiver.resetStream();
	}

	@Override
	public void closeStream() {
		for (final Closeable closeable : resources) {
			try {
				closeable.close();
			} catch (final IOException e) {
				errorHandler.error(e);
			}
		}
		outputStreamReceiver.closeStream();
	}

	protected void dispatch(final String path, final String value, final List<NamedValueReceiver> fallback) {
		final List<NamedValueReceiver> matchingData = findMatchingData(path, fallback);
		if (null != matchingData) {
			send(path, value, matchingData);
		}
	}

	private List<NamedValueReceiver> findMatchingData(final String path, final List<NamedValueReceiver> fallback) {
		final List<NamedValueReceiver> matchingData = dataRegistry.get(path);
		if (matchingData == null || matchingData.isEmpty()) {
			return fallback;
		}
		return matchingData;
	}

	private void send(final String key, final String value, final List<NamedValueReceiver> dataList) {
		for (final NamedValueReceiver data : dataList) {
			try {
				data.receive(key, value, null, recordCount, currentEntityCount);
			} catch (final RuntimeException e) {
				errorHandler.error(e);
			}
		}
	}

	/**
	 * @param streamReceiver
	 *                           the outputHandler to set
	 */
	@Override
	public <R extends StreamReceiver> R setReceiver(final R streamReceiver) {
		if (streamReceiver == null) {
			throw new IllegalArgumentException("'streamReceiver' must not be null");
		}
		this.outputStreamReceiver = streamReceiver;
		return streamReceiver;
	}

	public StreamReceiver getStreamReceiver() {
		return outputStreamReceiver;
	}

	@Override
	public void receive(final String name, final String value, final NamedValueSource source, final int recordCount,
			final int entityCount) {
		if (null == name) {
			throw new IllegalArgumentException(
					"encountered literal with name='null'. This indicates a bug in a function or a collector.");
		}

		if (name.length() != 0 && name.charAt(0) == FEEDBACK_CHAR) {
			dispatch(name, value, null);
			return;
		}

		String unescapedName = name;
		if (name.length() > 1 && name.charAt(0) == ESCAPE_CHAR
				&& (name.charAt(1) == FEEDBACK_CHAR || name.charAt(1) == ESCAPE_CHAR)) {
			unescapedName = name.substring(1);
		}
		outputStreamReceiver.literal(unescapedName, value);
	}

	@Override
	public Map<String, String> getMap(final String mapName) {
		return maps.getOrDefault(mapName, Collections.emptyMap());
	}

	@Override
	public String getValue(final String mapName, final String key) {
		final Map<String, String> map = getMap(mapName);
		if (map.containsKey(key)) {
			return map.get(key);
		}
		return map.get(Maps.DEFAULT_MAP_KEY);
	}

	@Override
	public Map<String, String> putMap(final String mapName, final Map<String, String> map) {
		if (map instanceof Closeable) {
			final Closeable closable = (Closeable) map;
			resources.add(closable);
		}
		return maps.put(mapName, map);
	}

	@Override
	public String putValue(final String mapName, final String key, final String value) {
		return maps.computeIfAbsent(mapName, k -> new HashMap<>()).put(key, value);
	}

	@Override
	public Collection<String> getMapNames() {
		return Collections.unmodifiableSet(maps.keySet());
	}

	public void registerRecordEndFlush(final FlushListener flushListener) {
		recordEndListener.add(flushListener);
	}

	@Override
	public void addNamedValueSource(final NamedValueSource namedValueSource) {
		namedValueSource.setNamedValueReceiver(this);
	}

	@Override
	public void setNamedValueReceiver(final NamedValueReceiver receiver) {
		throw new UnsupportedOperationException("The Metafix object cannot act as a NamedValueSender");
	}

	@Override
	public void setSourceLocation(final SourceLocation sourceLocation) {
		// Nothing to do
		// Metafix does not have a source location (we could
		// in theory use the location of the module in a flux
		// script)
	}

	@Override
	public SourceLocation getSourceLocation() {
		// Metafix does not have a source location
		return null;
	}

}