/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.templating;

import com.mitchellbosecke.pebble.extension.AbstractExtension;
import com.mitchellbosecke.pebble.extension.Function;

import java.util.HashMap;
import java.util.Map;

public class SeedStackExtension extends AbstractExtension {
    private final String packageName;

    public SeedStackExtension(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
        functions.put("yamlClassConfig", new YamlClassConfig(packageName));
        return functions;
    }
}
