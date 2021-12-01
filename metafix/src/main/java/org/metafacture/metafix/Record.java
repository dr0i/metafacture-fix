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

/**
 * Represents a metadata record, i.e., a {@link Value.Hash Hash} of fields
 * and values.
 */
public class Record extends Value.Hash {

    private final Map<String, Value> virtualFields = new LinkedHashMap<>();

    private boolean reject;

    /**
     * Creates an empty instance of {@link Record}.
     */
    public Record() {
    }

    /**
     * Returns a shallow clone of this record.
     *
     * @return a new record pre-populated with all entries from this record
     */
    public Record shallowClone() {
        final Record clone = new Record();

        clone.setReject(reject);
        forEach(clone::put);
        virtualFields.forEach(clone::putVirtualField);

        return clone;
    }

    /**
     * Flags whether this record should be rejected.
     *
     * @param reject true if this record should not be emitted, false otherwise
     */
    public void setReject(final boolean reject) {
        this.reject = reject;
    }

    /**
     * Checks whether this record should be rejected.
     *
     * @return true if this record should not be emitted, false otherwise
     */
    public boolean getReject() {
        return reject;
    }

    /**
     * Checks whether this record contains the <i>virtual</i> field.
     *
     * @param field the field name
     * @return true if this record contains the <i>virtual</i> field, false otherwise
     */
    public boolean containsVirtualField(final String field) {
        return virtualFields.containsKey(field);
    }

    /**
     * Adds a <i>virtual</i> field/value pair to this record, provided it's not
     * {@link Value#isNull(Value) null}. Virtual fields can be
     * {@link #get(String) accessed} like regular metadata fields, but aren't
     * {@link #forEach(BiConsumer) emitted} by default.
     *
     * @param field the field name
     * @param value the metadata value
     *
     * @see #retainFields(Collection)
     */
    public void putVirtualField(final String field, final Value value) {
        if (!Value.isNull(value)) {
            virtualFields.put(field, value);
        }
    }

    @Override
    public String toString() {
        // TODO: Improve string representation? Include reject status, virtual fields, etc.?
        return super.toString();
    }

    /**
     * Retrieves the field value from this record. Falls back to retrieving the
     * <i>virtual</i> field if the field name is not already
     * {@link #containsField(String) present}.
     *
     * @param field the field name
     * @return the metadata value
     */
    @Override
    public Value get(final String field) {
        return containsField(field) ? super.get(field) : virtualFields.get(field);
    }

    /**
     * Retains only the given field/value pairs in this record. Turns
     * <i>virtual</i> fields into regular metadata fields if they're not already
     * {@link #containsField(String) present}.
     *
     * @param fields the field names
     */
    @Override
    public void retainFields(final Collection<String> fields) {
        virtualFields.keySet().retainAll(fields);

        virtualFields.forEach((f, v) -> {
            if (!containsField(f)) {
                put(f, v);
            }
        });

        super.retainFields(fields);
    }

}
