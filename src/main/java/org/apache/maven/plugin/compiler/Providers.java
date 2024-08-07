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

import org.apache.maven.api.Session;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.MessageBuilderFactory;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;

/**
 * For providing instances to fields annotated with {@code @Inject} if the MOJO.
 */
@Named
class Providers {

    @Provides
    static ToolchainManager toolchainManager(Session session) {
        return session.getService(ToolchainManager.class);
    }

    @Provides
    static ArtifactManager artifactManager(Session session) {
        return session.getService(ArtifactManager.class);
    }

    @Provides
    static ProjectManager projectManager(Session session) {
        return session.getService(ProjectManager.class);
    }

    @Provides
    static MessageBuilderFactory messageBuilderFactory(Session session) {
        return session.getService(MessageBuilderFactory.class);
    }
}
