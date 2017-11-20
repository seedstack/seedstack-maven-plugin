/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
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
import org.seedstack.maven.runnables.DefaultLauncherRunnable;

/**
 * Defines the crypt goal. This goal runs the crypt Seed tool which crypts the given argument using a key/pair in
 * the master keystore of the application.
 */
@Mojo(name = "run", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class RunMojo extends AbstractExecutableMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        execute(new DefaultLauncherRunnable(getArgs(), getMonitor(), getLog()), false);
        waitForShutdown();
    }
}
