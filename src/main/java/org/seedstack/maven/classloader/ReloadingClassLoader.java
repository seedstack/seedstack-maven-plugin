/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.classloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import sun.misc.Resource;
import sun.misc.URLClassPath;

public class ReloadingClassLoader extends URLClassLoader {
    private final AccessControlContext acc = AccessController.getContext();
    private final URLClassPath ucp;
    private final ObjectMapper objectMapper;
    private final Set<String> packagesToScan;
    private final Map<String, DisposableClassLoader> classLoaders = new HashMap<>();
    private final Log log;

    public ReloadingClassLoader(Log log, URL[] urls) {
        super(urls);
        this.log = log;
        this.ucp = new URLClassPath(urls);
        this.objectMapper = createObjectMapper();
        this.packagesToScan = Collections.unmodifiableSet(resolvePackagesToScan());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isClassScanned(name)) {
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

    public void invalidateClasses(Set<String> classNamesToInvalidate) {
        synchronized (classLoaders) {
            for (String classNameToInvalidate : classNamesToInvalidate) {
                if (classLoaders.remove(classNameToInvalidate.replace('/', '.')) != null) {
                    log.debug("Class " + classNameToInvalidate + " will be reloaded on next access");
                }
            }
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
                                return new DisposableClassLoader(name, res);
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

    private boolean isClassScanned(String name) {
        for (String packageToScan : packagesToScan) {
            if (name.startsWith(packageToScan)) {
                return true;
            }
        }
        return false;
    }

    private ObjectMapper createObjectMapper() {
        YAMLFactory yamlFactory = new YAMLFactory();
        return new ObjectMapper(yamlFactory);
    }

    private Set<String> resolvePackagesToScan() {
        Set<String> packagesToScan = new HashSet<>();
        try {
            Enumeration<URL> resources = super.findResources("application.yaml");
            while (resources.hasMoreElements()) {
                packagesToScan.addAll(resolvePackagesToScan(resources.nextElement()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to find application base configuration files", e);
        }
        return packagesToScan;
    }

    private Set<String> resolvePackagesToScan(URL url) {
        try {
            return objectMapper.readValue(url, ApplicationConfig.class).getBasePackages();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to retrieve content of application configuration file: " + url.toExternalForm(), e);
        }
    }

    private class DisposableClassLoader extends SecureClassLoader {
        private final Resource res;
        private final String name;

        DisposableClassLoader(String name, Resource res) {
            this.name = name;
            this.res = res;
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (this.name.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    // First, check if the class has already been loaded
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            ByteBuffer byteBuffer = res.getByteBuffer();
                            if (byteBuffer != null) {
                                // Use (direct) ByteBuffer:
                                CodeSigner[] signers = res.getCodeSigners();
                                CodeSource cs = new CodeSource(res.getURL(), signers);
                                c = defineClass(name, byteBuffer, cs);
                            } else {
                                byte[] b = res.getBytes();
                                // must read certificates AFTER reading bytes.
                                CodeSigner[] signers = res.getCodeSigners();
                                CodeSource cs = new CodeSource(res.getURL(), signers);
                                c = defineClass(name, b, 0, b.length, cs);
                            }
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                        if (resolve) {
                            resolveClass(c);
                        }
                    }
                    return c;
                }
            } else {
                return ReloadingClassLoader.this.loadClass(name, resolve);
            }
        }
    }
}
