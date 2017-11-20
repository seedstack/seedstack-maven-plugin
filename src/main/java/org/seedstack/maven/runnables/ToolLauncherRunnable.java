/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.runnables;

import java.util.Arrays;
import org.apache.maven.plugin.logging.Log;
import org.seedstack.maven.SeedStackUtils;

public class ToolLauncherRunnable implements Runnable {
    private final String tool;
    private final String[] args;
    private final Object monitor;
    private final Log log;

    public ToolLauncherRunnable(String tool, String[] args, Object monitor, Log log) {
        this.tool = tool;
        this.args = args == null ? null : args.clone();
        this.monitor = monitor;
        this.log = log;
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
            log.info("Launching Seed tool " + tool + " with arguments " + Arrays.toString(args));
            SeedStackUtils.launch(toolLauncher, args);
        } catch (Exception e) {
            Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
        } finally {
            synchronized (monitor) {
                monitor.notify();
            }
        }
    }
}
