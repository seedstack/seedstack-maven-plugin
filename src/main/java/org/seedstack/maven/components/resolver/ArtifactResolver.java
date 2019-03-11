/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.resolver;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.Set;

public interface ArtifactResolver {
    String getHighestVersion(MavenProject mavenProject, String groupId, String artifactId, boolean allowSnapshots);

    ArtifactResult resolveArtifact(MavenProject mavenProject, String groupId, String artifactId, String type, String classifier, String version) throws ArtifactResolutionException;

    Set<Artifact> resolveProjectArtifacts(MavenProject mavenProject, DependencyFilter dependencyFilter);
}
