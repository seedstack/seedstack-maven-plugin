/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.seedstack.maven.components.inquirer.Inquirer;
import org.seedstack.maven.components.inquirer.InquirerException;
import org.seedstack.maven.components.prompter.PromptException;
import org.seedstack.maven.components.prompter.Prompter;
import org.seedstack.maven.components.prompter.Value;
import org.seedstack.maven.components.resolver.ArtifactResolver;
import org.seedstack.maven.components.templating.SeedStackExtension;

import com.google.common.base.CaseFormat;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.loader.FileLoader;
import com.mitchellbosecke.pebble.loader.StringLoader;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

/**
 * Defines the generate goal. This goal generates a SeedStack project from existing archetypes.
 *
 * @author adrien.lauer@mpsa.com
 */
@Mojo(name = "generate", requiresProject = false)
public class GenerateMojo extends AbstractSeedStackMojo {
    private static final String ARCHETYPE_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String ARCHETYPE_PLUGIN_ARTIFACT_ID = "maven-archetype-plugin";
    private static final String SEEDSTACK_ORG = "http://seedstack.org/maven/";
    private static final String CENTRAL_REMOTE_URL = "https://repo.maven.apache.org/maven2";
    private PebbleEngine stringTemplateEngine;
    private PebbleEngine fileTemplateEngine;
    @Component
    private ArtifactResolver artifactResolver;
    @Component
    private ArchetypeManager archetypeManager;
    @Component(hint = "basic")
    private Prompter basicPrompter;
    @Component(hint = "fancy")
    private Prompter fancyPrompter;
    @Component(hint = "basic")
    private Inquirer basicInquirer;
    @Component(hint = "fancy")
    private Inquirer fancyInquirer;
    private boolean basicMode;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        MavenSession mavenSession = getContext().getMavenSession();
        MavenProject mavenProject = getContext().getMavenProject();
        if (mavenSession.getUserProperties().getProperty("basicPrompt") == null) {
            this.basicMode = autodetectBasicMode();
        } else {
            this.basicMode = !mavenSession.getUserProperties().getProperty("basicPrompt", "false").equals("false");
        }
        if (!basicMode) {
            getLog().info("If enhanced prompt has issues on your system, try basic prompt by adding \"-DbasicPrompt\"" + " to your command line");
        }

