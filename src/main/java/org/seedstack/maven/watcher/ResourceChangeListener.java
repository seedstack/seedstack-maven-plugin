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
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.seedstack.maven.Context;
import org.seedstack.maven.WatchMojo;

public class ResourceChangeListener implements FileChangeListener {
    private final WatchMojo watchMojo;
    private final Context context;

    public ResourceChangeListener(WatchMojo watchMojo, Context context) {
        this.watchMojo = watchMojo;
        this.context = context;
    }

    @Override
    public void onChange(Set<FileEvent> fileEvents) {
        boolean configChanged = false;
        boolean staticResourceChanged = false;
        watchMojo.getLog().info("Resource change(s) detected");

        for (FileEvent fileEvent : fileEvents) {
            if (fileEvent.getKind() == FileEvent.Kind.CREATE) {
                watchMojo.getLog().debug("NEW: " + fileEvent.getFile().getPath());
            } else if (fileEvent.getKind() == FileEvent.Kind.MODIFY) {
                watchMojo.getLog().debug("MODIFIED: " + fileEvent.getFile().getPath());
            } else if (fileEvent.getKind() == FileEvent.Kind.DELETE) {
                watchMojo.getLog().debug("DELETED: " + fileEvent.getFile().getPath());
            }

            if (isConfigFile(fileEvent.getFile())) {
                configChanged = true;
            }
        }

        try {
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
}
