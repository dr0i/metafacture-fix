/*
 * Copyright 2022 hbz NRW
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

package org.metafacture.metafix.api;

import org.metafacture.metafix.Metafix;
import org.metafacture.metafix.Record;
import org.metafacture.metafix.Value;
import org.metafacture.metafix.maps.RdfMap;
import org.metafacture.metamorph.api.Maps;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@FunctionalInterface
public interface FixFunction {

    void apply(Metafix metafix, Record record, List<String> params, Map<String, String> options);

    default void withOption(final Map<String, String> options, final String key, final Consumer<String> consumer) {
        if (options.containsKey(key)) {
            consumer.accept(options.get(key));
        }
    }

    default boolean getBoolean(final Map<String, String> options, final String key) {
        return Boolean.parseBoolean(options.get(key));
    }

    default int getInteger(final List<String> params, final int index) {
        return Integer.parseInt(params.get(index));
    }

    default Value newArray(final Stream<Value> stream) {
        return Value.newArray(a -> stream.forEach(a::add));
    }

    default Stream<Value> unique(final Stream<Value> stream) {
        final Set<Value> set = new HashSet<>();
        return stream.filter(set::add);
    }

    default Stream<Value> flatten(final Stream<Value> stream) {
        return stream.flatMap(v -> v.extractType((m, c) -> m
                    .ifArray(a -> c.accept(flatten(a.stream())))
                    .orElse(w -> c.accept(Stream.of(w)))
        ));
    }

    default void lookup(final Metafix metafix, final Record record, final List<String> params, final Map<String, String> options, final FixFunction mapFunction) {
        final Map<String, String> map = getMap(metafix, record, params, options, mapFunction);
        record.transform(params.get(0), oldValue -> {
            final String newValue = map.getOrDefault(oldValue, map.get(Maps.DEFAULT_MAP_KEY));
            return newValue != null ? newValue : getBoolean(options, "delete") ? null : oldValue;
        });
    }

    static Map<String, String> getMap(final Metafix metafix, final Record record, final List<String> params, final Map<String, String> options, final FixFunction mapFunction) {
        final Map<String, String> map;
        if (params.size() <= 1) {
            map = options;
        }
        else {
            final String mapName = mapFunction.toString().equals("put_rdfmap") ? (params.size() > 1 ? params.get(1) : params.get(0)) + options.get(RdfMap.TARGET) + options.getOrDefault(RdfMap.TARGET_LANGUAGE, "") : params.get(1);
            if (!metafix.getMapNames().contains(mapName)) {
                if (mapName.contains(".") || mapName.contains(File.separator)) {
                    mapFunction.apply(metafix, record, Arrays.asList(mapName), options);
                }
                else {
                    // Probably an unknown internal map? Log a warning?
                }
            }
            map = metafix.getMap(mapName);
        }
        return map;
    }

}
