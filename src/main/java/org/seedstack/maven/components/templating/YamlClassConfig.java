/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.templating;

import com.mitchellbosecke.pebble.extension.Function;

import java.util.List;
import java.util.Map;

public class YamlClassConfig implements Function {
    private final String packageName;

    YamlClassConfig(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public Object execute(Map<String, Object> args) {
        StringBuilder sb = new StringBuilder("classes:\n");
        String indent = "  ";
        for (String part : packageName.split("\\.")) {
            sb.append(indent).append(part).append(":\n");
            indent = indent + "  ";
        }
        int index = 0;
        String key = "";
        for (Object arg : args.values()) {
            if (index % 2 == 0) {
                key = String.valueOf(arg);
            } else {
                sb.append(indent).append(key).append(": ").append(String.valueOf(arg.toString())).append("\n");
            }
            index++;
        }
        return sb.toString();
    }

    @Override
    public List<String> getArgumentNames() {
        return null;
    }
}
