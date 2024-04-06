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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * Compiles application test sources.
 *
 * @author Jason van Zyl
 * @author Martin Desruisseaux
 * @since 2.0
 */
@Mojo(name = "testCompile", defaultPhase = "COMPILE")
public class TestCompilerMojo extends AbstractCompilerMojo {
    /**
     * Whether to bypass compilation of test sources.
     * Its use is not recommended, but quite convenient on occasion.
     */
    @Parameter(property = "maven.test.skip")
    protected boolean skip;

    /**
     * The source directories containing the test-source to be compiled.
     */
    @Parameter
    protected List<String> compileSourceRoots;

    /**
     * The directory where compiled test classes go.
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
    protected Path outputDirectory;

    /**
     * A set of inclusion filters for the compiler.
     */
    @Parameter
    protected Set<String> testIncludes;

    /**
     * A set of exclusion filters for the compiler.
     */
    @Parameter
    protected Set<String> testExcludes;

    /**
     * A set of exclusion filters for the incremental calculation.
     * Updated files, if excluded by this filter, will not cause the project to be rebuilt.
     *
     * @since 3.11
     */
    @Parameter
    protected Set<String> testIncrementalExcludes;

    /**
     * The {@code --source} argument for the test Java compiler.
     *
     * @since 2.1
     */
    @Parameter(property = "maven.compiler.testSource")
    protected String testSource;

    /**
     * The {@code --target} argument for the test Java compiler.
     *
     * @since 2.1
     */
    @Parameter(property = "maven.compiler.testTarget")
    protected String testTarget;

    /**
     * the {@code --release} argument for the test Java compiler
     *
     * @since 3.6
     */
    @Parameter(property = "maven.compiler.testRelease")
    protected String testRelease;

    /**
     * The arguments to be passed to the test compiler.
     * If this parameter is specified, it replaces {@link #compilerArgs}.
     * Otherwise, the {@code compilerArgs} parameter is used.
     *
     * @since 4.0.0
     */
    @Parameter
    private List<String> testCompilerArgs;

    /**
     * The arguments to be passed to test compiler.
     *
     * @deprecated Replaced by {@link #testCompilerArgs} for consistency with the main goal.
     *
     * @since 2.1
     */
    @Parameter
    @Deprecated(since = "4.0.0")
    private Map<String, String> testCompilerArguments;

    /**
     * The single argument string to be passed to the test compiler.
     * If this parameter is specified, it replaces {@link #compilerArgument}.
     * Otherwise, the {@code compilerArgument} parameter is used.
     *
     * @since 2.1
     *
     * @deprecated Use {@link #testCompilerArguments} instead.
     */
    @Parameter
    @Deprecated(since = "4.0.0")
    private String testCompilerArgument;

    /**
     * Specify where to place generated source files created by annotation processing.
     * Only applies to JDK 1.6+
     *
     * @since 2.2
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
    private Path generatedTestSourcesDirectory;

    /**
     * The file where to dump the command-line when debug is activated or when the compilation failed.
     * For example, if the value is {@code "javac-test.txt"}, then the Java compiler can be launched
     * from the command-line by typing {@code javac @target/javac-test.txt}.
     * The debug file will contain the compiler options together with the list of source files to compile.
     *
     * @since 3.10.0
     */
    @Parameter(defaultValue = "javac-test.txt")
    private String debugFileName;

    /**
     * Creates a new test compiler MOJO.
     */
    public TestCompilerMojo() {
        super(true);
    }

    /**
     * Runs the Java compiler on the test source code.
     *
     * @throws MojoException if the compiler cannot be run.
     */
    @Override
    public void execute() throws MojoException {
        if (skip) {
            logger.info("Not compiling test sources");
            return;
        }
        reportDeprecatedParameter("testCompilerArgument", testCompilerArgument, "testCompilerArgs", null);
        reportDeprecatedParameter("testCompilerArguments", testCompilerArguments, "testCompilerArgs", null);
        super.execute();
    }

    /**
     * {@return the root directories of Java source files to compile for the tests}.
     */
    @Nonnull
    @Override
    protected List<Path> getCompileSourceRoots() {
        if (compileSourceRoots == null || compileSourceRoots.isEmpty()) {
            return projectManager.getCompileSourceRoots(project, ProjectScope.TEST);
        } else {
            return compileSourceRoots.stream().map(Paths::get).collect(Collectors.toList());
        }
    }

    /**
     * {@return the inclusion filters for the compiler, or an empty set for all Java source files}.
     */
    @Override
    protected Set<String> getIncludes() {
        return (testIncludes != null) ? testIncludes : Set.of();
    }

    /**
     * {@return the exclusion filters for the compiler, or an empty set if none}.
     */
    @Override
    protected Set<String> getExcludes() {
        return (testExcludes != null) ? testExcludes : Set.of();
    }

    /**
     * {@return the exclusion filters for the incremental calculation, or an empty set if none}.
     */
    @Override
    protected Set<String> getIncrementalExcludes() {
        return (testIncrementalExcludes != null) ? testIncrementalExcludes : Set.of();
    }

    /**
     * {@return the destination directory for test class files}.
     */
    @Nonnull
    @Override
    protected Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * If a different source version has been specified for the tests, returns that version.
     * Otherwise returns the same source version as the main code.
     *
     * @return the {@code --source} argument for the Java compiler
     */
    @Nullable
    @Override
    protected String getSource() {
        return testSource == null ? source : testSource;
    }

    /**
     * If a different target version has been specified for the tests, returns that version.
     * Otherwise returns the same target version as the main code.
     *
     * @return the {@code --target} argument for the Java compiler
     */
    @Nullable
    @Override
    protected String getTarget() {
        return testTarget == null ? target : testTarget;
    }

    /**
     * If a different release version has been specified for the tests, returns that version.
     * Otherwise returns the same release version as the main code.
     *
     * @return the {@code --release} argument for the Java compiler
     */
    @Nullable
    @Override
    protected String getRelease() {
        return testRelease == null ? release : testRelease;
    }

    /**
     * Adds compiler arguments to the given set of options.
     *
     * @param addTo where to add the compiler arguments
     */
    @Override
    void addCompilerArguments(Options addTo) {
        addTo.addUnchecked(testCompilerArgs == null || testCompilerArgs.isEmpty() ? compilerArgs : testCompilerArgs);
        for (Map.Entry<String, String> entry : testCompilerArguments.entrySet()) {
            addTo.addUnchecked(List.of(entry.getKey(), entry.getValue()));
        }
        addTo.addUnchecked(testCompilerArgument == null ? compilerArgument : testCompilerArgument);
    }

    /**
     * {@return the path where to place generated source files created by annotation processing on the test classes}.
     */
    @Nullable
    @Override
    protected Path getGeneratedSourcesDirectory() {
        return generatedTestSourcesDirectory;
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
