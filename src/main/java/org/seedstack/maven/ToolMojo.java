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
import org.seedstack.maven.runnables.ToolLauncherRunnable;

/**
 * Defines the tool goal. This goal runs a Seed tool specified with by the "tool" property.
 * Arguments to the tool can be given with the "args" property.
 */
@Mojo(name = "tool", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class ToolMojo extends AbstractExecutableMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String[] args = getArgs();
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new MojoExecutionException(
                    "No tool specified: specify the tool name as the first argument in the 'args' property");
        }
        String toolName = args[0];
        String[] shiftedArgs = new String[args.length - 1];
        System.arraycopy(args, 1, shiftedArgs, 0, args.length - 1);

        execute(new ToolLauncherRunnable(toolName, shiftedArgs, getMonitor(), getLog()), false);
        waitForShutdown();
    }
}
