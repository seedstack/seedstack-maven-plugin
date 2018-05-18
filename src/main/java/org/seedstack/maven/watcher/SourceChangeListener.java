/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.watcher;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import org.apache.maven.plugin.MojoExecutionException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.seedstack.maven.Context;
import org.seedstack.maven.WatchMojo;

public class SourceChangeListener implements FileChangeListener {
    private static final String COMPILATION_FAILURE_EXCEPTION =
            "org.apache.maven.plugin.compiler.CompilationFailureException";
    private final WatchMojo watchMojo;
    private final Context context;
    private final Semaphore semaphore = new Semaphore(1);
    private final ArrayBlockingQueue<FileEvent> pending = new ArrayBlockingQueue<>(10000);

    public SourceChangeListener(WatchMojo watchMojo, Context context) {
        this.watchMojo = watchMojo;
        this.context = context;
    }

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
                watchMojo.getLog().info("Source change(s) detected");

                try {
                    // Invalidate classes from source files that are gone
                    watchMojo.invalidateClasses(analyzeClasses(compiledFilesToRemove));
                } catch (RefreshException e) {
                    watchMojo.getLog().info("Cannot detect removed classes, invalidating all classes", e);
                    watchMojo.invalidateAllClasses();
                }

                // Remove compiled files for source files that are gone
                removeFiles(compiledFilesToRemove);

                try {
                    // Invalidate classes from source files that have changed
                    watchMojo.invalidateClasses(analyzeClasses(compiledFilesToUpdate));
                } catch (RefreshException e) {
                    watchMojo.getLog().info("Cannot detect changed classes, invalidating all classes", e);
                    watchMojo.invalidateAllClasses();
                }

                // Invalidate generated classes
                watchMojo.invalidateClassesFromPackage("org.seedstack.business.__generated");

                // Recompile the sources
                recompile();

                // Refresh the app
                watchMojo.refresh();

                // Trigger LiveReload
                watchMojo.liveReload();

                watchMojo.getLog().info("Refresh complete");
            }
        } catch (Exception e) {
            Throwable toLog = e.getCause();
            if (toLog == null || !toLog.getClass().getName().equals(COMPILATION_FAILURE_EXCEPTION)) {
                toLog = e;
            }
            watchMojo.getLog().warn("An error occurred during application refresh, ignoring changes", toLog);
        }
    }

    private void analyzeEvents(Set<FileEvent> fileEvents, Set<File> compiledFilesToRemove,
            Set<File> compiledFilesToUpdate) throws MojoExecutionException {
        for (FileEvent fileEvent : fileEvents) {
            File changedFile = fileEvent.getFile();
            if (!changedFile.isDirectory()) {
                for (String compileSourceRoot : context.getMavenProject().getCompileSourceRoots()) {
                    try {
                        String canonicalChangedFile = changedFile.getCanonicalPath();
                        String sourceRootPath = new File(compileSourceRoot).getCanonicalPath();
                        if (canonicalChangedFile.startsWith(sourceRootPath + File.separator)
                                && canonicalChangedFile.endsWith(".java")) {
                            if (fileEvent.getKind() == FileEvent.Kind.CREATE) {
                                watchMojo.getLog().debug("NEW: " + changedFile.getPath());
                                compiledFilesToUpdate.add(resolveCompiledFile(sourceRootPath,
                                        canonicalChangedFile));
                            } else if (fileEvent.getKind() == FileEvent.Kind.MODIFY) {
                                watchMojo.getLog().debug("MODIFIED: " + changedFile.getPath());
                                compiledFilesToUpdate.add(resolveCompiledFile(sourceRootPath,
                                        canonicalChangedFile));
                            } else if (fileEvent.getKind() == FileEvent.Kind.DELETE) {
                                watchMojo.getLog().debug("DELETED: " + changedFile.getPath());
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
        return new File(context.getClassesDirectory(), changedFilePath
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
                executionEnvironment(context.getMavenProject(),
                        context.getMavenSession(),
                        context.getBuildPluginManager())
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
