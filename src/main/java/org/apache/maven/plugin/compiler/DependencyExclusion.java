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

/**
 * Simple representation of a Maven dependency exclusion.
 *
 * @deprecated Used for {@link AbstractCompilerMojo#annotationProcessorPaths}, which is deprecated.
 */
@Deprecated(since = "4.0.0")
public final class DependencyExclusion {
    String groupId;

    String artifactId;

    private String classifier;

    private String extension = "jar";

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof DependencyExclusion other
                && Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(extension, other.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, classifier, extension);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + (classifier != null ? ":" + classifier : "")
                + (extension != null ? "." + extension : "");
    }
}
