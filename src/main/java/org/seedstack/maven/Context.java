/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import java.io.File;
import java.util.Locale;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class Context {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    private static final boolean IS_CYGWIN = IS_WINDOWS
            && System.getenv("PWD") != null
            && System.getenv("PWD").startsWith("/")
            && !"cygwin".equals(System.getenv("TERM"));
    private static final boolean IS_MINGW_XTERM = IS_WINDOWS
            && System.getenv("MSYSTEM") != null
            && System.getenv("MSYSTEM").startsWith("MINGW")
            && "xterm".equals(System.getenv("TERM"));
    private final String[] args;
    private final Log log;
    private final File classesDirectory;
    private final File testClassesDirectory;
    private final MavenProject mavenProject;
    private final MavenSession mavenSession;
    private final BuildPluginManager buildPluginManager;

    public Context(String[] args, Log log, File classesDirectory,
            File testClassesDirectory, MavenProject mavenProject, MavenSession mavenSession,
            BuildPluginManager buildPluginManager) {
        this.args = args == null ? null : args.clone();
        this.log = log;
        this.classesDirectory = classesDirectory;
        this.testClassesDirectory = testClassesDirectory;
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.buildPluginManager = buildPluginManager;
    }

    public String[] getArgs() {
        return args == null ? null : args.clone();
    }

    public Log getLog() {
        return log;
    }

    public File getClassesDirectory() {
        return classesDirectory;
    }

    public File getTestClassesDirectory() {
        return testClassesDirectory;
    }

    public MavenProject getMavenProject() {
        return mavenProject;
    }

    public MavenSession getMavenSession() {
        return mavenSession;
    }

    public BuildPluginManager getBuildPluginManager() {
        return buildPluginManager;
    }

    public void notifyStartup() {
        synchronized (this) {
            notifyAll();
        }
    }

    public void waitForStartup() throws MojoExecutionException {
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new MojoExecutionException("Interrupted while waiting for SeedStack application");
            }
        }
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isCygwin() {
        return IS_CYGWIN;
    }

    public static boolean isMingwXterm() {
        return IS_MINGW_XTERM;
    }
}
