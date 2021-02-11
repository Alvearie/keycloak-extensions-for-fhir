/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak.config.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

/**
 * This class represents a collection of properties - a property group. This could be the entire set of properties
 * resulting from loading the configuration, or it could be just a sub-structure within the overall config hierarchy, as
 * a property group can contain other property groups. Internally, there is a JsonObject which holds the actual group of
 * properties and this class provides a high-level API for accessing properties in a hierarchical manner.
 */
public class PropertyGroup {

    /**
     * This constant represents the separator character used within a hierarchical property name.
     * Example:
     * <code>keycloak|realms|tenant1|clients</code>
     */
    public static final String PATH_ELEMENT_SEPARATOR = "|";
    private JsonObject jsonObj;

    /**
     * Instantiates the property group.
     * @param jsonObj the JSON object
     */
    public PropertyGroup(JsonObject jsonObj) {
        this.jsonObj = jsonObj;
    }

    /**
     * Returns a PropertyGroup associated with the specified property.
     *
     * @param propertyName
     *            a hierarchical property name (e.g. "level1|level2|level3") that refers to a property group.
     * @return a PropertyGroup that holds the sub-structure associated with the specified property.
     */
    public PropertyGroup getPropertyGroup(String propertyName) {
        PropertyGroup result = null;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            if (jsonValue instanceof JsonObject) {
                result = new PropertyGroup((JsonObject) jsonValue);
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be of type object (JsonObject)");
            }
        }
        return result;
    }

    /**
     * Returns the value of the specified String property or null if it wasn't found.
     * If the value is encoded, then it will be decoded.
     *
     * @param propertyName
     *            the name of the property to retrieved
     * @throws Exception
     *             an exception
     */
    public String getStringProperty(String propertyName) throws Exception {
        return getStringProperty(propertyName, null);
    }

    /**
     * Returns the value of the specified String property. If not found, then
     * 'defaultValue' is returned instead.
     * If the value is encoded, then it will be decoded.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @throws Exception
     *             an exception
     */
    public String getStringProperty(String propertyName, String defaultValue) throws Exception {
        String result = defaultValue;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            if (jsonValue instanceof JsonString) {
                result = decode(((JsonString) jsonValue).getString());
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be of type String");
            }
        }
        return result;
    }

    /**
     * This is a convenience function that will retrieve an array property, then convert it
     * to a list of Strings by calling toString() on each array element.
     *
     * @param value
     *            the value to convert to list of strings
     * @return a List<String> containing the elements array.
     * @throws Exception
     */
    public static List<String> convertToStringList(Object value) throws Exception {
        List<String> strings = null;
        if (value != null) {
            strings = new ArrayList<String>();
            if (value instanceof List) {
                List<?> valueList = (List<?>) value;
                for (int i = 0; i < valueList.size(); i++) {
                    strings.add((String) valueList.get(i).toString());
                }
            }
            else {
                strings.add((String) value.toString());
            }
        }
        return strings;
    }

    /**
     * This is a convenience function that will retrieve an array property, then convert it
     * to a list of Strings by calling toString() on each array element.
     *
     * @param propertyName
     *            the name of the property to retrive
     * @return a List<String> containing the elements from the JSON array property.
     * @throws Exception
     */
    public List<String> getStringListProperty(String propertyName) throws Exception {
        Object[] array = getArrayProperty(propertyName);
        List<String> strings = null;
        if (array != null) {
            strings = new ArrayList<String>();
            for (int i = 0; i < array.length; i++) {
                strings.add((String) array[i].toString());
            }
        }
        return strings;
    }

    /**
     * Returns the value of the specified int property or null if it wasn't found.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Integer getIntProperty(String propertyName) {
        return getIntProperty(propertyName, null);
    }

    /**
     * Returns the value of the specified int property. If not found, then
     * 'defaultValue' is returned instead.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Integer getIntProperty(String propertyName, Integer defaultValue) {
        Integer result = defaultValue;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            if (jsonValue instanceof JsonNumber) {
                result = Integer.valueOf(((JsonNumber) jsonValue).intValue());
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be of type int");
            }
        }
        return result;
    }

    /**
     * Returns the value of the specified double property or null if it wasn't found.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Double getDoubleProperty(String propertyName) {
        return getDoubleProperty(propertyName, null);
    }

    /**
     * Returns the value of the specified double property. If not found, then
     * 'defaultValue' is returned instead.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Double getDoubleProperty(String propertyName, Double defaultValue) {
        Double result = defaultValue;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            if (jsonValue instanceof JsonNumber) {
                result = Double.valueOf(((JsonNumber) jsonValue).doubleValue());
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be of type double");
            }
        }
        return result;
    }

    /**
     * Returns the value of the specified boolean property or null if it wasn't found.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Boolean getBooleanProperty(String propertyName) {
        return getBooleanProperty(propertyName, null);
    }

    /**
     * Returns the value of the specified boolean property. If not found, then
     * 'defaultValue' is returned instead.
     *
     * @param propertyName
     *            the name of the property to retrieve
     */
    public Boolean getBooleanProperty(String propertyName, Boolean defaultValue) {
        Boolean result = defaultValue;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            // If the value is stored in the JSON object as a boolean, then
            // construct the result from that.
            if (jsonValue == JsonValue.TRUE || jsonValue == JsonValue.FALSE) {
                result = Boolean.valueOf(jsonValue == JsonValue.TRUE);
            }

            // Otherwise, if the value was actually a string (i.e. "true" or "false"),
            // then construct the boolean result from the string.
            else if (jsonValue instanceof JsonString) {
                result = Boolean.valueOf(((JsonString) jsonValue).getString());
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be of type boolean or String");
            }
        }
        return result;
    }

    /**
     * Returns the value (as an array of Object) of the specified array property.
     * Each element of the returned array will be an instance of Boolean, Integer, Double, String
     * or PropertyGroup, depending on the value type associated with the property within the
     * underlying JsonObject.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @return
     * @throws Exception
     */
    public Object[] getArrayProperty(String propertyName) throws Exception {
        Object[] result = null;
        JsonValue jsonValue = getJsonValue(propertyName);
        if (jsonValue != null) {
            if (jsonValue instanceof JsonArray) {
                result = convertJsonArray((JsonArray) jsonValue);
            } else {
                throw new IllegalArgumentException("Property '" + propertyName + "' must be an array");
            }
        }
        return result;
    }

    /**
     * Returns the properties contained in the PropertyGroup in the form of a list of
     * PropertyEntry instances. If no properties exist, then an empty list will be returned.
     *
     * @throws Exception
     */
    public List<PropertyEntry> getProperties() throws Exception {
        List<PropertyEntry> results = new ArrayList<>();
        for (Map.Entry<String, JsonValue> entry : jsonObj.entrySet()) {
            results.add(new PropertyEntry(entry.getKey(), convertJsonValue(entry.getValue())));
        }
        return results;
    }

    /**
     * Returns the String representation of the PropertyGroup instance.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PropertyGroup[");
        sb.append(jsonObj != null ? jsonObj.toString() : "<empty>");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Converts the specified JsonValue into the appropriate java.lang.* type.
     *
     * @param jsonValue
     *            the JsonValue instance to be converted
     * @return an instance of Boolean, Integer, String, PropertyGroup, or List<Object>
     * @throws Exception
     */
    public static Object convertJsonValue(JsonValue jsonValue) throws Exception {
        Object result = null;
        switch (jsonValue.getValueType()) {
        case ARRAY:
            Object[] objArray = convertJsonArray((JsonArray) jsonValue);
            result = Arrays.asList(objArray);
            break;
        case OBJECT:
            result = new PropertyGroup((JsonObject) jsonValue);
            break;
        case STRING:
            result = decode(((JsonString) jsonValue).getString());
            break;
        case NUMBER:
            JsonNumber jsonNumber = (JsonNumber) jsonValue;
            if (jsonNumber.isIntegral()) {
                result = Integer.valueOf(jsonNumber.intValue());
            } else {
                result = Double.valueOf(jsonNumber.doubleValue());
            }
            break;
        case TRUE:
            result = Boolean.TRUE;
            break;
        case FALSE:
            result = Boolean.FALSE;
            break;
        default:
            throw new IllegalStateException("Unexpected JSON value type: " + jsonValue.getValueType().name());
        }
        return result;
    }

    /**
     * Converts the specified JsonArray into an Object[]
     *
     * @param jsonArray
     *            the JsonArray to be converted
     * @return an Object[] containing the converted values found in the JsonArray
     * @throws Exception
     */
    private static Object[] convertJsonArray(JsonArray jsonArray) throws Exception {
        Object[] result = new Object[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = convertJsonValue(jsonArray.get(i));
        }
        return result;
    }

    /**
     * Finds the specified property and returns it as a generic JsonValue.
     *
     * @param propertyName
     *            the possibly hierarchical property name.
     */
    public JsonValue getJsonValue(String propertyName) {
        String[] pathElements = getPathElements(propertyName);
        JsonObject subGroup = getPropertySubGroup(pathElements);
        JsonValue result = null;
        if (subGroup != null) {
            result = subGroup.get(pathElements[pathElements.length - 1]);
        }
        return result;
    }

    /**
     * Splits a potentially hierarchical property name into the individual path elements
     *
     * @param propertyName
     *            a hierarchical property name (e.g. "level1|level2|myProperty"
     * @return
     */
    private String[] getPathElements(String propertyName) {
        return propertyName.split("\\" + PATH_ELEMENT_SEPARATOR);
    }

    /**
     * This function will find the JSON "sub object" rooted at "this.jsonObj" that is associated with the specified
     * hierarchical property name.
     * <p>
     * For example, consider the following JSON structure:
     *
     * <pre>
     * {
     *     "level1":{
     *         "level2":{
     *             "myProperty":"myValue"
     *         }
     *     }
     * }
     * </pre>
     *
     * If this function was invoked with a property name of "level1|level2|myProperty",
     * then the result will be the JsonObject associated with the "level2" field within the JSON
     * structure above.
     *
     * @param pathElements
     *            an array of path elements that make up the hierarchical property name (e.g. {"level1", "level2",
     *            "myProperty"})
     * @return the JsonObject sub-structure that contains the specified property.
     */
    private JsonObject getPropertySubGroup(String[] pathElements) {
        if (pathElements != null) {
            JsonObject cursor = this.jsonObj;
            int limit = pathElements.length - 1;
            for (int i = 0; i < limit; i++) {
                cursor = cursor.getJsonObject(pathElements[i]);
                if (cursor == null) {
                    break;
                }
            }
            return cursor;
        }

        return null;
    }

    /**
     * This class represents a single property contained within a PropertyGroup.
     */
    public static class PropertyEntry {

        private String name;
        private Object value;

        public PropertyEntry(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * This function can be used to decode an xor-encoded value that was produced by
     * the WebSphere Liberty 'securityUtility' command.
     *
     * @param encodedString
     *            the encoded string to be decoded
     * @return the decoded version of the input string
     * @throws Exception
     */
    private static String decode(String encodedString) throws Exception {
        String decodedString = null;
        if (isEncoded(encodedString)) {
            String withoutTag = encodedString.substring(5);
            byte[] bytes = withoutTag.getBytes("UTF-8");
            byte[] decodedBytes = Base64.getDecoder().decode(bytes);
            byte[] xor_bytes = new byte[decodedBytes.length];
            for (int i = 0; i < decodedBytes.length; i++) {
                xor_bytes[i] = (byte) (0x5F ^ decodedBytes[i]);
            }
            decodedString = new String(xor_bytes, "UTF-8");
        } else {
            decodedString = encodedString;
        }
        return decodedString;
    }

    /**
     * Returns true if and only if the specified string 's' is an encoded value,
     * which means it starts with the string "{xor}".
     *
     * @param s
     *            the string value to check
     */
    private static boolean isEncoded(String s) {
        return s != null && s.startsWith("{xor}");
    }
}
