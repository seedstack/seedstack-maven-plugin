/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides a common base for mojos that run SeedStack applications.
 */
public class AbstractExecutableMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true)
    private File testClassesDirectory;
    @Parameter(property = "args")
    private String args;

    private final IsolatedThreadGroup isolatedThreadGroup = new IsolatedThreadGroup("seed-app");
    private final Object monitor = new Object();
    private boolean testMode = false;
    protected Runnable runnable;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Create an isolated thread
        Thread bootstrapThread = new Thread(isolatedThreadGroup, runnable, "main");

        // Start the launcher thread
        bootstrapThread.setContextClassLoader(new URLClassLoader(getClassPathUrls()));
        bootstrapThread.start();

        // Wait for the application to launch
        synchronized (monitor) {
            try {
                monitor.wait();
            } catch (InterruptedException e) {
                throw new MojoExecutionException("Interrupted while waiting for Seed");
            }
        }

        // Check for any uncaught exception
        synchronized (isolatedThreadGroup) {
            if (isolatedThreadGroup.uncaughtException != null) {
                throw new MojoExecutionException("An exception occurred while executing Seed", isolatedThreadGroup.uncaughtException);
            }
        }

        // Join the application non-daemon threads
        joinNonDaemonThreads(isolatedThreadGroup);
    }

    protected void enableTestMode() {
        this.testMode = true;
    }

    protected Object getMonitor() {
        return monitor;
    }

    public String[] getArgs() throws MojoExecutionException {
        try {
            return CommandLineUtils.translateCommandline(args);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to parse arguments", e);
        }
    }

    private void joinNonDaemonThreads(ThreadGroup threadGroup) {
        boolean found = true;

        while (found) {
            found = false;

            for (Thread groupThread : getGroupThreads(threadGroup)) {
                if (!groupThread.isDaemon()) {
                    found = true;

                    try {
                        groupThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private Thread[] getGroupThreads(final ThreadGroup group) {
        int nAlloc = group.activeCount();
        int n;
        Thread[] threads;

        do {
            nAlloc *= 2;
            threads = new Thread[nAlloc];
            n = group.enumerate(threads);
        } while (n == nAlloc);

        return java.util.Arrays.copyOf(threads, n);
    }

    private URL[] getClassPathUrls() throws MojoExecutionException {
        List<URL> urls = new ArrayList<URL>();

        try {
            if (testMode) {
                // Project test resources
                addResources(this.testClassesDirectory, this.project.getTestResources(), urls);

                // Project test classes
                urls.add(this.testClassesDirectory.toURI().toURL());
            }

            // Project resources
            addResources(this.classesDirectory, this.project.getResources(), urls);

            // Project classes
            urls.add(this.classesDirectory.toURI().toURL());

            // Project dependencies (scope is dependent upon the @Mojo annotation and the already executed phase)
            addArtifacts(this.project.getArtifacts(), urls);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Unable to build classpath", e);
        }

        return urls.toArray(new URL[urls.size()]);
    }

    private void addArtifacts(Collection<Artifact> artifacts, List<URL> urls) throws MalformedURLException {
        for (Artifact artifact : artifacts) {
            File file = artifact.getFile();
            if (file.getName().endsWith(".jar")) {
                urls.add(file.toURI().toURL());
            }
        }
    }

    private void addResources(File classesDirectory, List<Resource> resources, List<URL> urls) throws MalformedURLException {
        for (Resource resource : resources) {
            File directory = new File(resource.getDirectory());
            urls.add(directory.toURI().toURL());
            removeDuplicatesFromOutputDirectory(classesDirectory, directory);
        }
    }

    private void removeDuplicatesFromOutputDirectory(File outputDirectory, File originDirectory) {
        if (originDirectory.isDirectory()) {
            String[] list = originDirectory.list();
            if (list != null) {
                for (String name : list) {
                    File targetFile = new File(outputDirectory, name);
                    if (targetFile.exists() && targetFile.canWrite()) {
                        if (!targetFile.isDirectory()) {
                            targetFile.delete();
                        } else {
                            removeDuplicatesFromOutputDirectory(targetFile,
                                    new File(originDirectory, name));
                        }
                    }
                }
            }
        }
    }

    private class IsolatedThreadGroup extends ThreadGroup {
        private Throwable uncaughtException;

        IsolatedThreadGroup(String name) {
            super(name);
        }

        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof ThreadDeath) {
                return;
            }

            synchronized (this) {
                if (uncaughtException == null) {
                    uncaughtException = throwable;
                }
            }

            getLog().warn(throwable);
        }
    }
}
