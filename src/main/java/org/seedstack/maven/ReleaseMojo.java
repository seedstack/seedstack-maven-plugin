/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Release the project simply by stripping the -SNAPSHOT part of the version.
 * Useful in continuous delivery pipelines.
 *
 * @author adrien.lauer@gmail.com
 */
@Mojo(name = "release", requiresProject = true, threadSafe = false, aggregator = true)
public class ReleaseMojo extends AbstractMojo {
    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    @Component
    private MavenProject executionMavenProject;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager buildPluginManager;

    @Component
    private ProjectBuilder projectBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String currentVersion = executionMavenProject.getVersion();

        if (isProjectDirty(getReactorModules("", executionMavenProject))) {
            throw new MojoFailureException("Cannot continue, a POM transformation is already in progress, commit and revert it before executing the release goal");
        }

        if (!executionMavenProject.equals(getLocalRoot(executionMavenProject))) {
            throw new MojoFailureException("Cannot continue, release goal must be executed from the local project root");
        }

        if (!currentVersion.endsWith(SNAPSHOT_SUFFIX)) {
            throw new MojoFailureException("Cannot continue, project version is not a SNAPSHOT");
        }

        if (executionMavenProject.hasParent() && executionMavenProject.getParent().getVersion().endsWith(SNAPSHOT_SUFFIX)) {
            throw new MojoFailureException("Cannot continue, parent project is still a SNAPSHOT");
        }

        String newVersion = currentVersion.substring(0, currentVersion.length() - SNAPSHOT_SUFFIX.length());
        getLog().info("SNAPSHOT version found, setting release version to " + newVersion);
        executionMavenProject.getProperties().setProperty("newVersion", newVersion);
        executeVersionsPlugin("set", executionMavenProject);

        boolean shouldRevert = false;
        List<MavenProject> transformedModules = new ArrayList<>();
        transformedModules.add(executionMavenProject);
        transformedModules.addAll(getReactorModules("", executionMavenProject).values());
        getLog().info("Checking transformed modules");
        for (MavenProject transformedModule : transformedModules) {
            try {
                checkModule(transformedModule);
            } catch (Exception e) {
                getLog().error("Module " + transformedModule.getArtifactId() + " is not valid\n" + e.getMessage());
                shouldRevert = true;
            }
        }

        if (shouldRevert) {
            getLog().info("Reverting transformations");
            for (MavenProject transformedModule : transformedModules) {
                revertModule(transformedModule);
            }
            throw new MojoFailureException("Release was aborted due to previous errors");
        } else {
            getLog().info("Committing transformations");
            for (MavenProject transformedModule : transformedModules) {
                commitModule(transformedModule);
            }
        }
    }

    private void revertModule(MavenProject mavenProject) {
        try {
            executeVersionsPlugin("revert", mavenProject);
        } catch (MojoExecutionException e) {
            getLog().error("Unable to revert module " + mavenProject.getArtifactId());
        }
    }

    private void commitModule(MavenProject mavenProject) throws MojoExecutionException {
        executeVersionsPlugin("commit", mavenProject);
    }

    private void checkModule(MavenProject mavenProject) throws MojoFailureException {
        Map<String, Dependency> snapshotDependencies = new HashMap<String, Dependency>();
        for (Dependency dependency : mavenProject.getDependencies()) {
            if (dependency.getVersion().endsWith(SNAPSHOT_SUFFIX)) {
                snapshotDependencies.put(mavenProject.getArtifactId(), dependency);
            }
        }

        if (!snapshotDependencies.isEmpty()) {
            StringBuilder sb = new StringBuilder("Cannot continue, there are still SNAPSHOT dependencies in the project:\n");
            for (Map.Entry<String, Dependency> dependencyEntry : snapshotDependencies.entrySet()) {
                sb.append("\t* ").append(dependencyEntry.getKey()).append(": ").append(dependencyEntry.getValue().getManagementKey()).append("\n");
            }

            throw new MojoFailureException(sb.toString());
        }
    }

    private void executeVersionsPlugin(String goal, MavenProject mavenProject) throws MojoExecutionException {
        MavenProject oldProject = mavenSession.getCurrentProject();
        mavenSession.setCurrentProject(mavenProject);

        executeMojo(
                plugin(
                        groupId("org.codehaus.mojo"),
                        artifactId("versions-maven-plugin"),
                        version("2.2")
                ),
                goal(goal),
                configuration(),
                executionEnvironment(mavenProject, mavenSession, buildPluginManager));

        mavenSession.setCurrentProject(oldProject);
    }

    private boolean isTransformationInProgress(MavenProject mavenProject) {
        return new File(mavenProject.getFile().getParentFile(), "pom.xml.versionsBackup").exists();
    }

    private boolean isProjectDirty(Map<String, MavenProject> reactorProjects) throws MojoFailureException {
        for (MavenProject reactorProject : reactorProjects.values()) {
            if (isTransformationInProgress(reactorProject)) {
                return true;
            }
        }

        return false;
    }

    private Map<String, MavenProject> getReactorModules(String path, MavenProject project) throws MojoFailureException {
        if (path.length() > 0 && !path.endsWith("/")) {
            path += '/';
        }

        Map<String, MavenProject> result = new LinkedHashMap<String, MavenProject>();
        Map<String, MavenProject> childResults = new LinkedHashMap<String, MavenProject>();

        Set<String> childModules = getChildModules(project);
        for (String moduleName : childModules) {
            String modulePath = path + moduleName;

            File moduleDir = new File(project.getBasedir(), moduleName);

            File moduleProjectFile;

            if (moduleDir.isDirectory()) {
                moduleProjectFile = new File(moduleDir, "pom.xml");
            } else {
                // i don't think this should ever happen... but just in case
                // the module references the file-name
                moduleProjectFile = moduleDir;
            }

            try {
                MavenProject moduleProject = buildProject(moduleProjectFile);
                result.put(modulePath, moduleProject);
                childResults.putAll(getReactorModules(modulePath, moduleProject));
            } catch (ProjectBuildingException e) {
                throw new MojoFailureException("Could not build project of " + moduleProjectFile.getPath(), e);
            }
        }
        result.putAll(childResults);
        return result;
    }


    private Set<String> getChildModules(MavenProject mavenProject) {
        Set<String> childModules = new TreeSet<String>();
        childModules.addAll(mavenProject.getModules());
        for (Profile profile : mavenProject.getModel().getProfiles()) {
            childModules.addAll(profile.getModules());
        }
        return childModules;
    }

    private MavenProject getLocalRoot(MavenProject project) {
        while (true) {
            final File parentDir = project.getBasedir().getParentFile();
            if (parentDir.isDirectory()) {
                File parent = new File(parentDir, "pom.xml");
                if (parent.isFile()) {
                    try {
                        final MavenProject parentProject = buildProject(parent);
                        if (getChildModules(parentProject).contains(project.getBasedir().getName())) {
                            project = parentProject;
                            continue;
                        }
                    } catch (ProjectBuildingException e) {
                        getLog().warn(e);
                    }
                }
            }

            return project;
        }
    }

    private MavenProject buildProject(File moduleProjectFile) throws ProjectBuildingException {
        DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        request.setSystemProperties(System.getProperties());
        request.setRepositorySession(executionMavenProject.getProjectBuildingRequest().getRepositorySession());
        return projectBuilder.build(moduleProjectFile, request).getProject();
    }
}
