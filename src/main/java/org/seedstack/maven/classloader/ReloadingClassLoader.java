/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.classloader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import sun.misc.Resource;
import sun.misc.URLClassPath;

public class ReloadingClassLoader extends URLClassLoader {
    private final AccessControlContext acc = AccessController.getContext();
    private final URLClassPath ucp;
    private final Map<String, DisposableClassLoader> classLoaders = new HashMap<>();
    private final Log log;
    private final List<String> sourceRoots;

    public ReloadingClassLoader(Log log, URL[] urls, List<String> sourceRoots) {
        super(urls);
        this.log = log;
        this.ucp = new URLClassPath(urls);
        this.sourceRoots = sourceRoots;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isClassInSourceRoots(name)) {
            DisposableClassLoader disposableClassLoader;
            synchronized (classLoaders) {
                disposableClassLoader = classLoaders.get(name);
                if (disposableClassLoader == null) {
                    classLoaders.put(name, disposableClassLoader = createDisposableClassLoader(name));
                }
            }
            return disposableClassLoader.loadClass(name, resolve);
        } else {
            return super.loadClass(name, resolve);
        }
    }

    private boolean isClassInSourceRoots(String name) {
        String sourceFile = name.replace('.', '/') + ".java";
        for (String sourceRoot : sourceRoots) {
            if (new File(sourceRoot, sourceFile).exists()) {
                return true;
            }
        }
        return false;
    }

    public void invalidateClasses(Set<String> classNamesToInvalidate) {
        synchronized (classLoaders) {
            for (String classNameToInvalidate : classNamesToInvalidate) {
                if (classLoaders.remove(classNameToInvalidate.replace('/', '.')) != null) {
                    log.debug("Class " + classNameToInvalidate + " will be reloaded on next access");
                }
            }
        }
    }

    public void invalidateClassesFromPackage(String aPackage) {
        synchronized (classLoaders) {
            Iterator<String> iterator = classLoaders.keySet().iterator();
            while (iterator.hasNext()) {
                String className = iterator.next();
                if (className.startsWith(aPackage)) {
                    iterator.remove();
                    log.debug("Class " + className + " will be reloaded on next access");
                }
            }
        }
    }

    public void invalidateAllClasses() {
        synchronized (classLoaders) {
            classLoaders.clear();
            log.debug("All classes will be reloaded on next access");
        }
    }

    private DisposableClassLoader createDisposableClassLoader(final String name) throws ClassNotFoundException {
        final DisposableClassLoader result;
        try {
            result = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<DisposableClassLoader>() {
                        public DisposableClassLoader run() throws ClassNotFoundException {
                            String path = name.replace('.', '/').concat(".class");
                            Resource res = ucp.getResource(path, false);
                            if (res != null) {
                                return new DisposableClassLoader(ReloadingClassLoader.this, name, res);
                            } else {
                                return null;
                            }
                        }
                    }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            throw (ClassNotFoundException) pae.getException();
        }
        if (result == null) {
            throw new ClassNotFoundException(name);
        }
        return result;
    }
}
