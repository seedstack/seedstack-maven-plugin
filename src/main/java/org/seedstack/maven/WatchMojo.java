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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.seedstack.maven.classloader.ReloadingClassLoader;
import org.seedstack.maven.livereload.LRServer;
import org.seedstack.maven.runnables.DefaultLauncherRunnable;
import org.seedstack.maven.watcher.AggregatingFileChangeListener;
import org.seedstack.maven.watcher.DirectoryWatcher;
import org.seedstack.maven.watcher.FileEvent;

/**
 * Defines the run goal. This goal runs a SeedStack project.
 */
@Mojo(name = "watch", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class WatchMojo extends AbstractExecutableMojo {
    public static final int LIVE_RELOAD_PORT = 35729;
    private DirectoryWatcher directoryWatcher;
    private Thread watcherThread;
    private AggregatingFileChangeListener fileChangeListener;
    private List<String> compileSourceRoots;
    private ReloadingClassLoader reloadingClassLoader;
    private DefaultLauncherRunnable launcherRunnable;
    private LRServer lrServer;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.compileSourceRoots = Collections.unmodifiableList(getProject().getCompileSourceRoots());
        this.fileChangeListener = new WatchFileChangeListener();
        try {
            this.directoryWatcher = new DirectoryWatcher(getLog(), fileChangeListener);
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
                fileChangeListener.stop();
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
        this.watcherThread.start();
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
                return new ReloadingClassLoader(getLog(), classPathUrls);
            }
        });
        return reloadingClassLoader;
    }

    private class WatchFileChangeListener extends AggregatingFileChangeListener {
        @Override
        public void onAggregatedChanges(Set<FileEvent> fileEvents) {
            try {
                if (getLog().isDebugEnabled()) {
                    for (FileEvent fileEvent : fileEvents) {
                        getLog().debug(fileEvent.getKind().name() + ": " + fileEvent.getFile().getAbsolutePath());
                    }
                }

                Set<File> compiledFilesToRemove = new HashSet<>();
                Set<File> compiledFilesToUpdate = new HashSet<>();

                analyzeEvents(fileEvents, compiledFilesToRemove, compiledFilesToUpdate);

                if (!compiledFilesToRemove.isEmpty() || !compiledFilesToUpdate.isEmpty()) {
                    reloadingClassLoader.invalidateClasses(
                            analyzeClasses(compiledFilesToRemove, compiledFilesToUpdate)
                    );
                    removeFiles(compiledFilesToRemove);
                    recompile();
                    launcherRunnable.refresh();
                    if (lrServer != null) {
                        getLog().info("Triggering LiveReload");
                        lrServer.notifyChange("/");
                    }
                }
            } catch (Exception e) {
                Throwable toLog = e.getCause();
                if (toLog == null || !toLog.getClass().getName()
                        .equals("org.apache.maven.plugin.compiler.CompilationFailureException")) {
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
                            String changedFilePath = changedFile.getCanonicalPath();
                            String sourceRootPath = new File(compileSourceRoot).getCanonicalPath();
                            if (changedFilePath.startsWith(sourceRootPath + File.separator)
                                    && changedFilePath.endsWith(".java")) {
                                String compiledFile = changedFilePath.substring(sourceRootPath.length());
                                compiledFile = compiledFile.replaceAll("\\.java$", ".class");
                                File fullCompiledFile = new File(getClassesDirectory(), compiledFile);
                                switch (fileEvent.getKind()) {
                                    case MODIFY:
                                        compiledFilesToUpdate.add(fullCompiledFile);
                                        break;
                                    case DELETE:
                                        compiledFilesToRemove.add(fullCompiledFile);
                                        break;
                                    default:
                                        // nothing to do
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

        private void removeFiles(Set<File> compiledFilesToRemove) throws MojoExecutionException {
            for (File file : compiledFilesToRemove) {
                if (!file.delete()) {
                    throw new MojoExecutionException("Unable to remove compiled file " + file.getAbsolutePath());
                }
            }
        }

        private Set<String> analyzeClasses(Set<File> compiledFilesToRemove, Set<File> compiledFilesToUpdate) {
            Set<String> classNamesToInvalidate = new HashSet<>();
            for (File file : compiledFilesToRemove) {
                classNamesToInvalidate.addAll(collectClassNames(file));
            }
            for (File file : compiledFilesToUpdate) {
                classNamesToInvalidate.addAll(collectClassNames(file));
            }
            return classNamesToInvalidate;
        }

        private Set<String> collectClassNames(File classFile) {
            Set<String> classNames = new HashSet<>();
            try (FileInputStream is = new FileInputStream(classFile)) {
                ClassReader classReader = new ClassReader(is);
                classReader.accept(new ClassNameCollector(classNames), 0);
            } catch (IOException e) {
                getLog().warn("Unable to parse class file " + classFile.getAbsolutePath());
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

        private class ClassNameCollector implements ClassVisitor {
            private final Set<String> classNames;

            ClassNameCollector(Set<String> classNames) {
                this.classNames = classNames;
            }

            @Override
            public void visit(int i, int i1, String name, String s1, String s2, String[] strings) {
                classNames.add(name);
            }

            @Override
            public void visitSource(String s, String s1) {
            }

            @Override
            public void visitOuterClass(String owner, String name, String desc) {
                classNames.add(name);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String s, boolean b) {
                return null;
            }

            @Override
            public void visitAttribute(Attribute attribute) {
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                classNames.add(name);
            }

            @Override
            public FieldVisitor visitField(int i, String s, String s1, String s2, Object o) {
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int i, String s, String s1, String s2, String[] strings) {
                return null;
            }

            @Override
            public void visitEnd() {
            }
        }
    }
}
