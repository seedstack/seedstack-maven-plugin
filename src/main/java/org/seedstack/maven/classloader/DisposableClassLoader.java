/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.classloader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import sun.misc.Resource;

class DisposableClassLoader extends SecureClassLoader {
    private final Resource res;
    private final HotSwapURLClassLoader hotSwapURLClassLoader;
    private final String name;

    DisposableClassLoader(HotSwapURLClassLoader hotSwapURLClassLoader, String name, Resource res) {
        this.hotSwapURLClassLoader = hotSwapURLClassLoader;
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
            return this.hotSwapURLClassLoader.loadClass(name, resolve);
        }
    }
}