        String type = mavenSession.getUserProperties().getProperty("type"),
                distributionGroupId = mavenSession.getUserProperties().getProperty("distributionGroupId", "org.seedstack"),
                distributionArtifactId = mavenSession.getUserProperties().getProperty("distributionArtifactId", "distribution"),
                version = mavenSession.getUserProperties().getProperty("version"),
                archetypeGroupId = mavenSession.getUserProperties().getProperty("archetypeGroupId"),
                archetypeArtifactId = mavenSession.getUserProperties().getProperty("archetypeArtifactId"),
                archetypeVersion = mavenSession.getUserProperties().getProperty("archetypeVersion"),
                remoteCatalog = mavenSession.getUserProperties().getProperty("remoteCatalog", SEEDSTACK_ORG);
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
                try {
                    // We have a list of possible types, let the user choose (if a "web" choice exists, set it as
                    // default)
                    List<Value> list = new ArrayList<>(findProjectTypes(archetypeGroupId, archetypeVersion, remoteCatalog));
                    Collections.sort(list);
                    list.add(new Value("custom archetype", "custom"));
                    type = getPrompter().promptList("Choose the project type", list, "web");

                    // If the user wants to input a custom archetype
                    if ("custom".equals(type)) {
                        // Ask for archetype group id (defaults to distribution group id)
                        archetypeGroupId = getPrompter().promptInput("Enter the archetype group id", archetypeGroupId);
                        // Ask for archetype artifact id
                        while (archetypeArtifactId == null || archetypeArtifactId.isEmpty()) {
                            archetypeArtifactId = getPrompter().promptInput("Enter the archetype artifact id", null);
                        }
                        // Ask for archetype version (defaults to latest)
                        try {
                            archetypeVersion = artifactResolver.getHighestVersion(mavenProject, archetypeGroupId, archetypeArtifactId,
                                    allowSnapshots);
                        } catch (Exception e) {
                            archetypeVersion = null;
                        }
                        archetypeVersion = getPrompter().promptInput("Enter the archetype version", archetypeVersion);
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
            getLog().info("Resolving latest " + (allowSnapshots ? "snapshot" : "release") + " of archetype " + archetypeGroupId + ":"
                    + archetypeArtifactId);
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
                groupId = getPrompter().promptInput("Generated project group id", "org.generated.project");
            }
            if (StringUtils.isBlank(groupId)) {
                throw new MojoExecutionException("Generated project group id cannot be blank");
            }
            if (StringUtils.isBlank(artifactId)) {
                artifactId = getPrompter().promptInput("Generated project artifact id",
                        "my-" + (StringUtils.isBlank(type) ? "" : type + "-") + "project");
            }
            if (StringUtils.isBlank(artifactId)) {
                throw new MojoExecutionException("Generated project artifact id cannot be blank");
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

                configuration(element(name("interactiveMode"), "false"), element(name("archetypeGroupId"), archetypeGroupId),
                        element(name("archetypeArtifactId"), archetypeArtifactId), element(name("archetypeVersion"), archetypeVersion)),

                executionEnvironment(mavenProject, mavenSession, getContext().getBuildPluginManager()));

        final File projectDir = new File("." + File.separator + artifactId);
        if (projectDir.exists() && projectDir.canWrite()) {
            final File questionFile = new File(projectDir, "questions.json");

            // Create template engines
            stringTemplateEngine = new PebbleEngine.Builder().loader(new StringLoader()).build();
            fileTemplateEngine = new PebbleEngine.Builder().loader(new FileLoader()).extension(new SeedStackExtension(groupId)).build();

            // Create vars
            final HashMap<String, Object> vars = new HashMap<>();

            // Put project values in vars
            HashMap<String, Object> projectVars = new HashMap<>();
            String normalizedLowerName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, artifactId);
            String normalizedUpperName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, normalizedLowerName);
            projectVars.put("type", type);
            projectVars.put("groupId", groupId);
            projectVars.put("artifactId", artifactId);
            projectVars.put("version", version);
            projectVars.put("package", groupId);
            projectVars.put("lowerName", normalizedLowerName);
            projectVars.put("upperName", normalizedUpperName);
            vars.put("project", projectVars);

            // Put archetype values in vars
            HashMap<String, Object> archetypeVars = new HashMap<>();
            archetypeVars.put("groupId", archetypeGroupId);
            archetypeVars.put("artifactId", archetypeArtifactId);
            archetypeVars.put("version", archetypeVersion);
            vars.put("archetype", archetypeVars);

            // If the user cancels during question, complete the process with minimal vars
            Thread shutdownRender = setupInquiryCancelHook(projectDir, questionFile, vars);

            // Inquire from user if a question file is present
            HashMap<String, Object> varsWithAnswers = new HashMap<>(vars);
            try {
                if (questionFile.exists() && questionFile.canRead()) {
                    varsWithAnswers.putAll(getInquirer().inquire(questionFile.toURI().toURL()));
                    if (!questionFile.delete()) {
                        getLog().warn("Unable to delete question file, useless files may be still be present in project");
                    }
                }
            } catch (MalformedURLException | InquirerException e) {
                getLog().error("Unable to process question file, resulting project might be unusable", e);
            }

            // We can now do the rendering properly, without asking anymore question, cancel the shutdown hooks
            Runtime.getRuntime().removeShutdownHook(shutdownRender);

