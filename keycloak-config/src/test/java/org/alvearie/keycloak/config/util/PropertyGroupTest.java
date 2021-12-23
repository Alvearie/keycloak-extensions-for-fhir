/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.keycloak.config.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.alvearie.keycloak.config.util.PropertyGroup.PropertyEntry;
import org.junit.BeforeClass;
import org.junit.Test;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;

public class PropertyGroupTest {
    private static final JsonBuilderFactory BUILDER_FACTORY = Json.createBuilderFactory(null);
    private static JsonObject obj = null;

    private static boolean DEBUG = true;

    @BeforeClass
    public static void setup() {
        // Build a JSON object for testing.
        obj = BUILDER_FACTORY.createObjectBuilder()
                .add("level1", BUILDER_FACTORY.createObjectBuilder()
                    .add("level2", BUILDER_FACTORY.createObjectBuilder()
                        .add("scalars", BUILDER_FACTORY.createObjectBuilder()
                            .add("stringProp", "stringValue")
                            .add("intProp", 123)
                            .add("booleanProp", true)
                            .add("booleanProp-2", "true"))
                        .add("arrays", BUILDER_FACTORY.createObjectBuilder()
                            .add("int-array", BUILDER_FACTORY.createArrayBuilder()
                                    .add(1)
                                    .add(2)
                                    .add(3))
                            .add("string-array", BUILDER_FACTORY.createArrayBuilder()
                                .add("one")
                                .add("two"))
                            .add("object-array", BUILDER_FACTORY.createArrayBuilder()
                                .add(BUILDER_FACTORY.createObjectBuilder()
                                    .add("attr1", "val1"))
                                .add(BUILDER_FACTORY.createObjectBuilder()
                                    .add("attr2", "val2"))))
                        .add("nulls", BUILDER_FACTORY.createObjectBuilder()
                            .add("nullProp", JsonValue.NULL)
                            .add("nullArrayProp", BUILDER_FACTORY.createArrayBuilder()
                                .add(JsonValue.NULL)))))
                .build();

        if (DEBUG) {
            Map<String, Object> config = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);
            JsonGeneratorFactory factory = Json.createGeneratorFactory(config);
            JsonGenerator generator = factory.createGenerator(System.out);
            generator.write(obj);
            generator.flush();
            System.out.println();
        }
    }

    @Test
    public void testGetPropertyGroup() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);

        PropertyGroup scalars = pg.getPropertyGroup("level1|level2|scalars");
        assertNotNull(scalars);
        PropertyGroup result = scalars.getPropertyGroup("scalars");
        assertNull(result);
        String value = scalars.getStringProperty("stringProp");
        assertNotNull(value);
        assertEquals("stringValue", value);
    }

    @Test
    public void testStringProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        String value = pg.getStringProperty("level1|level2|scalars|stringProp");
        assertNotNull(value);
        assertEquals("stringValue", value);
    }

    @Test
    public void testIntProperty() {
        PropertyGroup pg = new PropertyGroup(obj);
        Integer value = pg.getIntProperty("level1|level2|scalars|intProp");
        assertNotNull(value);
        assertEquals(123, value.intValue());
    }

    @Test
    public void testBooleanProperty() {
        PropertyGroup pg = new PropertyGroup(obj);
        Boolean value = pg.getBooleanProperty("level1|level2|scalars|booleanProp");
        assertNotNull(value);
        assertEquals(Boolean.TRUE, value);

        value = pg.getBooleanProperty("level1|level2|scalars|booleanProp-2");
        assertNotNull(value);
        assertEquals(Boolean.TRUE, value);
    }

    @Test
    public void testArrayProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        Object[] array = pg.getArrayProperty("level1|level2|arrays|string-array");
        assertNotNull(array);
        assertEquals(2, array.length);
        assertEquals("one", array[0]);
        assertEquals("two", array[1]);
    }

    @Test
    public void testStringListProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        List<String> strings = pg.getStringListProperty("level1|level2|arrays|string-array");
        assertNotNull(strings);
        assertEquals(2, strings.size());
        assertEquals("one", strings.get(0));
        assertEquals("two", strings.get(1));

        strings = pg.getStringListProperty("level1|level2|arrays|int-array");
        assertNotNull(strings);
        assertEquals(3, strings.size());
        assertEquals("1", strings.get(0));
        assertEquals("2", strings.get(1));
        assertEquals("3", strings.get(2));
    }

    @Test
    public void testObjectArrayProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        Object[] array = pg.getArrayProperty("level1|level2|arrays|object-array");
        assertNotNull(array);
        assertEquals(2, array.length);
        if (!(array[0] instanceof PropertyGroup)) {
            fail("array element 0 not a PropertyGroup!");
        }
        if (!(array[1] instanceof PropertyGroup)) {
            fail("array element 1 not a PropertyGroup!");
        }

        // Check the first element.
        PropertyGroup pg0 = (PropertyGroup) array[0];
        String val1 = pg0.getStringProperty("attr1");
        assertNotNull(val1);
        assertEquals("val1", val1);

        // Check the second element.
        PropertyGroup pg1 = (PropertyGroup) array[1];
        String val2 = pg1.getStringProperty("attr2");
        assertNotNull(val2);
        assertEquals("val2", val2);
    }

    @Test
    public void testNullProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        assertNull(pg.getJsonValue("level1|level2|nulls|nullProp"));
        assertNull(pg.getStringProperty("level1|level2|nulls|nullProp"));
        assertNull(pg.getBooleanProperty("level1|level2|nulls|nullProp"));
        assertNull(pg.getIntProperty("level1|level2|nulls|nullProp"));
        assertNull(pg.getDoubleProperty("level1|level2|nulls|nullProp"));
        assertNull(pg.getStringListProperty("level1|level2|nulls|nullProp"));
    }

    @Test
    public void testNonExistentProperty() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        assertNull(pg.getJsonValue("bogus"));
        assertNull(pg.getStringProperty("bogus"));
        assertNull(pg.getBooleanProperty("bogus"));
        assertNull(pg.getIntProperty("bogus"));
        assertNull(pg.getDoubleProperty("bogus"));
        assertNull(pg.getStringListProperty("bogus"));

        assertNull(pg.getJsonValue("level1|bogus"));
        assertNull(pg.getStringProperty("level1|bogus"));
        assertNull(pg.getBooleanProperty("level1|bogus"));
        assertNull(pg.getIntProperty("level1|bogus"));
        assertNull(pg.getDoubleProperty("level1|bogus"));
        assertNull(pg.getStringListProperty("level1|bogus"));

        assertNull(pg.getJsonValue("bogus|bogus"));
        assertNull(pg.getStringProperty("bogus|bogus"));
        assertNull(pg.getBooleanProperty("bogus|bogus"));
        assertNull(pg.getIntProperty("bogus|bogus"));
        assertNull(pg.getDoubleProperty("bogus|bogus"));
        assertNull(pg.getStringListProperty("bogus|bogus"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStringPropertyException() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        Object result = pg.getStringProperty("level1|level2|scalars|intProp");
        System.err.println("Unexpected result: " + result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntPropertyException() {
        PropertyGroup pg = new PropertyGroup(obj);
        Object result = pg.getIntProperty("level1|level2|scalars|stringProp");
        System.err.println("Unexpected result: " + result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBooleanPropertyException() {
        PropertyGroup pg = new PropertyGroup(obj);
        Object result = pg.getBooleanProperty("level1|level2|scalars|intProp");
        System.err.println("Unexpected result: " + result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayPropertyException() throws Exception {
        PropertyGroup pg = new PropertyGroup(obj);
        Object result = pg.getArrayProperty("level1|level2");
        System.err.println("Unexpected result: " + result);
    }
}
