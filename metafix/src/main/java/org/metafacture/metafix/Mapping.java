/*
 * Copyright 2021 hbz NRW
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Represents a mapping of metadata fields and values.
 */
public class Mapping {

    private static final String EMPTY = "";

    private final Map<String, Object> map = new LinkedHashMap<>();

    /**
     * Creates an empty instance of {@link Mapping}.
     */
    public Mapping() {
    }

    /**
     * Checks whether this mapping contains the metadata field.
     *
     * @param field the field name
     * @return true if this mapping contains the metadata field, false otherwise
     */
    public boolean containsField(final String field) {
        return map.containsKey(field);
    }

    /**
     * Checks whether this mapping is empty.
     *
     * @return true if this mapping is empty, false otherwise
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Gets the number of field/value pairs in this mapping.
     *
     * @return the number of field/value pairs in this mapping
     */
    public int size() {
        return map.size();
    }

    /**
     * Adds a field/value pair to this mapping, provided it's not {@code null}.
     *
     * @param field the field name
     * @param value the metadata value
     */
    public void put(final String field, final Object value) {
        if (value != null) {
            map.put(field, value);
        }
    }

    /**
     * {@link #put(String, Object) Replaces} a field/value pair in this mapping,
     * provided the field name is already {@link #containsField(String) present}.
     *
     * @param field the field name
     * @param value the metadata value
     */
    public void replace(final String field, final Object value) {
        if (containsField(field)) {
            put(field, value);
        }
    }

    /**
     * Retrieves the field value from this mapping.
     *
     * @param field the field name
     * @return the metadata value
     */
    public Object get(final String field) {
        return map.get(field);
    }

    /**
     * Removes the given field/value pair from this mapping.
     *
     * @param field the field name
     */
    public void remove(final String field) {
        map.remove(field);
    }

    /**
     * Retains only the given field/value pairs in this mapping.
     *
     * @param fields the field names
     */
    public void retainFields(final Collection<String> fields) {
        map.keySet().retainAll(fields);
    }

    /**
     * Removes all field/value pairs from this mapping whose value is empty.
     */
    public void removeEmptyValues() {
        map.values().removeIf(EMPTY::equals);
    }

    /**
     * Iterates over all field/value pairs in this mapping.
     *
     * @param consumer the action to be performed for each field/value pair
     */
    public void forEach(final BiConsumer<String, Object> consumer) {
        map.forEach(consumer);
    }

    @Override
    public String toString() {
        return map.toString();
    }

    // TODO: Replace map accesses with record operations!
    public Map<String, Object> temporarilyGetMap() {
        return map;
    }

}
