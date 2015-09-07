/**
 * Copyright (c) 2013-2015 by The SeedStack authors. All rights reserved.
 *
 * This file is part of SeedStack, An enterprise-oriented full development stack.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.inetpsa.seed.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the run goal. This goal runs a SeedStack project.
 */
@Mojo(name = "run", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class RunMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(property = "args")
    private String args;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        IsolatedThreadGroup isolatedThreadGroup = new IsolatedThreadGroup(SeedStackConstants.mainClassName);

        Thread bootstrapThread = new Thread(isolatedThreadGroup, new Runnable() {
            public void run() {
                try {
                    Method main = Thread.currentThread().getContextClassLoader().loadClass(SeedStackConstants.mainClassName).getMethod("main", String[].class);
                    main.setAccessible(true);

                    if (!Modifier.isStatic(main.getModifiers())) {
                        throw new MojoExecutionException("Main method of class " + SeedStackConstants.mainClassName + " is not static");
                    }

                    main.invoke(null, new Object[]{CommandLineUtils.translateCommandline(args)});
                } catch (Exception e) {
                    Thread.currentThread().getThreadGroup().uncaughtException(
                            Thread.currentThread(),
                            new Exception("Unable to invoke main method of class " + SeedStackConstants.mainClassName, e)
                    );
                }
            }
        }, SeedStackConstants.mainClassName + ".main()");

        bootstrapThread.setContextClassLoader(new URLClassLoader(getClassPathUrls()));

        bootstrapThread.start();

        joinThreads(isolatedThreadGroup);

        synchronized (isolatedThreadGroup) {
            if (isolatedThreadGroup.uncaughtException != null) {
                throw new MojoExecutionException("An exception occurred while executing the Seed application", isolatedThreadGroup.uncaughtException);
            }
        }
    }

    private void joinThreads(ThreadGroup threadGroup) {
        boolean found;

        do {
            found = false;

            for (Thread thread : getGroupThreads(threadGroup)) {
                if (!thread.isDaemon()) {
                    found = true;

                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (thread.isAlive()) {
                        getLog().warn(String.format("Unable to stop thread %s", thread));
                    }
                }
            }
        } while (found);
    }

    Thread[] getGroupThreads(final ThreadGroup group) {
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
            // Project resources
            for (Resource resource : this.project.getResources()) {
                File directory = new File(resource.getDirectory());
                urls.add(directory.toURI().toURL());
                removeDuplicatesFromOutputDirectory(this.classesDirectory, directory);
            }

            // Project classes
            urls.add(this.classesDirectory.toURI().toURL());

            // Project dependencies
            for (Artifact artifact : this.project.getArtifacts()) {
                File file = artifact.getFile();
                if (file.getName().endsWith(".jar")) {
                    urls.add(file.toURI().toURL());
                }
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Unable to build classpath", e);
        }

        return urls.toArray(new URL[urls.size()]);
    }

    public void removeDuplicatesFromOutputDirectory(File outputDirectory, File originDirectory) {
        if (originDirectory.isDirectory()) {
            for (String name : originDirectory.list()) {
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

    class IsolatedThreadGroup extends ThreadGroup {
        private Throwable uncaughtException;

        public IsolatedThreadGroup(String name) {
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
