/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.templating;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class YamlClassConfigTest {
    @Test
    public void yamlClassConfig() throws Exception {
        YamlClassConfig yamlClassConfig = new YamlClassConfig("org.seedstack.some.demo");
        Map<String, Object> args = new HashMap<>();
        args.put("0", "someAttribute");
        args.put("1", "someValue");
        args.put("2", "otherAttribute");
        args.put("3", "otherValue");
        assertEquals("classes:\n" +
                "  org:\n" +
                "    seedstack:\n" +
                "      some:\n" +
                "        demo:\n" +
                "          someAttribute: someValue\n" +
                "          otherAttribute: otherValue\n", yamlClassConfig.execute(args));
    }
}
