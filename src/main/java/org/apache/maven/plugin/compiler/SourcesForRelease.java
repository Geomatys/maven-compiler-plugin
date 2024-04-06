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

import javax.lang.model.SourceVersion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source files for a specific Java release. Instances of {@code SourcesForRelease} are created from
 * a list of {@link SourceFile} after the sources have been filtered according include and exclude filters.
 *
 * @author Martin Desruisseaux
 */
final class SourcesForRelease {
    /**
     * The release for this set of sources. For this class, the
     * {@link SourceVersion#RELEASE_0} value means "no version".
     */
    final SourceVersion release;

    /**
     * All source files.
     */
    final List<Path> files;

    /**
     * The root directories for each module. Keys are module names.
     * The empty string stands for no module.
     */
    final Map<String, Set<Path>> roots;

    /**
     * Last directory added to the {@link #roots} map. This is a small optimization for reducing
     * the number of accesses to the map. In most cases, only one element will be written there.
     */
    private SourceDirectory lastDirectoryAdded;

    /**
     * Creates an initially empty instance for the given Java release.
     *
     * @param release the release for this set of sources, or {@link SourceVersion#RELEASE_0} for no version.
     */
    private SourcesForRelease(SourceVersion release) {
        this.release = release;
        roots = new LinkedHashMap<>();
        files = new ArrayList<>(256);
    }

    /**
     * Adds the given source file to this collection of source files.
     * The value of {@code source.directory.release} must be {@link #release}.
     *
     * @param source the source file to add.
     */
    private void add(SourceFile source) {
        var directory = source.directory;
        if (lastDirectoryAdded != directory) {
            lastDirectoryAdded = directory;
            String moduleName = directory.moduleName;
            if (moduleName == null) {
                moduleName = "";
            }
            roots.computeIfAbsent(moduleName, (key) -> new LinkedHashSet<>()).add(directory.root);
        }
        files.add(source.file);
    }

    /**
     * Groups all sources files first by Java release versions, then by module names.
     * The elements in the returned collection are sorted in the order of {@link SourceVersion}
     * enumeration values. It should match the increasing order of Java releases.
     *
     * @param sources the sources to group.
     * @return the given sources grouped by Java release versions and module names.
     */
    public static Collection<SourcesForRelease> groupByReleaseAndModule(List<SourceFile> sources) {
        EnumMap<SourceVersion, SourcesForRelease> result = new EnumMap<>(SourceVersion.class);
        for (SourceFile source : sources) {
            SourceVersion release = source.directory.release;
            if (release == null) {
                release = SourceVersion.RELEASE_0;
            }
            result.computeIfAbsent(release, SourcesForRelease::new).add(source);
        }
        // TODO: add empty set for all modules present in a release but not in the next release.
        return result.values();
    }
}
