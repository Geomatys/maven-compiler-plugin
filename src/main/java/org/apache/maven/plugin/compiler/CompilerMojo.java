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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.apache.maven.api.*;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * Compiles application sources
 *
 * @author Jason van Zyl
 * @author Martin Desruisseaux
 * @since 2.0
 */
@Mojo(name = "compile", defaultPhase = "COMPILE")
public class CompilerMojo extends AbstractCompilerMojo {
    /**
     * The source directories containing the sources to be compiled.
     * If {@code null} or empty, the directory will be obtained from the project manager.
     */
    @Parameter
    protected List<String> compileSourceRoots;

    /**
     * Projects main artifact.
     */
    @Parameter(defaultValue = "${project.mainArtifact}", readonly = true, required = true)
    protected Artifact projectArtifact;

    /**
     * The directory for compiled classes.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    protected Path outputDirectory;

    /**
     * A set of inclusion filters for the compiler.
     */
    @Parameter
    protected Set<String> includes;

    /**
     * A set of exclusion filters for the compiler.
     */
    @Parameter
    protected Set<String> excludes;

    /**
     * A set of exclusion filters for the incremental calculation.
     * Updated source files, if excluded by this filter, will not cause the project to be rebuilt.
     *
     * <h4>Limitation</h4>
     * In the current implementation, those exclusion filters are applied for added or removed files,
     * but not yet for removed files.
     *
     * @since 3.11
     */
    @Parameter
    protected Set<String> incrementalExcludes;

    /**
     * Specify where to place generated source files created by annotation processing. Only applies to JDK 1.6+
     *
     * @since 2.2
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
    protected Path generatedSourcesDirectory;

    /**
     * Set this to {@code true} to bypass compilation of main sources.
     * Its use is not recommended, but quite convenient on occasion.
     */
    @Parameter(property = "maven.main.skip")
    protected boolean skipMain;

    /**
     * When set to {@code true}, the classes will be placed in {@code META-INF/versions/${release}}.
     * <p>
     * <strong>Note:</strong> A jar is only a multirelease jar if {@code META-INF/MANIFEST.MF} contains
     * {@code Multi-Release: true}. You need to set this by configuring the <a href=
     * "https://maven.apache.org/plugins/maven-jar-plugin/examples/manifest-customization.html">maven-jar-plugin</a>.
     * This implies that you cannot test a multirelease jar using the {@link #outputDirectory}.
     * </p>
     *
     * @since 3.7.1
     *
     * @deprecated This parameter is ignored in Maven 4.
     * Multi-release are supported by specifying the release version together with the source directory.
     */
    @Parameter
    @Deprecated(since = "4.0.0", forRemoval = true)
    protected boolean multiReleaseOutput;

    /**
     * The file where to dump the command-line when debug is activated or when the compilation failed.
     * For example, if the value is {@code "javac.txt"}, then the Java compiler can be launched from
     * the command-line by typing {@code javac @target/javac.txt}.
     * The debug file will contain the compiler options together with the list of source files to compile.
     *
     * @since 3.10.0
     */
    @Parameter(defaultValue = "javac.txt")
    protected String debugFileName;

    /**
     * Creates a new compiler MOJO.
     */
    public CompilerMojo() {
        super(false);
    }

    /**
     * Runs the Java compiler on the main source code.
     *
     * @throws MojoException if the compiler cannot be run.
     */
    @Override
    public void execute() throws MojoException {
        if (skipMain) {
            logger.info("Not compiling main sources");
            return;
        }
        reportDeprecatedParameter("multiReleaseOutput", compilerArgument, null, null);
        super.execute();
        if (Files.isDirectory(outputDirectory) && projectArtifact != null) {
            artifactManager.setPath(projectArtifact, outputDirectory);
        }
    }

    /**
     * {@return the root directories of Java source files to compile}.
     * It can be a parameter specified to the compiler plugin,
     * or otherwise the value provided by the project manager.
     */
    @Nonnull
    @Override
    protected List<Path> getCompileSourceRoots() {
        List<Path> sources;
        if (compileSourceRoots == null || compileSourceRoots.isEmpty()) {
            sources = projectManager.getCompileSourceRoots(project, ProjectScope.MAIN);
        } else {
            sources = compileSourceRoots.stream().map(Paths::get).toList();
        }
        return sources;
    }

    /**
     * {@return the destination directory for main class files}.
     */
    @Nonnull
    @Override
    protected Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * {@return the inclusion filters for the compiler, or an empty set for all Java source files}.
     */
    @Override
    protected Set<String> getIncludes() {
        return (includes != null) ? includes : Set.of();
    }

    /**
     * {@return the exclusion filters for the compiler, or an empty set if none}.
     */
    @Override
    protected Set<String> getExcludes() {
        return (excludes != null) ? excludes : Set.of();
    }

    /**
     * {@return the exclusion filters for the incremental calculation, or an empty set if none}.
     */
    @Override
    protected Set<String> getIncrementalExcludes() {
        return (incrementalExcludes != null) ? incrementalExcludes : Set.of();
    }

    /**
     * {@return the path where to place generated source files created by annotation processing on the main classes}.
     */
    @Nullable
    @Override
    protected Path getGeneratedSourcesDirectory() {
        return generatedSourcesDirectory;
    }

    /**
     * {@return the file where to dump the command-line when debug is activated or when the compilation failed}.
     */
    @Nullable
    @Override
    protected String getDebugFileName() {
        return debugFileName;
    }
}
