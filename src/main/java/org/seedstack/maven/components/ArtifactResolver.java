/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components;

import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component(role = ArtifactResolver.class)
public class ArtifactResolver {
    @Requirement
    private RepositorySystem repositorySystem;
    @Requirement
    private ProjectDependenciesResolver projectDependenciesResolver;

    public String getHighestVersion(MavenProject mavenProject, String groupId, String artifactId, boolean allowSnapshots) {
        RepositorySystemSession session = mavenProject.getProjectBuildingRequest().getRepositorySession();

        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(new DefaultArtifact(groupId, artifactId, null, "[0,)"));
        rangeRequest.setRepositories(mavenProject.getRemoteProjectRepositories());

        VersionRangeResult rangeResult;
        try {
            rangeResult = repositorySystem.resolveVersionRange(session, rangeRequest);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to resolve version for %s:%s", groupId, artifactId), e);
        }

        Version highestVersion = null;
        for (Version version : rangeResult.getVersions()) {
            if (highestVersion == null) {
                highestVersion = version;
            } else if ((allowSnapshots || !version.toString().endsWith("-SNAPSHOT")) && version.compareTo(highestVersion) > 0) {
                highestVersion = version;
            }
        }

        if (highestVersion == null) {
            throw new RuntimeException(String.format("No version found for artifact %s:%s", groupId, artifactId));
        }

        return highestVersion.toString();
    }

    public ArtifactResult resolveArtifact(MavenProject mavenProject, String groupId, String artifactId, String type, String classifier, String version) throws ArtifactResolutionException {
        RepositorySystemSession session = mavenProject.getProjectBuildingRequest().getRepositorySession();
        String coordinates = groupId + ":" + artifactId;

        if (type != null && !type.isEmpty()) {
            coordinates += ":" + type;
        }

        if (classifier != null && !classifier.isEmpty()) {
            coordinates += ":" + classifier;
        }

        if (version != null && !version.isEmpty()) {
            coordinates += ":" + version;
        }

        return repositorySystem.resolveArtifact(session, new ArtifactRequest(new DefaultArtifact(coordinates), mavenProject.getRemoteProjectRepositories(), null));
    }

    public Set<Artifact> resolveProjectArtifacts(MavenProject mavenProject, DependencyFilter dependencyFilter) {
        DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(mavenProject, mavenProject.getProjectBuildingRequest().getRepositorySession());
        request.setResolutionFilter(dependencyFilter);

        DependencyResolutionResult result;
        try {
            result = projectDependenciesResolver.resolve(request);
        } catch (org.apache.maven.project.DependencyResolutionException e) {
            throw new RuntimeException("Unable to resolve project artifacts");
        }

        Set<Artifact> artifacts = new HashSet<Artifact>();
        for (org.eclipse.aether.graph.Dependency dependency : result.getDependencies()) {
            artifacts.add(dependency.getArtifact());
        }

        return artifacts;
    }

    public org.eclipse.aether.artifact.Artifact convertArtifactToAether(org.apache.maven.artifact.Artifact artifact) {
        return new DefaultArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getClassifier(),
                artifact.getType(),
                artifact.getVersion());
    }

    public org.eclipse.aether.graph.Dependency convertDependencyToAether(org.apache.maven.model.Dependency dependency) {
        List<org.eclipse.aether.graph.Exclusion> exclusions = new ArrayList<Exclusion>();

        for (org.apache.maven.model.Exclusion exclusion : dependency.getExclusions()) {
            exclusions.add(new org.eclipse.aether.graph.Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), null, null));
        }

        return new org.eclipse.aether.graph.Dependency(new DefaultArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getClassifier(),
                dependency.getType(),
                dependency.getVersion()),
                dependency.getScope(),
                Boolean.parseBoolean(dependency.getOptional()), exclusions);
    }
}
