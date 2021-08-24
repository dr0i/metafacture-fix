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

import org.metafacture.framework.StreamReceiver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

/**
 * Tests Metafix field / record level methods. Following the cheat sheet
 * examples at https://github.com/LibreCat/Catmandu/wiki/Fixes-Cheat-Sheet
 *
 * @author Fabian Steeg
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class MetafixFieldTest {

    @RegisterExtension
    private MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StreamReceiver streamReceiver;

    public MetafixFieldTest() {
    }

    @Test
    public void set() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "set_field('my.name','patrick')",
                "set_field('your.name','nicolas')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
                //
                i.startRecord("2");
                i.startEntity("my");
                i.literal("name", "max");
                i.endEntity();
                i.startEntity("your");
                i.literal("name", "mo");
                i.endEntity();
                i.endRecord();
                //
                i.startRecord("3");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("my.name", "patrick");
                o.get().literal("your.name", "nicolas");
                o.get().endRecord();
                //
                o.get().startRecord("2");
                o.get().literal("my.name", "patrick");
                o.get().literal("your.name", "nicolas");
                o.get().endRecord();
                //
                o.get().startRecord("3");
                o.get().literal("my.name", "patrick");
                o.get().literal("your.name", "nicolas");
                o.get().endRecord();
            });
    }

    @Test
    public void add() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "add_field('my.name','nicolas')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
                //
                i.startRecord("2");
                i.startEntity("my");
                i.literal("name", "max");
                i.endEntity();
                i.endRecord();
                //
                i.startRecord("3");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("my.name", "nicolas");
                o.get().endRecord();
                //
                o.get().startRecord("2");
                o.get().literal("my.name", "max");
                o.get().literal("my.name", "nicolas");
                o.get().endRecord();
                //
                o.get().startRecord("3");
                o.get().literal("my.name", "nicolas");
                o.get().endRecord();
            });
    }

    @Test
    public void move() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "move_field('my.name','your.name')",
                "move_field('missing','whatever')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
                //
                i.startRecord("2");
                i.startEntity("my");
                i.literal("name", "max");
                i.endEntity();
                i.endRecord();
                //
                i.startRecord("3");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().endRecord();
                //
                o.get().startRecord("2");
                o.get().literal("your.name", "max");
                o.get().endRecord();
                //
                o.get().startRecord("3");
                o.get().endRecord();
            });
    }

    @Test
    public void copy() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "copy_field('your.name','your.name2')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
                //
                i.startRecord("2");
                i.startEntity("your");
                i.literal("name", "max");
                i.endEntity();
                i.endRecord();
                //
                i.startRecord("3");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().endRecord();
                //
                o.get().startRecord("2");
                o.get().literal("your.name", "max");
                o.get().literal("your.name2", "max");
                o.get().endRecord();
                //
                o.get().startRecord("3");
                o.get().endRecord();
            });
    }

    @Test
    public void remove() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "remove_field('your.name')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
                //
                i.startRecord("2");
                i.startEntity("your");
                i.literal("name", "max");
                i.endEntity();
                i.endRecord();
                //
                i.startRecord("3");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().endRecord();
                //
                o.get().startRecord("2");
                o.get().endRecord();
                //
                o.get().startRecord("3");
                o.get().endRecord();
            });
    }

    @Test
    public void setArray() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "set_array('foo','a','b','c')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("foo", "a");
                o.get().literal("foo", "b");
                o.get().literal("foo", "c");
                o.get().endRecord();
            });
    }

    @Test
    public void setHash() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "set_hash('foo','a': 'b','c': 'd')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().startEntity("foo");
                o.get().literal("a", "b");
                o.get().literal("c", "d");
                o.get().endEntity();
                o.get().endRecord();
            });
    }

    @Test
    public void paste() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "paste('my.string','a','b','c','d')",
                "remove_field('a','b','c','d')"), //
            i -> {
                i.startRecord("1");
                i.literal("a", "eeny");
                i.literal("b", "meeny");
                i.literal("c", "miny");
                i.literal("d", "moe");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("my.string", "eeny meeny miny moe");
                o.get().endRecord();
            });
    }

    @Test
    public void pasteWithCustomSep() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "paste('my.string','a','b','c','d','join_char': ', ')",
                "remove_field('a','b','c','d')"), //
            i -> {
                i.startRecord("1");
                i.literal("a", "eeny");
                i.literal("b", "meeny");
                i.literal("c", "miny");
                i.literal("d", "moe");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("my.string", "eeny, meeny, miny, moe");
                o.get().endRecord();
            });
    }

    @Test
    public void pasteWithLiteralStrings() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "paste('my.string','~Hi','a','~how are you?')",
                "remove_field('a','b','c','d')"), //
            i -> {
                i.startRecord("1");
                i.literal("a", "eeny");
                i.literal("b", "meeny");
                i.literal("c", "miny");
                i.literal("d", "moe");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("my.string", "Hi eeny how are you?");
                o.get().endRecord();
            });
    }

    @Test
    public void hashFromArray() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "set_array('foo','a','b','c','d')", //
                "hash('foo')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().startEntity("foo");
                o.get().literal("a", "b");
                o.get().literal("c", "d");
                o.get().endEntity();
                o.get().endRecord();
            });
    }

    @Test
    public void arrayFromHash() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "set_hash('foo','a': 'b','c': 'd')", //
                "array('foo')"), //
            i -> {
                i.startRecord("1");
                i.endRecord();
            }, o -> {
                o.get().startRecord("1");
                o.get().literal("foo", "a");
                o.get().literal("foo", "b");
                o.get().literal("foo", "c");
                o.get().literal("foo", "d");
                o.get().endRecord();
            });
    }

    @Test
    public void reject() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(//
                "if exists ('_metadata.error')", //
                "  reject()",
                "end"), //
            i -> {
                i.startRecord("1");
                i.literal("_metadata.error", "details");
                i.endRecord();
                i.startRecord("2");
                i.endRecord();
            }, o -> {
                o.get().startRecord("2");
                o.get().endRecord();
            });
    }
}
