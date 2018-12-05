/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.classloader;

import java.net.URL;
import java.net.URLClassLoader;

class DisposableClassLoader extends URLClassLoader {
    private final ReloadingClassLoader reloadingClassLoader;
    private final String name;

    DisposableClassLoader(ReloadingClassLoader reloadingClassLoader, String name, URL[] urLs) {
        super(urLs, null);
        this.reloadingClassLoader = reloadingClassLoader;
        this.name = name;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (this.name.equals(name)) {
            // Only load the class specific to this classloader
            synchronized (getClassLoadingLock(name)) {
                // First, check if the class has already been loaded
                Class<?> c = findLoadedClass(name);

                if (c == null) {
                    // If not, directly load it (no delegation to parent)
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                }

                return c;
            }
        } else {
            // Otherwise delegate back to reloading classloader
            return reloadingClassLoader.loadClass(name, resolve);
        }
    }
}
