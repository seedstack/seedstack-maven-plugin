/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.seedstack.maven.classloader.ReloadingClassLoader;
import org.seedstack.maven.livereload.LRServer;
import org.seedstack.maven.runnables.DefaultLauncherRunnable;
import org.seedstack.maven.watcher.DirectoryWatcher;
import org.seedstack.maven.watcher.FileChangeListener;
import org.seedstack.maven.watcher.FileEvent;

/**
 * Defines the run goal. This goal runs a SeedStack project.
 */
@Mojo(name = "watch", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class WatchMojo extends AbstractExecutableMojo {
    private static final int LIVE_RELOAD_PORT = 35729;
    private DirectoryWatcher directoryWatcher;
    private Thread watcherThread;
    private List<String> compileSourceRoots;
    private ReloadingClassLoader reloadingClassLoader;
    private DefaultLauncherRunnable launcherRunnable;
    private LRServer lrServer;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.compileSourceRoots = Collections.unmodifiableList(getProject().getCompileSourceRoots());
        try {
            this.directoryWatcher = new DirectoryWatcher(getLog(), new WatchFileChangeListener());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create directory watcher", e);
        }

        for (String sourceRoot : compileSourceRoots) {
            File file = new File(sourceRoot);
            if (file.isDirectory()) {
                getLog().info("Will watch source directory " + file.getPath());
                try {
                    this.directoryWatcher.watchRecursively(file.toPath());
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to watch directory " + file.getAbsolutePath(), e);
                }
            }
        }

        this.watcherThread = new Thread(this.directoryWatcher, "watcher");
        this.launcherRunnable = new DefaultLauncherRunnable(getArgs(), getMonitor(), getLog());
        execute(launcherRunnable, false);

        Runtime.getRuntime().addShutdownHook(new Thread("watcher") {
            @Override
            public void run() {
                directoryWatcher.stop();
                watcherThread.interrupt();
                try {
                    watcherThread.join(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        });

        // Start live reload server
        try {
            getLog().info("Starting LiveReload server on port " + LIVE_RELOAD_PORT);
            lrServer = new LRServer(LIVE_RELOAD_PORT);
            lrServer.start();
        } catch (Exception e) {
            getLog().error("Unable to start LiveReload server", e);
        }

        // Start watching sources
        watcherThread.start();

        waitForShutdown();

        if (lrServer != null) {
            try {
                lrServer.stop();
            } catch (Exception e) {
                getLog().error("Unable to stop LiveReload server", e);
            }
        }
    }

    @Override
    URLClassLoader getClassLoader(final URL[] classPathUrls) {
        reloadingClassLoader = AccessController.doPrivileged(new PrivilegedAction<ReloadingClassLoader>() {
            public ReloadingClassLoader run() {
                return new ReloadingClassLoader(getLog(), classPathUrls, compileSourceRoots);
            }
        });
        return reloadingClassLoader;
    }

    private class WatchFileChangeListener implements FileChangeListener {
        private static final String COMPILATION_FAILURE_EXCEPTION = "org.apache.maven.plugin.compiler" +
                ".CompilationFailureException";

        private final Semaphore semaphore = new Semaphore(1);
        private final ArrayBlockingQueue<FileEvent> pending = new ArrayBlockingQueue<>(10000);

        @Override
        public void onChange(Set<FileEvent> fileEvents) {
            pending.addAll(fileEvents);
            while (!pending.isEmpty()) {
                boolean permit = false;
                try {
                    permit = semaphore.tryAcquire();
                    if (permit) {
                        HashSet<FileEvent> fileEventsToProcess = new HashSet<>();
                        pending.drainTo(fileEventsToProcess);
                        refresh(fileEventsToProcess);
                    } else {
                        pending.addAll(fileEvents);
                    }
                } finally {
                    if (permit) {
                        semaphore.release();
                    }
                }
            }
        }

        private void refresh(Set<FileEvent> fileEvents) {
            try {
                Set<File> compiledFilesToRemove = new HashSet<>();
                Set<File> compiledFilesToUpdate = new HashSet<>();

                analyzeEvents(fileEvents, compiledFilesToRemove, compiledFilesToUpdate);

                if (!compiledFilesToRemove.isEmpty() || !compiledFilesToUpdate.isEmpty()) {
                    getLog().info("Source changes detected");

                    try {
                        // Invalidate classes from source files that are gone
                        reloadingClassLoader.invalidateClasses(analyzeClasses(compiledFilesToRemove));
                    } catch (RefreshException e) {
                        getLog().info("Cannot detect removed classes, invalidating all classes", e);
                        reloadingClassLoader.invalidateAllClasses();
                    }

                    // Remove compiled files for source files that are gone
                    removeFiles(compiledFilesToRemove);

                    try {
                        // Invalidate classes from source files that have changed
                        reloadingClassLoader.invalidateClasses(analyzeClasses(compiledFilesToUpdate));
                    } catch (RefreshException e) {
                        getLog().info("Cannot detect changed classes, invalidating all classes", e);
                        reloadingClassLoader.invalidateAllClasses();
                    }

                    // Invalidate generated classes
                    reloadingClassLoader.invalidateClassesFromPackage("org.seedstack.business.__generated");

                    // Recompile the sources
                    recompile();

                    // Refresh the app
                    launcherRunnable.refresh();

                    // Trigger LiveReload
                    if (lrServer != null) {
                        getLog().info("Triggering LiveReload");
                        lrServer.notifyChange("/");
                    }

                    getLog().info("Refresh complete");
                }
            } catch (Exception e) {
                Throwable toLog = e.getCause();
                if (toLog == null || !toLog.getClass().getName().equals(COMPILATION_FAILURE_EXCEPTION)) {
                    toLog = e;
                }
                getLog().warn("An error occurred during application refresh, ignoring changes", toLog);
            }
        }

        private void analyzeEvents(Set<FileEvent> fileEvents, Set<File> compiledFilesToRemove,
                Set<File> compiledFilesToUpdate) throws MojoExecutionException {
            for (FileEvent fileEvent : fileEvents) {
                File changedFile = fileEvent.getFile();
                if (!changedFile.isDirectory()) {
                    for (String compileSourceRoot : compileSourceRoots) {
                        try {
                            String canonicalChangedFile = changedFile.getCanonicalPath();
                            String sourceRootPath = new File(compileSourceRoot).getCanonicalPath();
                            if (canonicalChangedFile.startsWith(sourceRootPath + File.separator)
                                    && canonicalChangedFile.endsWith(".java")) {
                                if (fileEvent.getKind() == FileEvent.Kind.CREATE) {
                                    getLog().debug("NEW: " + canonicalChangedFile);
                                    compiledFilesToUpdate.add(resolveCompiledFile(sourceRootPath,
                                            canonicalChangedFile));
                                } else if (fileEvent.getKind() == FileEvent.Kind.MODIFY) {
                                    getLog().debug("MODIFIED: " + canonicalChangedFile);
                                    compiledFilesToUpdate.add(resolveCompiledFile(sourceRootPath,
                                            canonicalChangedFile));
                                } else if (fileEvent.getKind() == FileEvent.Kind.DELETE) {
                                    getLog().debug("DELETED: " + canonicalChangedFile);
                                    compiledFilesToRemove.add(resolveCompiledFile(sourceRootPath,
                                            canonicalChangedFile));
                                }
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException(
                                    "Unable to resolve compiled file for " + changedFile.getAbsolutePath());
                        }
                    }
                }
            }
        }

        private File resolveCompiledFile(String sourceRootPath, String changedFilePath) {
            return new File(getClassesDirectory(), changedFilePath
                    .substring(sourceRootPath.length())
                    .replaceAll("\\.java$", ".class"));
        }

        private void removeFiles(Set<File> compiledFilesToRemove) throws RefreshException {
            for (File file : compiledFilesToRemove) {
                if (file.exists() && !file.delete()) {
                    throw new RefreshException("Unable to remove compiled file " + file.getAbsolutePath());
                }
            }
        }

        private Set<String> analyzeClasses(Set<File> classFiles) throws RefreshException {
            Set<String> classNamesToInvalidate = new HashSet<>();
            for (File file : classFiles) {
                if (file.exists() && file.length() > 0) {
                    classNamesToInvalidate.addAll(collectClassNames(file));
                }
            }
            return classNamesToInvalidate;
        }

        private Set<String> collectClassNames(File classFile) throws RefreshException {
            Set<String> classNames = new HashSet<>();
            try (FileInputStream is = new FileInputStream(classFile)) {
                ClassReader classReader = new ClassReader(is);
                classReader.accept(new ClassNameCollector(classNames), 0);
            } catch (Exception e) {
                throw new RefreshException("Unable to analyze class file " + classFile.getAbsolutePath(), e);
            }
            return classNames;
        }

        private void recompile() throws MojoExecutionException {
            executeMojo(
                    plugin(groupId("org.apache.maven.plugins"), artifactId("maven-compiler-plugin"), version("3.7.0")),
                    goal("compile"),
                    configuration(),
                    executionEnvironment(getProject(), getMavenSession(), getBuildPluginManager())
            );
        }

        private class ClassNameCollector extends ClassVisitor {
            private final Set<String> classNames;

            ClassNameCollector(Set<String> classNames) {
                super(Opcodes.ASM6);
                this.classNames = classNames;
            }

            @Override
            public void visit(int i, int i1, String name, String s1, String s2, String[] strings) {
                classNames.add(name);
            }

            public void visitOuterClass(String owner, String name, String desc) {
                classNames.add(name);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                classNames.add(name);
            }
        }
    }

}
