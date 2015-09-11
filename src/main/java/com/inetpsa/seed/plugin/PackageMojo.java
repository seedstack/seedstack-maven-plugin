/**
 * Copyright (c) 2013-2015 by The SeedStack authors. All rights reserved.
 *
 * This file is part of SeedStack, An enterprise-oriented full development stack.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.inetpsa.seed.plugin;

import com.google.common.base.Strings;
import com.inetpsa.seed.plugin.components.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Defines the package goal. This goal packages a SeedStack project as a capsule.
 *
 * @author adrien.lauer@gmail.com
 */
@Mojo(name = "package", requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.PACKAGE)
@Execute(phase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractMojo {
    public static final String CAPSULE_GROUP_ID = "co.paralleluniverse";
    public static final String CAPSULE_ARTIFACT_ID = "capsule";
    public static final String MAVEN_CAPLET_ARTIFACT_ID = "capsule-maven";
    public static final String CAPSULE_CLASS = "Capsule.class";
    public static final String MAVEN_CAPLET_CLASS = "MavenCapsule.class";
    public static final String PREMAIN_CLASS = "Premain-Class";
    public static final String APPLICATION_CLASS = "Application-Class";
    public static final String APPLICATION_NAME = "Application-Name";
    public static final String ALLOW_SNAPSHOTS = "Allow-Snapshots";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession mavenSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", required = true, readonly = true)
    protected List<RemoteRepository> remoteRepositories = null;

    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    protected String finalName;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    protected File classesDirectory;

    @Parameter(defaultValue = "${project.build.directory}")
    private File outputDirectory;

    @Parameter(property = "mainClass", defaultValue = SeedStackConstants.mainClassName, required = true)
    private String mainClass;

    @Parameter(property = "capsuleVersion")
    private String capsuleVersion;

    @Parameter(property = "standalone")
    private String standalone;

    @Parameter(property = "allowSnapshots")
    private String allowSnapshots;

    @Component
    private BuildPluginManager buildPluginManager;

    @Component
    private ArtifactResolver artifactResolver;

    enum Type {
        light,
        standalone
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        MavenProjectHelper helper = new DefaultMavenProjectHelper();

        if (capsuleVersion == null) {
            capsuleVersion = artifactResolver.getHighestVersion(mavenProject, CAPSULE_GROUP_ID, CAPSULE_ARTIFACT_ID, false);
        }

        if (!outputDirectory.exists()) {
            boolean success = outputDirectory.mkdirs();
            if (!success) throw new MojoFailureException("Unable to create output directory");
        }

        getLog().info("Packaging SeedStack application using Capsule version " + capsuleVersion);

        File capsuleFile;
        if (standalone != null) {
            try {
                capsuleFile = buildStandalone();
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to build standalone Capsule", e);
            }
        } else {
            try {
                capsuleFile = buildLight();
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to build lightweight Capsule", e);
            }
        }

        helper.attachArtifact(mavenProject, capsuleFile, "capsule");
    }

    public File buildLight() throws IOException, ArtifactResolutionException, DependencyResolutionException {
        File jarFile = new File(this.outputDirectory, getOutputName());
        JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));

        // Manifest
        Map<String, String> additionalAttributes = new HashMap<String, String>();
        additionalAttributes.put("Dependencies", getDependencyString());
        additionalAttributes.put("Repositories", getRepoString());
        addManifest(jarStream, additionalAttributes, Type.light);

        // Main JAR
        File mainJarFile = new File(outputDirectory, finalName + ".jar");
        addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jarStream);

        // Capsule classes
        addCapsuleClasses(jarStream);
        addMavenCapletClasses(jarStream);

        IOUtil.close(jarStream);

        return jarFile;
    }

    public File buildStandalone() throws IOException, MojoExecutionException, ArtifactResolutionException, DependencyResolutionException {
        DependencyFilter dependencyFilter = new DependencyFilter() {
            @Override
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                return "jar".equals(node.getArtifact().getExtension());
            }
        };
        File jarFile = new File(outputDirectory, getOutputName());
        JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));

        // Manifest
        addManifest(jarStream, null, Type.standalone);

        // Main JAR
        File mainJarFile = new File(outputDirectory, finalName + ".jar");
        addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jarStream);

        // Dependencies
        for (Artifact artifact : getProjectArtifacts(dependencyFilter)) {
            getLog().debug("Adding " + artifact);
            if (artifact.getFile() == null) {
                throw new MojoExecutionException("Unable to find artifact " + artifact);
            } else {
                addToJar(artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jarStream);
            }
        }

        // Capsule classes
        addCapsuleClasses(jarStream);

        IOUtil.close(jarStream);

        return jarFile;
    }

    private String getOutputName() {
        return String.format("%s-capsule.jar", finalName);
    }

    private void addCapsuleClasses(JarOutputStream jarOutputStream) throws IOException, ArtifactResolutionException {
        ArtifactResult capsule = artifactResolver.resolveArtifact(mavenProject, CAPSULE_GROUP_ID, CAPSULE_ARTIFACT_ID, null, null, capsuleVersion);
        JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(capsule.getArtifact().getFile()));

        JarEntry entry;
        while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) {
            if (entry.getName().startsWith("capsule") || entry.getName().equals(CAPSULE_CLASS)) {
                addToJar(entry.getName(), new ByteArrayInputStream(IOUtil.toByteArray(capsuleJarInputStream)), jarOutputStream);
            }
        }
    }

    private void addMavenCapletClasses(JarOutputStream jarOutputStream) throws IOException, ArtifactResolutionException {
        ArtifactResult capsule = artifactResolver.resolveArtifact(mavenProject, CAPSULE_GROUP_ID, MAVEN_CAPLET_ARTIFACT_ID, null, null, capsuleVersion);
        JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(capsule.getArtifact().getFile()));

        JarEntry entry;
        while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) {
            if (entry.getName().startsWith("capsule") || entry.getName().equals(MAVEN_CAPLET_CLASS)) {
                addToJar(entry.getName(), new ByteArrayInputStream(IOUtil.toByteArray(capsuleJarInputStream)), jarOutputStream);
            }
        }
    }

    private JarOutputStream addManifest(JarOutputStream jar, Map<String, String> additionalAttributes, Type type) throws IOException {
        String capsuleMainClass = type == Type.standalone ? "Capsule" : "MavenCapsule";
        Manifest manifestBuild = new Manifest();
        Attributes mainAttributes = manifestBuild.getMainAttributes();

        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttributes.put(Attributes.Name.MAIN_CLASS, capsuleMainClass);
        mainAttributes.put(new Attributes.Name(PREMAIN_CLASS), capsuleMainClass);
        mainAttributes.put(new Attributes.Name(APPLICATION_CLASS), mainClass);
        mainAttributes.put(new Attributes.Name(APPLICATION_NAME), this.getOutputName());

        if (allowSnapshots != null) {
            getLog().warn("Allowing SNAPSHOT dependencies in the Capsule");
            mainAttributes.put(new Attributes.Name(ALLOW_SNAPSHOTS), "true");
        }

//        TODO String propertiesString = getSystemPropertiesString();
//        if (propertiesString != null) mainAttributes.put(new Attributes.Name("System-Properties"), propertiesString);

        // additional attributes
        if (additionalAttributes != null) {
            for (Map.Entry<String, String> entry : additionalAttributes.entrySet()) {
                mainAttributes.put(new Attributes.Name(entry.getKey()), entry.getValue());
            }
        }

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        manifestBuild.write(dataStream);

        return addToJar(JarFile.MANIFEST_NAME, new ByteArrayInputStream(dataStream.toByteArray()), jar);
    }

    private JarOutputStream addToJar(String name, InputStream input, JarOutputStream jar) throws IOException {
        try {
            jar.putNextEntry(new ZipEntry(name));
            IOUtil.copy(input, jar);
            jar.closeEntry();
        } catch (ZipException e) {
            // ignore
        }

        IOUtil.close(input);

        return jar;
    }

    private String getRepoString() {
        StringBuilder repoList = new StringBuilder();

        for (RemoteRepository repository : remoteRepositories) {
            repoList.append(repository.getId()).append("(").append(repository.getUrl()).append(") ");
        }

        return repoList.toString();
    }

    private String getDependencyString() throws DependencyResolutionException, ArtifactResolutionException {
        StringBuilder dependenciesList = new StringBuilder();
        DependencyFilter dependencyFilter = new DependencyFilter() {
            @Override
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                return (parents.size() == 1 && !"pom".equals(node.getArtifact().getExtension())) ||
                        (parents.size() > 1 && "pom".equals(parents.get(0).getArtifact().getExtension()));
            }
        };

        for (org.eclipse.aether.artifact.Artifact artifact : getProjectArtifacts(dependencyFilter)) {
            dependenciesList
                    .append(artifact.getGroupId()).append(":")
                    .append(artifact.getArtifactId()).append(":")
                    .append(artifact.getVersion())
                    .append(Strings.isNullOrEmpty(artifact.getClassifier()) ? "" : ":" + artifact.getClassifier())
                    .append(" ");
        }

        return dependenciesList.toString();
    }

    private Set<Artifact> getProjectArtifacts(DependencyFilter dependencyFilter) throws DependencyResolutionException {
        List<org.eclipse.aether.graph.Dependency> managedDependencies = new ArrayList<org.eclipse.aether.graph.Dependency>();

        for (Dependency dependency : mavenProject.getDependencyManagement().getDependencies()) {
            managedDependencies.add(artifactResolver.convertDependencyToAether(dependency));
        }

        List<ArtifactResult> artifactResults = artifactResolver.resolveTransitiveArtifacts(
                mavenProject,
                artifactResolver.convertArtifactToAether(mavenProject.getArtifact()),
                managedDependencies,
                dependencyFilter
        );

        Set<Artifact> artifacts = new HashSet<Artifact>();
        for (ArtifactResult artifactResult : artifactResults) {
            artifacts.add(artifactResult.getArtifact());
        }

        return artifacts;
    }
}
