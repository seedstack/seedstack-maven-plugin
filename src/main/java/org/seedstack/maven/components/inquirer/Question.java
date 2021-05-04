/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.seedstack.maven.components.prompter.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Question {
    private String name;
    private Style style;
    private String message;
    private List<Condition> conditions = new ArrayList<>();
    private List<Value> values = new ArrayList<>();
    @JsonProperty("default")
    private String defaultValue;
    private Type type = Type.STRING;

    public Question() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = new ArrayList<>(conditions);
    }

    public List<Value> getValues() {
        return Collections.unmodifiableList(values);
    }

    public void setValues(List<Value> values) {
        this.values = new ArrayList<>(values);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        STRING,
        BOOLEAN,
        INTEGER,
        DOUBLE;

        @JsonCreator
        public static Type fromString(String key) {
            for (Type type : Type.values()) {
                if (type.name().equalsIgnoreCase(key)) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum Style {
        INPUT,
        CHOICE,
        CHECKBOX,
        LIST,
        CONFIRM;

        @JsonCreator
        public static Style fromString(String key) {
            for (Style style : Style.values()) {
                if (style.name().equalsIgnoreCase(key)) {
                    return style;
                }
            }
            return null;
        }
    }
}
