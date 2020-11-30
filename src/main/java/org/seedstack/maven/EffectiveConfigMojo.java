/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.seedstack.maven.runnables.ToolRunnable;

/**
 * Defines the effective-config goal. This goal runs the effective-config Seed tool which dumps the effective
 * configuration of
 * the application.
 */
@Mojo(name = "effective-config", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class EffectiveConfigMojo extends AbstractExecutableMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        execute(new ToolRunnable("effective-config", getContext()), false);
        waitForShutdown();
    }
}
