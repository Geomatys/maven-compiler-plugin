/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.compiler;

import java.util.Objects;

import org.apache.maven.api.Session;
import org.apache.maven.api.services.DependencyCoordinateFactory;
import org.apache.maven.api.services.DependencyCoordinateFactoryRequest;

/**
 * Simple representation of Maven-coordinates of a dependency.
 *
 * @author Andreas Gudian
 * @since 3.4
 *
 * @deprecated Used for {@link AbstractCompilerMojo#annotationProcessorPaths}, which is deprecated.
 */
@Deprecated(since = "4.0.0")
public class DependencyCoordinate {
    private String groupId;

    private String artifactId;

    private String version;

    private String classifier;

    private String type = "jar";

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DependencyCoordinate other = (DependencyCoordinate) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version)
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + (version != null ? ":" + version : "")
                + (classifier != null ? ":" + classifier : "") + (type != null ? "." + type : "");
    }

    /**
     * Converts this coordinate to the Maven API.
     *
     * @param session the current build session instance
     * @return this coordinate as Maven API
     */
    final org.apache.maven.api.DependencyCoordinate toCoordinate(Session session) {
        return session.getService(DependencyCoordinateFactory.class)
                .create(DependencyCoordinateFactoryRequest.builder()
                        .session(session)
                        .groupId(groupId)
                        .artifactId(artifactId)
                        .version(version)
                        .classifier(classifier)
                        .type(type)
                        .build());
    }
}
