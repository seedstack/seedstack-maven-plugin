/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.seedstack.maven.Context;
import org.seedstack.maven.WatchMojo;

public class ResourceChangeListener extends AbstractFileChangeListener {
    public ResourceChangeListener(WatchMojo watchMojo, Context context) {
        super(watchMojo, context);
    }

    @Override
    protected void refresh(Set<FileEvent> fileEvents) {
        boolean configChanged = false;
        List<File> resourcesToDelete = new ArrayList<>();
        watchMojo.getLog().info("Resource change(s) detected");

        try {
            for (FileEvent fileEvent : fileEvents) {
                File changedFile = fileEvent.getFile();
                if (fileEvent.getKind() == FileEvent.Kind.CREATE) {
                    watchMojo.getLog().debug("NEW: " + changedFile.getPath());
                } else if (fileEvent.getKind() == FileEvent.Kind.MODIFY) {
                    watchMojo.getLog().debug("MODIFIED: " + changedFile.getPath());
                } else if (fileEvent.getKind() == FileEvent.Kind.DELETE) {
                    watchMojo.getLog().debug("DELETED: " + changedFile.getPath());
                    try {
                        resourcesToDelete.add(resolveResourceFile(changedFile));
                    } catch (IOException e) {
                        throw new MojoExecutionException("Unable to resolve target file for resource " + changedFile.getAbsolutePath());
                    }
                }

                if (isConfigFile(changedFile)) {
                    configChanged = true;
                }
            }

            for (File file : resourcesToDelete) {
                watchMojo.getLog().info("Deleting missing resource " + file.getAbsolutePath());
                if (!file.delete()) {
                    watchMojo.getLog().warn("Unable to delete resource " + file.getAbsolutePath());
                }
            }
            watchMojo.getLog().info("Updating resources");
            copyResources();

            if (configChanged) {
                // Wait for the application to notice the change
                watchMojo.getLog()
                        .info("A configuration file has changed, waiting for the application to notice it");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            watchMojo.liveReload();
        } catch (MojoExecutionException e) {
            watchMojo.getLog().warn("An error occurred during resource copy, ignoring resource changes", e);
        }
    }

    private boolean isConfigFile(File file) {
        String fileName = file.getName();
        return fileName.equals("application.yaml")
                || fileName.equals("application.override.yaml")
                || fileName.equals("application.json")
                || fileName.equals("application.override.json")
                || fileName.equals("application.properties")
                || fileName.equals("application.override.properties")
                || file.getParentFile().getPath().endsWith("META-INF" + File.separator + "configuration");
    }

    private void copyResources() throws MojoExecutionException {
        executeMojo(
                plugin(groupId("org.apache.maven.plugins"), artifactId("maven-resources-plugin"), version("3.1.0")),
                goal("resources"),
                configuration(),
                executionEnvironment(context.getMavenProject(),
                        context.getMavenSession(),
                        context.getBuildPluginManager())
        );
    }

    private File resolveResourceFile(File changedFile) throws IOException {
        String outputDirectory = context.getMavenProject().getBuild().getOutputDirectory();
        for (Resource resource : context.getMavenProject().getResources()) {
            try {
                String resourceDir = new File(resource.getDirectory()).getCanonicalPath();
                String filePath = changedFile.getCanonicalPath();
                if (filePath.startsWith(resourceDir)) {
                    String relativePath = filePath.substring(resourceDir.length());
                    String targetPath = resource.getTargetPath();
                    File targetDir;
                    if (targetPath == null || targetPath.isEmpty()) {
                        targetDir = new File(outputDirectory);
                    } else {
                        targetDir = new File(outputDirectory, targetPath);
                    }
                    return new File(targetDir, relativePath);
                }
            } catch (IOException e) {
                throw new IOException("Unable to resolve resource file path: " + changedFile.getAbsolutePath(), e);
            }
        }
        return null;
    }
}