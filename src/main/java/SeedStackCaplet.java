/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class SeedStackCaplet extends Capsule {
    private static final Map.Entry<String, List<String>> RAW_ATTR_APP_CLASS_PATH = ATTRIBUTE("App-Class-Path",
            T_LIST(T_STRING()),
            null,
            true,
            "A list of entries that are added to the classpath on runtime");
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
        if (attr.getKey().equals(RAW_ATTR_APP_CLASS_PATH.getKey())) {
            final List<Object> resolvedClasspath = new ArrayList<>();

            // Runtime classpath
            String runtimeClasspath = System.getProperty(CLASSPATH);
            if (runtimeClasspath != null && !runtimeClasspath.isEmpty()) {
                for (String rawPath : runtimeClasspath.split(File.pathSeparator)) {
                    resolvedClasspath.add(resolvePath(rawPath));
                }
            }

            // Static POM classpath
            for (String rawPath : super.attribute(RAW_ATTR_APP_CLASS_PATH)) {
                resolvedClasspath.add(resolvePath(rawPath));
            }

            // App classpath
            resolvedClasspath.add(lookup("*.jar", ATTR_APP_CLASS_PATH));

            return (T) new ArrayList<>(resolvedClasspath);
        } else {
            return super.attribute(attr);
        }
    }

    private List<Path> resolvePath(String path) {
        HashSet<Path> result = new LinkedHashSet<>();

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

        String filePath = file.getPath();
        if (filePath.endsWith("/*") || filePath.endsWith("\\*")) {
            // Cannot use a FilenameFilter here because the capsule won't load the anonymous (or inner) class
            File[] files = file.getParentFile().listFiles();
            if (files != null) {
                for (File candidate : files) {
                    String candidatePath = candidate.getPath();
                    if (candidatePath.endsWith(".jar") || candidatePath.endsWith(".JAR")) {
                        result.add(normalizePath(candidate));
                    }
                }
            }
        } else {
            result.add(normalizePath(file));
        }

        return new ArrayList<>(result);
    }

    private Path normalizePath(File file) {
        return file.getAbsoluteFile().toPath();
    }
}