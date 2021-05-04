/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * SeedStack utils.
 */
public final class SeedStackUtils {
    private static final String seedClassName = "org.seedstack.seed.core.Seed";
    static final String mainClassName = "org.seedstack.seed.core.SeedMain";

    private SeedStackUtils() {
        // no instantiation allowed
    }

    public static Object getSeedLauncher() throws Exception {
        try {
            Method getLauncherMethod;
            try {
                getLauncherMethod = Thread.currentThread().getContextClassLoader()
                        .loadClass(SeedStackUtils.seedClassName).getMethod("getLauncher");
            } catch (NoSuchMethodException e) {
                getLauncherMethod = Thread.currentThread().getContextClassLoader()
                        .loadClass(SeedStackUtils.mainClassName).getMethod("getLauncher");
            }
            return getLauncherMethod.invoke(null);
        } catch (Exception e) {
            throw unwrapException(e);
        }
    }

    public static Object getToolLauncher(String tool) throws Exception {
        try {
            Method getLauncherMethod;
            try {
                getLauncherMethod = Thread.currentThread().getContextClassLoader()
                        .loadClass(SeedStackUtils.seedClassName).getMethod("getToolLauncher", String.class);
            } catch (NoSuchMethodException e) {
                getLauncherMethod = Thread.currentThread().getContextClassLoader()
                        .loadClass(SeedStackUtils.mainClassName).getMethod("getToolLauncher", String.class);
            }
            return getLauncherMethod.invoke(null, tool);
        } catch (Exception e) {
            throw unwrapException(e);
        }
    }

    public static void launch(Object seedLauncher, String[] args) throws Exception {
        try {
            Method launchMethod = seedLauncher.getClass().getMethod("launch", String[].class);
            launchMethod.invoke(seedLauncher, new Object[]{args});
        } catch (Exception e) {
            throw unwrapException(e);
        }
    }

    public static void shutdown(Object seedLauncher) throws Exception {
        try {
            Method shutdownMethod = seedLauncher.getClass().getMethod("shutdown");
            shutdownMethod.invoke(seedLauncher);
        } catch (Exception e) {
            throw unwrapException(e);
        }
    }

    public static void refresh(Object seedLauncher) throws Exception {
        Method refreshMethod;
        try {
            refreshMethod = seedLauncher.getClass().getMethod("refresh");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Application refresh is not supported before Seed 3.4.0", e);
        }
        try {
            refreshMethod.invoke(seedLauncher);
        } catch (Exception e) {
            throw unwrapException(e);
        }
    }

    private static Exception unwrapException(Exception e) {
        Exception unwrapped;
        if (e instanceof InvocationTargetException
                && ((InvocationTargetException) e).getTargetException() instanceof Exception) {
            unwrapped = (Exception) ((InvocationTargetException) e).getTargetException();
        } else {
            unwrapped = e;
        }
        return unwrapped;
    }
}