            renderTemplates(projectDir, varsWithAnswers);
        }
    }

    private Thread setupInquiryCancelHook(final File projectDir, final File questionFile, final HashMap<String, Object> vars) {
        Thread shutdownRender = new Thread(new Runnable() {
            @Override
            public void run() {
                getLog().warn("Inquiry has been interrupted, trying to render project as-is");
                renderTemplates(projectDir, vars);
                if (questionFile.exists() && questionFile.canRead() && !questionFile.delete()) {
                    getLog().warn("Unable to delete question file, useless files may be still be present in project");
                }
            }
        }, "render-hook");
        Runtime.getRuntime().addShutdownHook(shutdownRender);
        return shutdownRender;
    }

    private void renderTemplates(File file, Map<String, Object> vars) {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File c : files) {
                    renderTemplates(c, vars);
                }
            }
        }

        if (file.isFile() && file.canWrite()) {
            try {
                String originalPath = file.getCanonicalFile().getAbsolutePath();
                String outputPath = processPath(originalPath, vars);
                PebbleTemplate template = fileTemplateEngine.getTemplate(originalPath);
                StringWriter stringWriter = new StringWriter();

                getLog().info("Rendering template " + outputPath);

                template.evaluate(stringWriter, vars);
                String renderedContent = stringWriter.toString();
                if (renderedContent.trim().length() > 0) {
                    try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
                        writer.write(renderedContent);
                    }
                } else {
                    getLog().info("Rendered content is empty, no output file is being generated");
                    if (!file.delete()) {
                        getLog().warn("Unable to delete template, useless files may be still be present in project");
                    }
                }
                if (!originalPath.equals(outputPath)) {
                    if (!new File(originalPath).delete()) {
                        getLog().warn("Unable to delete original file, useless files may be still be present in project");
                    }
                }
            } catch (PebbleException | IOException e) {
                getLog().error("Unable to render template, resulting project might be unusable", e);
            }
        }
    }

    private String processPath(String absolutePath, Map<String, Object> vars) {
        try {
            StringWriter stringWriter = new StringWriter();
            PebbleTemplate template = stringTemplateEngine.getTemplate(absolutePath);
            template.evaluate(stringWriter, vars);
            return stringWriter.toString();
        } catch (PebbleException | IOException e) {
            getLog().error("Unable to render filename template, resulting project might be unusable", e);
            return absolutePath;
        }
    }

    private Set<Value> findProjectTypes(String archetypeGroupId, String archetypeVersion, String remoteCatalog) {
        Set<Value> possibleTypes = new HashSet<>();
        getLog().info("Searching for " + archetypeVersion + " archetypes in remote catalog " + remoteCatalog);
        possibleTypes.addAll(findArchetypes(archetypeGroupId, archetypeVersion, archetypeManager.getRemoteCatalog(remoteCatalog)));

        if (possibleTypes.isEmpty()) {
            getLog().info("No remote " + archetypeVersion + " archetype found, trying the central catalog");
            possibleTypes.addAll(findArchetypes(archetypeGroupId, archetypeVersion, archetypeManager.getRemoteCatalog(CENTRAL_REMOTE_URL)));
        }
        if (possibleTypes.isEmpty()) {
            getLog().info("No remote or central " + archetypeVersion + " archetype found, trying the local catalog");
            possibleTypes.addAll(findArchetypes(archetypeGroupId, archetypeVersion, archetypeManager.getDefaultLocalCatalog()));
        }
        if (possibleTypes.isEmpty()) {
            getLog().warn(
                    "No " + archetypeVersion + " archetype found anywhere (check your Maven proxy settings), falling " + "back to hard-coded list");
            possibleTypes.add(new Value("addon"));
            possibleTypes.add(new Value("batch"));
            possibleTypes.add(new Value("cli"));
            possibleTypes.add(new Value("domain"));
            possibleTypes.add(new Value("web"));
        }

        return possibleTypes;
    }

    private Set<Value> findArchetypes(String archetypeGroupId, String archetypeVersion, ArchetypeCatalog catalog) {
        Set<Value> possibleTypes = new HashSet<>();
        for (Archetype archetype : catalog.getArchetypes()) {
            if (archetypeGroupId.equals(archetype.getGroupId()) && archetypeVersion.equals(archetype.getVersion())) {
                String artifactId = archetype.getArtifactId();
                if (artifactId.endsWith("-archetype")) {
                    possibleTypes.add(new Value(artifactId.substring(0, artifactId.length() - 10)));
                }
            }
        }
        return possibleTypes;
    }

    private Prompter getPrompter() {
        if (basicMode) {
            return basicPrompter;
        } else {
            return fancyPrompter;
        }
    }

    private Inquirer getInquirer() {
        if (basicMode) {
            return basicInquirer;
        } else {
            return fancyInquirer;
        }
    }

    private boolean autodetectBasicMode() {
        return Context.isCygwin() || Context.isMingwXterm();
    }
}