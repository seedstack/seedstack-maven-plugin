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

public class DefaultLauncherRunnable implements Runnable {
    private final String[] args;
    private final Object monitor;
    private final Log log;

    public DefaultLauncherRunnable(String[] args, Object monitor, Log log) {
        this.args = args;
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

            log.info("Launching Seed application " + " with arguments " + Arrays.toString(args));
            launch(seedLauncher, args);
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
        try {
            Method getLauncherMethod = Thread.currentThread().getContextClassLoader().loadClass(SeedStackConstants.mainClassName).getMethod("getLauncher");
            return getLauncherMethod.invoke(null);
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot launch SeedStack application", e);
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
