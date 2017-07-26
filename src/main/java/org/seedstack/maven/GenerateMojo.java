/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.seedstack.maven.components.inquirer.Inquirer;
import org.seedstack.maven.components.inquirer.InquirerException;
import org.seedstack.maven.components.prompter.PromptException;
import org.seedstack.maven.components.prompter.Prompter;
import org.seedstack.maven.components.prompter.Value;
import org.seedstack.maven.components.resolver.ArtifactResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Defines the generate goal. This goal generates a SeedStack project from existing archetypes.
 *
 * @author adrien.lauer@mpsa.com
 */
@Mojo(name = "generate", requiresProject = false)
public class GenerateMojo extends AbstractMojo {
    private static final String ARCHETYPE_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String ARCHETYPE_PLUGIN_ARTIFACT_ID = "maven-archetype-plugin";
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager buildPluginManager;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private ArchetypeManager archetypeManager;

    @Component
    private Prompter prompter;

    @Component
    private Inquirer inquire;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String type = mavenSession.getUserProperties().getProperty("type"),
                distributionGroupId = mavenSession.getUserProperties().getProperty("distributionGroupId", "org.seedstack"),
                distributionArtifactId = mavenSession.getUserProperties().getProperty("distributionArtifactId", "distribution"),
                version = mavenSession.getUserProperties().getProperty("version"),
                archetypeGroupId = mavenSession.getUserProperties().getProperty("archetypeGroupId"),
                archetypeArtifactId = mavenSession.getUserProperties().getProperty("archetypeArtifactId"),
                archetypeVersion = mavenSession.getUserProperties().getProperty("archetypeVersion");
        boolean allowSnapshots = !mavenSession.getUserProperties().getProperty("allowSnapshots", "false").equals("false");


        if (StringUtils.isBlank(archetypeGroupId)) {
            archetypeGroupId = distributionGroupId;
        }

        if (StringUtils.isBlank(archetypeArtifactId)) {
            if (StringUtils.isBlank(type)) {
                // Resolve archetype version using SeedStack highest version
                if (StringUtils.isBlank(archetypeVersion)) {
                    getLog().info("Resolving latest " + (allowSnapshots ? "snapshot" : "release") + " of SeedStack (" + distributionGroupId + ")");
                    archetypeVersion = artifactResolver.getHighestVersion(mavenProject, distributionGroupId, distributionArtifactId, allowSnapshots);
                    getLog().info("Resolved version " + archetypeVersion);
                }
                Set<String> possibleTypes = findProjectTypes(archetypeGroupId, archetypeVersion);
                try {
                    // We have a list of possible types, let the user choose
                    if (!possibleTypes.isEmpty()) {
                        ArrayList<String> list = new ArrayList<>(possibleTypes);
                        Collections.sort(list);
                        list.add("custom archetype");
                        type = prompter.promptList("Choose the project type", Value.convertList(list));
                    }
                    // No possible types or the user wants to input a custom archetype
                    if (possibleTypes.isEmpty() || "custom archetype".equals(type)) {
                        // Ask for archetype group id (defaults to distribution group id)
                        archetypeGroupId = prompter.promptInput("Enter the archetype group id", archetypeGroupId);
                        // Ask for archetype artifact id
                        archetypeArtifactId = prompter.promptInput("Enter the archetype artifact id", null);
                        // Ask for archetype version (defaults to latest)
                        try {
                            archetypeVersion = artifactResolver.getHighestVersion(mavenProject, archetypeGroupId, archetypeArtifactId, allowSnapshots);
                        } catch (Exception e) {
                            archetypeVersion = null;
                        }
                        archetypeVersion = prompter.promptInput("Enter the archetype version", archetypeVersion);
                    } else {
                        archetypeArtifactId = String.format("%s-archetype", type);
                    }
                } catch (PromptException e) {
                    throw new MojoExecutionException("Cannot continue without choosing an archetype/project type", e);
                }
            } else {
                archetypeArtifactId = String.format("%s-archetype", type);
            }
        }

        // If needed, find the latest version of the archetype
        if (StringUtils.isBlank(archetypeVersion)) {
            getLog().info("Resolving latest " + (allowSnapshots ? "snapshot" : "release") + " of archetype " + archetypeGroupId + ":" + archetypeArtifactId);
            archetypeVersion = artifactResolver.getHighestVersion(mavenProject, archetypeGroupId, archetypeArtifactId, allowSnapshots);
            getLog().info("Resolved version " + archetypeVersion);
        }

        if (StringUtils.isBlank(version)) {
            version = "1.0.0-SNAPSHOT";
        }

        mavenSession.getUserProperties().setProperty("version", version);

        String groupId = mavenSession.getUserProperties().getProperty("groupId");
        String artifactId = mavenSession.getUserProperties().getProperty("artifactId");
        try {
            if (StringUtils.isBlank(groupId)) {
                groupId = prompter.promptInput("Generated project group id", null);
            }
            if (StringUtils.isBlank(artifactId)) {
                artifactId = prompter.promptInput("Generated project artifact id", null);
            }
        } catch (PromptException e) {
            throw new MojoExecutionException("Generated project group id and artifact id are required", e);
        }

