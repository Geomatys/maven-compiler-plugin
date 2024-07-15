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

import javax.tools.OptionChecker;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.JavaPathType;
import org.apache.maven.api.PathType;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.MessageBuilder;

/**
 * Compiles application test sources.
 * Each instance shall be used only once, then discarded.
 *
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @author Martin Desruisseaux
 * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html">javac Command</a>
 * @since 2.0
 */
@Mojo(name = "testCompile", defaultPhase = "test-compile")
public class TestCompilerMojo extends AbstractCompilerMojo {
    /**
     * Whether to bypass compilation of test sources.
     * Its use is not recommended, but quite convenient on occasion.
     *
     * @see CompilerMojo#skipMain
     */
    @Parameter(property = "maven.test.skip")
    protected boolean skip;

    /**
     * The source directories containing the test-source to be compiled.
     *
     * @see CompilerMojo#compileSourceRoots
     */
    @Parameter
    protected List<String> compileSourceRoots;

    /**
     * Specify where to place generated source files created by annotation processing.
     * Only applies to JDK 1.6+
     *
     * @see CompilerMojo#generatedSourcesDirectory
     * @since 2.2
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
    protected Path generatedTestSourcesDirectory;

    /**
     * A set of inclusion filters for the compiler.
     *
     * @see CompilerMojo#includes
     */
    @Parameter
    protected Set<String> testIncludes;

    /**
     * A set of exclusion filters for the compiler.
     *
     * @see CompilerMojo#excludes
     */
    @Parameter
    protected Set<String> testExcludes;

    /**
     * A set of exclusion filters for the incremental calculation.
     * Updated files, if excluded by this filter, will not cause the project to be rebuilt.
     *
     * @see CompilerMojo#incrementalExcludes
     * @since 3.11
     */
    @Parameter
    protected Set<String> testIncrementalExcludes;

    /**
     * The {@code --source} argument for the test Java compiler.
     *
     * @see CompilerMojo#source
     * @since 2.1
     */
    @Parameter(property = "maven.compiler.testSource")
    protected String testSource;

    /**
     * The {@code --target} argument for the test Java compiler.
     *
     * @see CompilerMojo#target
     * @since 2.1
     */
    @Parameter(property = "maven.compiler.testTarget")
    protected String testTarget;

    /**
     * the {@code --release} argument for the test Java compiler
     *
     * @see CompilerMojo#release
     * @since 3.6
     */
    @Parameter(property = "maven.compiler.testRelease")
    protected String testRelease;

    /**
     * The arguments to be passed to the test compiler.
     * If this parameter is specified, it replaces {@link #compilerArgs}.
     * Otherwise, the {@code compilerArgs} parameter is used.
     *
     * @see CompilerMojo#compilerArgs
     * @since 4.0.0
     */
    @Parameter
    protected List<String> testCompilerArgs;

    /**
     * The arguments to be passed to test compiler.
     *
     * @deprecated Replaced by {@link #testCompilerArgs} for consistency with the main goal.
     *
     * @since 2.1
     */
    @Parameter
    @Deprecated(since = "4.0.0")
    protected Map<String, String> testCompilerArguments;

    /**
     * The single argument string to be passed to the test compiler.
     * If this parameter is specified, it replaces {@link #compilerArgument}.
     * Otherwise, the {@code compilerArgument} parameter is used.
     *
     * @deprecated Use {@link #testCompilerArguments} instead.
     *
     * @see CompilerMojo#compilerArgument
     * @since 2.1
     */
    @Parameter
    @Deprecated(since = "4.0.0")
    protected String testCompilerArgument;

