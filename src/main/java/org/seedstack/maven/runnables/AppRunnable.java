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
import org.seedstack.maven.Context;
import org.seedstack.maven.SeedStackUtils;

public class AppRunnable implements Runnable {
    private final Object refreshMonitor = new Object();
    private final Context context;
    private ClassLoader classLoader;
    private ThreadGroup threadGroup;
    private Object seedLauncher;

    public AppRunnable(Context context) {
        this.context = context;
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
                        SeedStackUtils.shutdown(seedLauncher);
                    } catch (Exception e) {
                        context.getLog().error("SeedStack application failed to shutdown properly", e);
                    }
                }
            });

            String[] args = context.getArgs();
            context.getLog().info("Launching SeedStack application with arguments " + Arrays.toString(args));
            SeedStackUtils.launch(seedLauncher, args);
        } catch (Exception e) {
            threadGroup.uncaughtException(Thread.currentThread(), e);
        } finally {
            context.notifyStartup();
        }
    }

    @SuppressFBWarnings(value = {"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"}, justification = "Cannot know when the "
            + "application is refreshed")
    public void refresh() throws Exception {
        final Exception[] refreshException = new Exception[]{null};
        if (seedLauncher != null) {
            new Thread(threadGroup, new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setContextClassLoader(classLoader);

                    try {
                        SeedStackUtils.refresh(seedLauncher);
                    } catch (Exception e) {
                        refreshException[0] = e;
                    }

                    // Notify end of refresh
                    context.notifyStartup();
                }
            }, "refresh").start();
        }

        // Wait for refresh complete
        context.waitForStartup();

        if (refreshException[0] != null) {
            throw refreshException[0];
        }
    }
}