        mavenSession.getUserProperties().put("groupId", groupId);
        mavenSession.getUserProperties().put("artifactId", artifactId);

        String pluginVersion;
        try {
            pluginVersion = artifactResolver.getHighestVersion(mavenProject, ARCHETYPE_PLUGIN_GROUP_ID, ARCHETYPE_PLUGIN_ARTIFACT_ID, false);
            getLog().info("Using the latest version of archetype plugin: " + pluginVersion);
        } catch (Exception e) {
            getLog().warn("Unable to determine latest version of archetype plugin, falling back to 3.0.0");
            pluginVersion = "3.0.0";
        }

        executeMojo(plugin(groupId(ARCHETYPE_PLUGIN_GROUP_ID), artifactId(ARCHETYPE_PLUGIN_ARTIFACT_ID), version(pluginVersion)),

                goal("generate"),

                configuration(
                        element(name("interactiveMode"), "true"),
                        element(name("archetypeGroupId"), archetypeGroupId),
                        element(name("archetypeArtifactId"), archetypeArtifactId),
                        element(name("archetypeVersion"), archetypeVersion)
                ),

                executionEnvironment(mavenProject, mavenSession, buildPluginManager));

        File projectDir = new File("." + File.separator + artifactId);
        if (projectDir.exists() && projectDir.canWrite()) {
            PebbleEngine engine = new PebbleEngine.Builder().build();
            HashMap<String, Object> vars = new HashMap<>();

            // Put project values in vars
            HashMap<String, Object> projectVars = new HashMap<>();
            projectVars.put("type", type);
            projectVars.put("groupId", groupId);
            projectVars.put("artifactId", artifactId);
            projectVars.put("version", version);
            vars.put("project", projectVars);

            // Put archetype values in vars
            HashMap<String, Object> archetypeVars = new HashMap<>();
            archetypeVars.put("groupId", archetypeGroupId);
            archetypeVars.put("artifactId", archetypeArtifactId);
            archetypeVars.put("version", archetypeVersion);
            vars.put("archetype", archetypeVars);

            // Inquire from user if a question file is present
            try {
                File questionFile = new File(projectDir, "questions.json");
                if (questionFile.exists() && questionFile.canRead()) {
                    vars.putAll(inquire.inquire(questionFile.toURI().toURL()));
                    if (!questionFile.delete()) {
                        getLog().warn("Unable to delete question file, useless files may be still be present in project");
                    }
                }
            } catch (MalformedURLException | InquirerException e) {
                getLog().error("Unable to process question file, resulting project might be unusable", e);
            }

            renderTemplates(projectDir, engine, vars);
        }
    }

    private void renderTemplates(File file, PebbleEngine engine, Map<String, Object> vars) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File c : files) {
                    renderTemplates(c, engine, vars);
                }
            }
        }

        if (file.isFile() && file.canWrite()) {
            getLog().info("Rendering template " + file.getPath());
            try {
                String absolutePath = file.getCanonicalFile().getAbsolutePath();
                PebbleTemplate template = engine.getTemplate(absolutePath);
                StringWriter stringWriter = new StringWriter();

                template.evaluate(stringWriter, vars);
                String renderedContent = stringWriter.toString();
                if (renderedContent.trim().length() > 0) {
                    try (Writer writer = new OutputStreamWriter(new FileOutputStream(absolutePath), StandardCharsets.UTF_8)) {
                        writer.write(renderedContent);
                    }
                } else {
                    getLog().info("Rendered content is empty, no output file is being generated");
                    if (!file.delete()) {
                        getLog().warn("Unable to delete template, useless files may be still be present in project");
                    }
                }
            } catch (PebbleException | IOException e) {
                getLog().error("Unable to render template, resulting project might be unusable", e);
            }
        }
    }

    private Set<String> findProjectTypes(String archetypeGroupId, String archetypeVersion) {
        Set<String> possibleTypes = new HashSet<>();
        getLog().info("Searching for archetypes in remote catalog");
        possibleTypes.addAll(findArchetypes(archetypeGroupId, archetypeVersion, archetypeManager.getRemoteCatalog()));

        if (possibleTypes.isEmpty()) {
            getLog().info("No remote archetype found with version " + archetypeVersion + ", trying the local catalog");
            possibleTypes.addAll(findArchetypes(archetypeGroupId, archetypeVersion, archetypeManager.getDefaultLocalCatalog()));
        }
        if (possibleTypes.isEmpty()) {
            getLog().info("No suitable archetype found");
        }

        return possibleTypes;
    }

    private Set<String> findArchetypes(String archetypeGroupId, String archetypeVersion, ArchetypeCatalog catalog) {
        Set<String> possibleTypes = new HashSet<>();
        for (Archetype archetype : catalog.getArchetypes()) {
            if (archetypeGroupId.equals(archetype.getGroupId()) && archetypeVersion.equals(archetype.getVersion())) {
                String artifactId = archetype.getArtifactId();
                if (artifactId.endsWith("-archetype")) {
                    possibleTypes.add(artifactId.substring(0, artifactId.length() - 10));
                }
            }
        }
        return possibleTypes;
    }
}