    /**
     * The directory where compiled test classes go.
     * This parameter should only be modified in special cases.
     * See the {@link CompilerMojo#outputDirectory} for more information.
     *
     * @see CompilerMojo#outputDirectory
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true)
    protected Path outputDirectory;

    /**
     * The output directory of the main classes.
     * This directory will be added to the class-path or module-path.
     * Its value should be the same as {@link CompilerMojo#outputDirectory}.
     *
     * @see CompilerMojo#outputDirectory
     * @see #addImplicitDependencies(Map, boolean)
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    protected Path mainOutputDirectory;

    /**
     * Whether to place the main classes on the module path when {@code module-info} is present.
     * When {@code false}, always places the main classes on the class path.
     * Dependencies are also placed on the class-path, unless their type is {@code module-jar}.
     *
     * @since 3.11
     */
    @Parameter(defaultValue = "true")
    protected boolean useModulePath = true;

    /**
     * Name of the main module to compile, or {@code null} if not yet determined.
     * If the project is not modular, an empty string.
     *
     * TODO: use "*" as a sentinel value for modular source hierarchy.
     *
     * @see #getModuleName()
     */
    private String moduleName;

    /**
     * Whether a {@code module-info.java} file is defined in the test sources.
     * In such case, it has precedence over the {@code module-info.java} in main sources.
     *
     * @deprecated This is defined for compatibility with Maven 3, but not recommended.
     * It works if {@code module-info.java} is only in the test directory. But having
     * that file in both source and target directories is no longer supported.
     */
    @Deprecated(since = "4.0.0")
    private boolean hasTestModuelInfo;

