/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.prompter;

import java.util.ArrayList;
import java.util.List;

public class Value {
    private String name;
    private String label;
    private Character key;
    private boolean separator;

    public Value() {
    }

    public Value(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public static List<Value> convertList(List<String> values) {
        List<Value> result = new ArrayList<>();
        for (String value : values) {
            result.add(new Value(value));
        }
        return result;
    }
}

