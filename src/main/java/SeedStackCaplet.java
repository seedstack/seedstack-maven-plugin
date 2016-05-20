/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class SeedStackCaplet extends Capsule {
    private static final String CLASSPATH = "capsule.classpath";
    private final String homePath = getHomePath();
    private final String startupPath = getStartupPath();

    public SeedStackCaplet(Capsule prev) {
        super(prev);
    }

    public SeedStackCaplet(Path jarFile) {
        super(jarFile);
    }

    private String getHomePath() {
        try {
            return new File(System.getProperty("user.home")).getAbsoluteFile().getCanonicalPath();
        } catch (Exception e) {
            log(LOG_QUIET, "Unable to resolve home path: " + e.getMessage());
        }
        return null;
    }

    private String getStartupPath() {
        try {
            CodeSource codeSource = SeedStackCaplet.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                File file = new File(URLDecoder.decode(codeSource.getLocation().getPath(), "UTF-8"));
                if (file.isFile()) {
                    file = file.getParentFile();
                }
                return file.getAbsoluteFile().getCanonicalPath();
            }
        } catch (Exception e) {
            log(LOG_QUIET, "Unable to resolve startup path: " + e.getMessage());
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Map.Entry<String, T> attr) {
        if (attr == ATTR_APP_CLASS_PATH) {
            final List<Object> appClasspath = new ArrayList<>(super.attribute(ATTR_APP_CLASS_PATH));

            String runtimeClasspath = System.getProperty(CLASSPATH);
            if (runtimeClasspath != null && !runtimeClasspath.isEmpty()) {
                Collections.addAll(appClasspath, runtimeClasspath.split(File.pathSeparator));
            }

            final List<Object> resolvedClasspath = new ArrayList<>();
            for (Object o : appClasspath) {
                if (o instanceof Path) {
                    resolvedClasspath.add(resolvePath(o.toString()));
                } else if (o instanceof String) {
                    resolvedClasspath.add(resolvePath((String) o));
                } else {
                    resolvedClasspath.add(o);
                }
            }

            // deduplicate list of normalized paths
            return (T) new ArrayList<>(new HashSet<>(resolvedClasspath));
        } else {
            return super.attribute(attr);
        }
    }

    private String resolvePath(String path) {
        if (path.startsWith("~")) {
            if (homePath == null) {
                throw new RuntimeException("Unable to resolve path containing home reference: " + path);
            }
            path = path.replaceFirst("^~", Matcher.quoteReplacement(homePath));
        }

        File file = new File(path);
        if (!file.isAbsolute()) {
            if (startupPath == null) {
                throw new RuntimeException("Unable to resolve relative path: " + path);
            }
            file = new File(startupPath, path);
        }

        try {
            return file.getAbsoluteFile().getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("Unable to resolve path: " + path, e);
        }
    }
}