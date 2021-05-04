/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.prompter;

import java.util.ArrayList;
import java.util.List;

public class Value implements Comparable<Value> {
    private String label;
    private String name;
    private Character key;
    private boolean separator;

    public Value() {
    }

    public Value(String label) {
        this.label = label;
        this.name = label;
        this.key = null;
        this.separator = false;
    }

    public Value(String label, String name) {
        this.label = label;
        this.name = name;
        this.key = null;
        this.separator = false;
    }

    public Value(String label, String name, Character key) {
        this.label = label;
        this.name = name;
        this.key = key;
        this.separator = false;
    }

    public Value(String label, boolean separator) {
        this.label = label;
        this.name = label;
        this.key = null;
        this.separator = separator;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Character getKey() {
        return key;
    }

    public void setKey(Character key) {
        this.key = key;
    }

    public boolean isSeparator() {
        return separator;
    }

    public void setSeparator(boolean separator) {
        this.separator = separator;
    }

    public static List<Value> convertStrings(List<String> values) {
        List<Value> result = new ArrayList<>();
        for (String value : values) {
            result.add(new Value(value));
        }
        return result;
    }

    public static List<String> convertValues(List<Value> values) {
        List<String> result = new ArrayList<>();
        for (Value value : values) {
            result.add(value.getName());
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Value value = (Value) o;

        return name.equals(value.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(Value o) {
        return this.name.compareTo(o.name);
    }
}

