/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.runnables;

import java.util.Arrays;
import org.seedstack.maven.Context;
import org.seedstack.maven.SeedStackUtils;

public class ToolRunnable implements Runnable {
    private final String tool;
    private final Context context;

    public ToolRunnable(String tool, Context context) {
        this.tool = tool;
        this.context = context;
    }

    public void run() {
        try {
            final Object toolLauncher = SeedStackUtils.getToolLauncher(tool);
            Runtime.getRuntime().addShutdownHook(new Thread("shutdown") {
                @Override
                public void run() {
                    try {
                        SeedStackUtils.shutdown(toolLauncher);
                    } catch (Exception e) {
                        // ignore exception in case of tools
                    }
                }
            });
            String[] args = context.getArgs();
            context.getLog().info("Launching Seed tool " + tool + " with arguments " + Arrays.toString(args));
            SeedStackUtils.launch(toolLauncher, args);
        } catch (Exception e) {
            Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
        } finally {
            context.notifyStartup();
        }
    }
}
