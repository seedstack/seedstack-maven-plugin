/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.seedstack.maven.classloader.ReloadingClassLoader;
import org.seedstack.maven.livereload.LRServer;
import org.seedstack.maven.runnables.AppRunnable;
import org.seedstack.maven.watcher.DirectoryWatcher;
import org.seedstack.maven.watcher.ResourceChangeListener;
import org.seedstack.maven.watcher.SourceChangeListener;

/**
 * Defines the run goal. This goal runs a SeedStack project.
 */
@Mojo(name = "watch", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class WatchMojo extends AbstractExecutableMojo {
    private static final int LIVE_RELOAD_PORT = 35729;
    private DirectoryWatcher sourceWatcher;
    private DirectoryWatcher resourceWatcher;
    private Thread sourceWatcherThread;
    private Thread resourceWatcherThread;
    private List<String> compileSourceRoots;
    private ReloadingClassLoader reloadingClassLoader;
    private AppRunnable appRunnable;
    private LRServer lrServer;

    @Override
    public void execute() throws MojoExecutionException {
        MavenProject mavenProject = getContext().getMavenProject();
        this.compileSourceRoots = Collections.unmodifiableList(mavenProject.getCompileSourceRoots());

        setupSourceWatcher();

        setupResourceWatcher(mavenProject);

        // Force config watching
        System.setProperty("seedstack.config.config.watch", "true");

        this.appRunnable = new AppRunnable(getContext());
        execute(appRunnable, false);

        // Start LiveReload server
        startLiveReload();

        // Start watching sources and resources
        sourceWatcherThread.start();
        resourceWatcherThread.start();

        // Trigger initial LiveReload
        liveReload();

        // Wait for the app to end
        waitForShutdown();

        // Stop the LiveReload server
        stopLiveReload();

        // Stop the watchers
        stopWatcher(sourceWatcher, sourceWatcherThread);
        stopWatcher(resourceWatcher, resourceWatcherThread);
    }

    private void startLiveReload() {
        try {
            getLog().info("Starting LiveReload server on port " + LIVE_RELOAD_PORT);
            lrServer = new LRServer(LIVE_RELOAD_PORT);
            lrServer.start();
        } catch (Exception e) {
            getLog().error("Unable to start LiveReload server", e);
        }
    }

    private void stopLiveReload() {
        if (lrServer != null) {
            try {
                getLog().info("Stopping LiveReload server");
                lrServer.stop();
            } catch (Exception e) {
                getLog().warn("Unable to stop LiveReload server", e);
            }
        }
    }

    private void stopWatcher(DirectoryWatcher watcher, Thread watcherThread) {
        watcher.stop();
        watcherThread.interrupt();
        try {
            watcherThread.join(1000);
        } catch (InterruptedException e) {
            getLog().warn("Unable to stop a watcher", e);
        }
    }

    private void setupResourceWatcher(MavenProject mavenProject) throws MojoExecutionException {
        try {
            this.resourceWatcher = new DirectoryWatcher(getLog(), new ResourceChangeListener(this, getContext()));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create resource watcher", e);
        }
        for (Resource resource : mavenProject.getResources()) {
            File file = new File(resource.getDirectory());
            if (file.isDirectory()) {
                getLog().info("Will watch resource directory " + file.getPath());
                try {
                    this.resourceWatcher.watchRecursively(file.toPath());
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to watch resource directory "
                            + file.getAbsolutePath(), e);
                }
            }
        }
        this.resourceWatcherThread = new Thread(this.resourceWatcher, "resource-watcher");
    }

    private void setupSourceWatcher() throws MojoExecutionException {
        try {
            this.sourceWatcher = new DirectoryWatcher(getLog(), new SourceChangeListener(this, getContext()));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create source watcher", e);
        }

        for (String sourceRoot : compileSourceRoots) {
            File file = new File(sourceRoot);
            if (file.isDirectory()) {
                getLog().info("Will watch source directory " + file.getPath());
                try {
                    this.sourceWatcher.watchRecursively(file.toPath());
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to watch source directory " + file.getAbsolutePath(), e);
                }
            }
        }
        this.sourceWatcherThread = new Thread(this.sourceWatcher, "source-watcher");
    }

    @Override
    URLClassLoader createClassLoader(final URL[] classPathUrls) {
        if (reloadingClassLoader == null) {
            reloadingClassLoader = AccessController.doPrivileged(new PrivilegedAction<ReloadingClassLoader>() {
                public ReloadingClassLoader run() {
                    return new ReloadingClassLoader(getLog(), classPathUrls, compileSourceRoots);
                }
            });
        }
        return reloadingClassLoader;
    }

    public void liveReload() {
        if (lrServer != null) {
            getLog().info("Triggering LiveReload");
            try {
                lrServer.notifyChange("/");
            } catch (Exception e) {
                getLog().warn("Error triggering LiveReload", e);
            }
        }
    }

    public void invalidateClasses(Set<String> classNamesToInvalidate) {
        reloadingClassLoader.invalidateClasses(classNamesToInvalidate);
    }

    public void invalidateClassesFromPackage(String aPackage) {
        reloadingClassLoader.invalidateClassesFromPackage(aPackage);
    }

    public void invalidateAllClasses() {
        reloadingClassLoader.invalidateAllClasses();
    }

    public void refresh() throws Exception {
        appRunnable.refresh();
    }
}
