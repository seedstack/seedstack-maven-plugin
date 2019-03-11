/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import java.io.File;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.fusesource.jansi.AnsiConsole;

abstract class AbstractSeedStackMojo extends AbstractMojo {
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                AnsiConsole.systemUninstall();
            }
        }, "uninstall-ansi"));
        AnsiConsole.systemInstall();
    }

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject mavenProject;
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession mavenSession;
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true)
    private File testClassesDirectory;
    @Parameter(property = "args")
    private String args;
    @Component
    private BuildPluginManager buildPluginManager;
    private Context context;

    public synchronized Context getContext() {
        if (context == null) {
            try {
                context = new Context(
                        CommandLineUtils.translateCommandline(this.args),
                        getLog(),
                        classesDirectory,
                        testClassesDirectory,
                        mavenProject,
                        mavenSession,
                        buildPluginManager
                );
            } catch (Exception e) {
                throw new RuntimeException("Unable to create execution context", e);
            }
        }
        return context;
    }
}
