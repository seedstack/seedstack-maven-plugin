/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.runnables;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.seedstack.maven.SeedStackConstants;

import java.lang.reflect.Method;
import java.util.Arrays;

public class ToolLauncherRunnable implements Runnable {
    private final String tool;
    private final String[] args;
    private final Object monitor;
    private final Log log;

    public ToolLauncherRunnable(String tool, String[] args, Object monitor, Log log) {
        this.tool = tool;
        this.args = args;
        this.monitor = monitor;
        this.log = log;
    }

    public void run() {
        try {
            final Object toolLauncher = getToolLauncher();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        shutdown(toolLauncher);
                    } catch (Exception e) {
                        // ignore exception in case of tools
                    }
                }
            });
            log.info("Launching Seed tool " + tool + " with arguments " + Arrays.toString(args));
            launch(toolLauncher, args);
        } catch (Exception e) {
            Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
        } finally {
            synchronized (monitor) {
                monitor.notify();
            }
        }
    }

    private Object getToolLauncher() throws MojoExecutionException {
        try {
            Method getLauncherMethod = Thread.currentThread().getContextClassLoader().loadClass(SeedStackConstants.mainClassName).getMethod("getToolLauncher", String.class);
            return getLauncherMethod.invoke(null, tool);
        } catch (Exception e) {
            throw new MojoExecutionException("Seed application doesn't support tools (you need at least Seed 3.0.0)");
        }
    }

    private void launch(Object seedLauncher, String[] args) throws Exception {
        Method launchMethod = seedLauncher.getClass().getMethod("launch", String[].class);
        launchMethod.invoke(seedLauncher, new Object[]{args});
    }

    private void shutdown(Object seedLauncher) throws Exception {
        Method launchMethod = seedLauncher.getClass().getMethod("shutdown");
        launchMethod.invoke(seedLauncher);
    }
}
