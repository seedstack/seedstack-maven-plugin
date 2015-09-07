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
import com.google.common.io.Files;
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
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class PackageMojo extends AbstractMojo {
    public static final String CAPSULE_GROUP_ID = "co.paralleluniverse";
    public static final String CAPSULE_ARTIFACT_ID = "capsule";
    public static final String MAVEN_CAPLET_ARTIFACT_ID = "capsule-maven";
    public static final String CAPSULE_CLASS = "Capsule.class";
    public static final String MAVEN_CAPLET_CLASS = "MavenCapsule.class";

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

    @Parameter(defaultValue = "thin")
    private Type capsuleType;

    @Parameter
    private String capsuleVersion;

    @Component
    private BuildPluginManager buildPluginManager;

    @Component
    private ArtifactResolver artifactResolver;

    enum Type {
        empty,
        thin,
        fat
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
        switch (capsuleType) {
            case thin:
                try {
                    capsuleFile = buildThin();
                } catch (Exception e) {
                    throw new MojoExecutionException("Unable to build capsule", e);
                }

                break;
            default:
                throw new MojoFailureException("Unsupported capsule type " + capsuleType);
        }

        helper.attachArtifact(mavenProject, capsuleFile, String.format("capsule-%s", capsuleType));
    }

    public File buildThin() throws IOException, ArtifactResolutionException, DependencyResolutionException {
        File jarFile = new File(this.outputDirectory, getOutputName(Type.thin));
        JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));
        Map<String, String> additionalAttributes = new HashMap<String, String>();
        additionalAttributes.put("Dependencies", getDependencyString());
        additionalAttributes.put("Repositories", getRepoString());

        addManifest(jarStream, additionalAttributes, Type.thin);

        addCompiledProjectClasses(jarStream);

        addCapsuleClasses(jarStream);
        addMavenCapletClasses(jarStream);

        IOUtil.close(jarStream);

        return jarFile;
    }

    private String getOutputName(Type type) {
        return String.format("%s-%s.jar", finalName, type);
    }

    private void addCompiledProjectClasses(JarOutputStream jarStream) throws IOException {
        for (File f : Files.fileTreeTraverser().preOrderTraversal(classesDirectory)) {
            String path = f.getPath();

            if (!f.isDirectory() && !path.endsWith(".DS_Store") && !path.endsWith("MANIFEST.MF") && !f.equals(classesDirectory)) {
                addToJar(path.substring(path.indexOf("classes") + 8), new FileInputStream(f), jarStream);
            }
        }
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
        Manifest manifestBuild = new Manifest();
        Attributes mainAttributes = manifestBuild.getMainAttributes();

        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");

        switch (type) {
            case fat:
                mainAttributes.put(Attributes.Name.MAIN_CLASS, "Capsule");
            case thin:
                mainAttributes.put(Attributes.Name.MAIN_CLASS, "MavenCapsule");
            case empty:
                mainAttributes.put(Attributes.Name.MAIN_CLASS, "MavenCapsule");
        }

        mainAttributes.put(new Attributes.Name("Application-Class"), SeedStackConstants.mainClassName);

        mainAttributes.put(new Attributes.Name("Application-Name"), this.getOutputName(type));

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
        List<org.eclipse.aether.graph.Dependency> managedDependencies = new ArrayList<org.eclipse.aether.graph.Dependency>();

        for (Dependency dependency : mavenProject.getDependencyManagement().getDependencies()) {
            managedDependencies.add(artifactResolver.convertDependencyToAether(dependency));
        }

        List<ArtifactResult> artifactResults = artifactResolver.resolveTransitiveArtifacts(mavenProject, artifactResolver.convertArtifactToAether(mavenProject.getArtifact()), managedDependencies, new DependencyFilter() {
            @Override
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                return (parents.size() == 1 && !"pom".equals(node.getArtifact().getExtension())) ||
                        (parents.size() > 1 && "pom".equals(parents.get(0).getArtifact().getExtension()));
            }
        });

        Set<Artifact> artifacts = new HashSet<Artifact>();
        for (ArtifactResult artifactResult : artifactResults) {
            artifacts.add(artifactResult.getArtifact());
        }

        for (org.eclipse.aether.artifact.Artifact artifact : artifacts) {
            dependenciesList
                    .append(artifact.getGroupId()).append(":")
                    .append(artifact.getArtifactId()).append(":")
                    .append(artifact.getVersion())
                    .append(Strings.isNullOrEmpty(artifact.getClassifier()) ? "" : ":" + artifact.getClassifier())
                    .append(" ");
        }

        return dependenciesList.toString();

    }
}
