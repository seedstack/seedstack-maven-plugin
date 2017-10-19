/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.fusesource.jansi.AnsiConsole;

abstract class AbstractSeedStackMojo extends AbstractMojo {
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                AnsiConsole.systemUninstall();
            }
        }, "uninstall-ansi"));
        AnsiConsole.systemInstall();
    }
}
