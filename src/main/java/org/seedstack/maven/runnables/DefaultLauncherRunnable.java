/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.runnables;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import org.apache.maven.plugin.logging.Log;
import org.seedstack.maven.SeedStackUtils;

public class DefaultLauncherRunnable implements Runnable {
    private final String[] args;
    private final Object launchMonitor;
    private final Object refreshMonitor;
    private final Log log;
    private ClassLoader classLoader;
    private ThreadGroup threadGroup;
    private Object seedLauncher;

    public DefaultLauncherRunnable(String[] args, Object monitor, Log log) {
        this.args = args == null ? null : args.clone();
        this.launchMonitor = monitor;
        this.refreshMonitor = new Object();
        this.log = log;
    }

    public void run() {
        try {
            classLoader = Thread.currentThread().getContextClassLoader();
            threadGroup = Thread.currentThread().getThreadGroup();
            seedLauncher = SeedStackUtils.getSeedLauncher();

            Runtime.getRuntime().addShutdownHook(new Thread("shutdown") {
                @Override
                public void run() {
                    try {
                        System.out.println();
                        SeedStackUtils.shutdown(seedLauncher);
                    } catch (Exception e) {
                        log.error("SeedStack application failed to shutdown properly", e);
                    }
                }
            });

            log.info("Launching SeedStack application with arguments " + Arrays.toString(args));
            SeedStackUtils.launch(seedLauncher, args);
        } catch (Exception e) {
            threadGroup.uncaughtException(Thread.currentThread(), e);
        } finally {
            synchronized (launchMonitor) {
                launchMonitor.notify();
            }
        }
    }

    @SuppressFBWarnings(value = {"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"}, justification = "Cannot know when the "
            + "application is refreshed")
    public void refresh() {
        if (seedLauncher != null) {
            new Thread(threadGroup, new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    try {
                        SeedStackUtils.refresh(seedLauncher);
                    } catch (Exception e) {
                        log.error("Unable to refresh SeedStack application", e);
                    }

                    // Notify end of refresh
                    synchronized (refreshMonitor) {
                        refreshMonitor.notify();
                    }
                }
            }, "refresh").start();
        }

        // Wait for refresh complete
        synchronized (refreshMonitor) {
            try {
                refreshMonitor.wait();
            } catch (InterruptedException e) {
                log.warn("Failed to wait until refresh is complete");
            }
        }
    }
}
