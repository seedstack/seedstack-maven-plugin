/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.lang.reflect.Method;

public class LauncherRunnable implements Runnable {
    private final String commandLine;
    private final Object monitor;
    private final Log log;

    public LauncherRunnable(String commandLine, Object monitor, Log log) {
        this.commandLine = commandLine;
        this.monitor = monitor;
        this.log = log;
    }

    public void run() {
        try {
            final Object seedLauncher = getSeedLauncher();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        log.info("Stopping Seed application");

                        shutdown(seedLauncher);

                        log.info("Seed application stopped");
                    } catch (Exception e) {
                        log.error("Seed application failed to shutdown properly", e);
                    }
                }
            });

            log.info("Starting Seed application");

            launch(seedLauncher, CommandLineUtils.translateCommandline(commandLine));

            log.info("Seed application started");
        } catch (Exception e) {
            Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
        } finally {
            synchronized (monitor) {
                monitor.notify();
            }
        }
    }

    private Object getSeedLauncher() throws Exception {
        Method getLauncherMethod = Thread.currentThread().getContextClassLoader().loadClass(SeedStackConstants.mainClassName).getMethod("getLauncher");
        return getLauncherMethod.invoke(null);
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