    /**
     * The file where to dump the command-line when debug is activated or when the compilation failed.
     * For example, if the value is {@code "javac-test"}, then the Java compiler can be launched
     * from the command-line by typing {@code javac @target/javac-test.args}.
     * The debug file will contain the compiler options together with the list of source files to compile.
     *
     * @see CompilerMojo#debugFileName
     * @since 3.10.0
     */
    @Parameter(defaultValue = "javac-test.args")
    protected String debugFileName;

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
        super.execute();
    }

    /**
     * Parses the parameters declared in the MOJO.
     *
     * @param  compiler  the tools to use for verifying the validity of options
     * @return the options after validation
     */
    @Override
    @SuppressWarnings("deprecation")
    protected Options acceptParameters(final OptionChecker compiler) {
        Options compilerConfiguration = super.acceptParameters(compiler);
        reportDeprecatedParameter("testCompilerArgument", testCompilerArgument, "testCompilerArgs", null);
        reportDeprecatedParameter("testCompilerArguments", testCompilerArguments, "testCompilerArgs", null);
        compilerConfiguration.addUnchecked(
                testCompilerArgs == null || testCompilerArgs.isEmpty() ? compilerArgs : testCompilerArgs);
        if (testCompilerArguments != null) {
            for (Map.Entry<String, String> entry : testCompilerArguments.entrySet()) {
                compilerConfiguration.addUnchecked(List.of(entry.getKey(), entry.getValue()));
            }
        }
        compilerConfiguration.addUnchecked(testCompilerArgument == null ? compilerArgument : testCompilerArgument);
        return compilerConfiguration;
    }

    /**
     * Adds {@code --patch-module} options for the given source directories.
     * In this case, the option values are directory of <em>source</em> files.
     * Not to be confused with cases where is module is patched with compiled
     * classes (it may happen in other parts of the compiler plugin).
     *
     * @param addTo the collection of source paths to augment
     * @param compileSourceRoots the source paths to eventually adds to the {@code toAdd} map
     * @throws IOException if this method needs to read a module descriptor and this operation failed
     */
    @Override
    void addSourceDirectories(Map<PathType, List<Path>> addTo, List<SourceDirectory> compileSourceRoots)
            throws IOException {
        for (SourceDirectory dir : compileSourceRoots) {
            String name = dir.moduleName;
            if (name == null) {
                name = getModuleName();
            }
            if (!name.isEmpty()) {
                addTo.computeIfAbsent(JavaPathType.patchModule(name), (key) -> new ArrayList<>())
                        .add(dir.root);
            }
        }
    }

    /**
     * {@return whether the project has at least one {@code module-info.class} file}.
     * This method opportunistically fetches the module name.
     *
     * @param sourceFiles the test sources to compile
     * @throws IOException if this method needed to read a module descriptor and failed
     */
    @Override
    boolean hasModuleDeclaration(final List<SourceFile> sourceFiles) throws IOException {
        if (useModulePath) {
            hasTestModuelInfo = super.hasModuleDeclaration(sourceFiles);
            if (hasTestModuelInfo) {
                MessageBuilder message = messageBuilderFactory.builder();
                message.a("The test directory should not contain a ")
                        .warning(MODULE_INFO + JAVA_FILE_SUFFIX)
                        .a(" file. This is only partially supported in this compiler plugin "
                                + "and may be removed in a future release. Use ")
                        .info("--add-reads")
                        .a(", ")
                        .info("--add-modules")
                        .a(" and related options instead.");
                logger.warn(message.toString());
                return true;
            }
            return !getModuleName().isEmpty();
        }
        return false;
    }

    /**
     * {@return the module name of the main code, or an empty string if none}.
     * This method reads the module descriptor when first needed and caches the result.
     *
     * @throws IOException if the module descriptor cannot be read.
     */
    private String getModuleName() throws IOException {
        if (moduleName == null) {
            Path file = mainOutputDirectory.resolve(MODULE_INFO + CLASS_FILE_SUFFIX);
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    moduleName = ModuleDescriptor.read(in).name();
                }
            } else {
                moduleName = "";
            }
        }
        return moduleName;
    }

    /**
     * Adds the main compilation output directories as test dependencies.
     *
     * @param addTo where to add dependencies
     * @param hasModuleDeclaration whether the main sources have or should have a {@code module-info} file
     */
    @Override
    protected void addImplicitDependencies(Map<PathType, List<Path>> addTo, boolean hasModuleDeclaration) {
        var pathType = hasModuleDeclaration ? JavaPathType.MODULES : JavaPathType.CLASSES;
        if (Files.exists(mainOutputDirectory)) {
            addTo.computeIfAbsent(pathType, (key) -> new ArrayList<>()).add(mainOutputDirectory);
        }
    }

    /**
     * Generates the {@code --add-modules} and {@code --add-reads} options for the dependencies that are not
     * in the main compilation. This method is invoked only if {@code hasModuleDeclaration} is {@code true}.
     *
     * @param dependencies the project dependencies
     * @param addTo where to add the options
     * @throws IOException if the module information of a dependency cannot be read
     */
    @Override
    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    protected void addModuleOptions(DependencyResolverResult dependencies, Options addTo) throws IOException {
        if (hasTestModuelInfo) {
            /*
             * Do not add any `--add-reads` parameters. The developers should put
             * everything needed in the `module-info`, including test dependencies.
             */
            return;
        }
        StringJoiner addReads = null;
        StringJoiner addModules = null;
        boolean hasUnnamed = false;
        for (Map.Entry<Dependency, Path> entry : dependencies.getDependencies().entrySet()) {
            switch (entry.getKey().getScope()) {
                case TEST:
                case TEST_ONLY:
                    // TODO: we could exclude transitive dependencies.
                    if (addReads == null) {
                        addReads = new StringJoiner(",", getModuleName() + "=", "");
                    }
                    String depName =
                            dependencies.getModuleName(entry.getValue()).orElse(null);
                    if (depName != null) {
                        if (addModules == null) {
                            addModules = new StringJoiner(",");
                        }
                        addModules.add(depName);
                        addReads.add(depName);
                    } else {
                        hasUnnamed = true;
                    }
                    break;
            }
        }
        if (addModules != null) {
            addTo.addIfNonBlank("--add-modules", addModules.toString());
        }
        if (hasUnnamed) {
            addReads.add("ALL-UNNAMED");
        }
        if (addReads != null) {
            addTo.addIfNonBlank("--add-reads", addReads.toString());
        }
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
            return compileSourceRoots.stream().map(Paths::get).toList();
        }
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
     * {@return the destination directory for test class files}.
     */
    @Nonnull
    @Override
    protected Path getOutputDirectory() {
        return outputDirectory;
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
