/**
 * Copyright (c) 2013-2015 by The SeedStack authors. All rights reserved.
 *
 * This file is part of SeedStack, An enterprise-oriented full development stack.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.inetpsa.seed.plugin.components;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * This class provide the logic for finding the highest version with Maven 3.1+.
 */
@Component(role = VersionResolver.class)
public class VersionResolver {
    @Requirement
    private RepositorySystem repositorySystem;

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
            throw new RuntimeException(String.format("No version found for archetype %s:%s", groupId, artifactId));
        }

        return highestVersion.toString();
    }
}
