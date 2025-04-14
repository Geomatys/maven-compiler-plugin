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

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.services.DependencyResolverResult;

/**
 * Reader of {@code module-info-patch.txt} files.
 * The options manages by this class are the options that are not defined by Maven dependencies.
 * They are the options for opening or exporting packages to other modules, or reading more modules.
 * The values of these options are module names or package names.
 * Options with path values are not managed by this class.
 *
 * <h2>Omitted option</h2>
 * The {@code --add-modules} option is omitted because this is a global option, not an option defined
 * on a per-module basis. Furthermore, in a Maven context, its value is controlled by the dependencies
 * declared in the {@code pom.xml} file.
 *
 * @author Martin Desruisseaux
 */
final class ModuleInfoPatch {
    /**
     * Maven-specific keyword for meaning to export a package to all the test module path.
     * Other keywords such as {@code "ALL-MODULE-PATH"} are understood by the Java compiler.
     */
    private static final String TEST_MODULE_PATH = "TEST-MODULE-PATH";

    /**
     * Special cases for the {@code --add-exports} option.
     * The {@value #TEST_MODULE_PATH} keyword is specific to Maven.
     * Other keywords in this set are recognized by the Java compiler.
     */
    private static final Set<String> ADD_EXPORTS_SPECIAL_CASES = Set.of("ALL-UNNAMED", TEST_MODULE_PATH);

    /**
     * The name of the module to patch, or {@code null} if unspecified.
     *
     * @see #getModuleName()
     */
    private String moduleName;

    /**
     * Values parsed from the {@code module-info-patch} file for {@code --limit-modules} option.
     */
    private final Set<String> limitModules;

    /**
     * Values parsed from the {@code module-info-patch} file for {@code --add-reads} option.
     * Option values will be prefixed by {@link #moduleName}.
     */
    private final Set<String> addReads;

    /**
     * Values parsed from the {@code module-info-patch} file for {@code --add-exports} option.
     * Option values will be prefixed by {@link #moduleName}.
     * Keys are package names.
     */
    private final Map<String, Set<String>> addExports;

    /**
     * Values parsed from the {@code module-info-patch} file for {@code --add-opens} option.
     * Option values will be prefixed by {@link #moduleName}.
     * Keys are package names.
     */
    private final Map<String, Set<String>> addOpens;

    /**
     * Whether to read all the test module path. This is requested by the {@link #TEST_MODULE_PATH} keyword in
     * {@code add-reads} option. That keyword is specific to Maven and therefore cannot be given to the compiler.
     */
    private boolean readAllTestModulePath;

    /**
     * Packages that the user wants to export to all the test module path.
     * They are identified by the {@link #TEST_MODULE_PATH} keyword, which
     * is specific to Maven and therefore cannot be given to the compiler.
     *
     * @see #getExportsToTestModulePath(Set)
     */
    private final Set<String> exportsToTestModulePath;

    /**
     * Creates an initially empty module patch.
     *
     * @param defaultModule the name of the default module if there is no {@code module-info-patch}
     */
    ModuleInfoPatch(String defaultModule) {
        if (defaultModule != null && !defaultModule.isBlank()) {
            moduleName = defaultModule;
        }
        readAllTestModulePath = true; // Default value if there is no {@code module-info-patch}.
        exportsToTestModulePath = new LinkedHashSet<>();
        limitModules = new LinkedHashSet<>();
        addReads = new LinkedHashSet<>();
        addExports = new LinkedHashMap<>();
        addOpens = new LinkedHashMap<>();
    }

    /**
     * Creates a module patch with the specified {@code --add-reads} options and everything else empty.
     *
     * @param moduleName the name of the module to patch
     * @param addReads the {@code --add-reads} option
     *
     * @see #patchWithSameReads(String)
     */
    private ModuleInfoPatch(String moduleName, Set<String> addReads) {
        this.moduleName = moduleName;
        this.addReads = addReads;
        exportsToTestModulePath = Set.of();
        limitModules = Set.of();
        addExports = Map.of();
        addOpens = Map.of();
    }

    /**
     * Loads the content of the given stream of characters.
     * This method does not close the given reader.
     *
     * @param source stream of characters to read
     * @throws IOException if an I/O error occurred while loading the file
     */
    public void load(Reader source) throws IOException {
        var reader = new StreamTokenizer(source);
        reader.slashSlashComments(true);
        reader.slashStarComments(true);
        expectToken(reader, "patch-module");
        moduleName = nextName(reader, true);
        readAllTestModulePath = false;
        expectToken(reader, '{');
        while (reader.nextToken() == StreamTokenizer.TT_WORD) {
            switch (reader.sval) {
                case "limit-modules":
                    readModuleList(reader, limitModules, Set.of());
                    break;
                case "add-reads":
                    readModuleList(reader, addReads, Set.of(TEST_MODULE_PATH));
                    readAllTestModulePath |= addReads.remove(TEST_MODULE_PATH);
                    break;
                case "add-exports":
                    readQualified(reader, addExports, ADD_EXPORTS_SPECIAL_CASES);
                    for (Map.Entry<String, Set<String>> entry : addExports.entrySet()) {
                        if (entry.getValue().remove(TEST_MODULE_PATH)) {
                            exportsToTestModulePath.add(entry.getKey());
                        }
                    }
                    break;
                case "add-opens":
                    readQualified(reader, addOpens, Set.of());
                    break;
                default:
                    throw new ModuleInfoPatchException("Unknown keyword \"" + reader.sval + '"', reader);
            }
        }
        if (reader.ttype != '}') {
            throw new ModuleInfoPatchException("Not a token", reader);
        }
        if (reader.nextToken() != StreamTokenizer.TT_EOF) {
            throw new ModuleInfoPatchException("Expected end of file but found \"" + reader.sval + '"', reader);
        }
    }

    /**
     * Skips a token which is expected to be equal to the given value.
     *
     * @param reader the reader from which to skip a token
     * @param expected the expected token value
     * @throws IOException if an I/O error occurred while loading the file
     * @throws ModuleInfoPatchException if the next token does not have the expected value
     */
    private static void expectToken(StreamTokenizer reader, String expected) throws IOException {
        if (reader.nextToken() != StreamTokenizer.TT_WORD || !expected.equals(reader.sval)) {
            throw new ModuleInfoPatchException("Expected \"" + expected + '"', reader);
        }
    }

    /**
     * Skips a token which is expected to be equal to the given value.
     * The expected character must be flagged as an ordinary character in the reader.
     *
     * @param reader the reader from which to skip a token
     * @param expected the expected character value
     * @throws IOException if an I/O error occurred while loading the file
     * @throws ModuleInfoPatchException if the next token does not have the expected value
     */
    private static void expectToken(StreamTokenizer reader, char expected) throws IOException {
        if (reader.nextToken() != expected) {
            throw new ModuleInfoPatchException("Expected \"" + expected + '"', reader);
        }
    }

    /**
     * Returns the next package or module name.
     * This method verifies that the name is non-empty and a valid Java identifier.
     *
     * @param reader the reader from which to get the package or module name
     * @param module {@code true} is expecting a module name, {@code false} if expecting a package name
     * @return the package or module name
     * @throws IOException if an I/O error occurred while loading the file
     * @throws ModuleInfoPatchException if the next token is not a package or module name
     */
    private static String nextName(StreamTokenizer reader, boolean module) throws IOException {
        if (reader.nextToken() != StreamTokenizer.TT_WORD) {
            throw new ModuleInfoPatchException("Expected a " + (module ? "module" : "package") + " name", reader);
        }
        return ensureValidName(reader, reader.sval.strip(), module);
    }

    /**
     * Verifies that the given name is a valid package or module identifier.
     *
     * @param reader the reader from which to get the line number if an exception needs to be thrown
     * @param name the name to verify
     * @param module {@code true} is expecting a module name, {@code false} if expecting a package name
     * @throws ModuleInfoPatchException if the next token is not a package or module name
     * @return the given name
     */
    private static String ensureValidName(StreamTokenizer reader, String name, boolean module) {
        int length = name.length();
        boolean expectFirstChar = true;
        int c;
        for (int i = 0; i < length; i += Character.charCount(c)) {
            c = name.codePointAt(i);
            if (expectFirstChar) {
                if (Character.isJavaIdentifierStart(c)) {
                    expectFirstChar = false;
                } else {
                    break; // Will throw exception because `expectFirstChar` is true.
                }
            } else if (!Character.isJavaIdentifierPart(c)) {
                expectFirstChar = true;
                if (c != '.') {
                    break; // Will throw exception because `expectFirstChar` is true.
                }
            }
        }
        if (expectFirstChar) { // Also true if the name is empty
            throw new ModuleInfoPatchException(
                    "Invalid " + (module ? "module" : "package") + " name \"" + name + '"', reader);
        }
        return name;
    }

    /**
     * Reads a list of modules and stores the values in the given set.
     *
     * @param reader the reader from which to get the module names
     * @param target where to store the module names
     * @param specialCases special values to accept
     * @return {@code target} or a new set if the target was initially null
     * @throws IOException if an I/O error occurred while loading the file
     * @throws ModuleInfoPatchException if the next token is not a module name
     */
    private static void readModuleList(StreamTokenizer reader, Set<String> target, Set<String> specialCases)
            throws IOException {
        do {
            while (reader.nextToken() == StreamTokenizer.TT_WORD) {
                String module = reader.sval.strip();
                if (!specialCases.contains(module)) {
                    module = ensureValidName(reader, module, true);
                }
                target.add(module);
            }
        } while (reader.ttype == ',');
        if (reader.ttype != ';') {
            throw new ModuleInfoPatchException("Missing ';' character", reader);
        }
    }

    /**
     * Reads a package name followed by a list of modules names.
     * Used for qualified exports or qualified opens.
     *
     * @param reader the reader from which to get the module names
     * @param target where to store the module names
     * @param specialCases special values to accept
     * @throws IOException if an I/O error occurred while loading the file
     * @throws ModuleInfoPatchException if the next token is not a module name
     */
    private static void readQualified(StreamTokenizer reader, Map<String, Set<String>> target, Set<String> specialCases)
            throws IOException {
        String packageName = nextName(reader, false);
        expectToken(reader, "to");
        Set<String> values = target.computeIfAbsent(packageName, (key) -> new LinkedHashSet<>());
        readModuleList(reader, values, specialCases);
    }

    /**
     * Eventually adds all dependencies declared in the test module path.
     * These dependencies are automatically added to the {@code --add-modules} option once for all modules,
     * then added to the {@code add-reads} option if the user specified the {@code TEST-MODULE-PATH} value.
     * The latter is on a per-module basis. These options are also added implicitly if the user did not put
     * a {@code module-info-patch} file in the test.
     *
     * @param dependencyResolution the result of resolving the dependencies, or {@code null} if none
     * @param runtime whether to include the runtime-only dependencies
     * @param addModules where to add the {@code --add-modules} option, or {@code null} if none
     * @throws IOException if an error occurred while reading information from a dependency
     */
    public void addTestModulePath(
            final DependencyResolverResult dependencyResolution, final boolean runtime, final Set<String> addModules)
            throws IOException {
        if (dependencyResolution == null || (addModules == null && !readAllTestModulePath)) {
            return;
        }
        final var done = new HashSet<String>(); // Added modules and their dependencies.
        for (Map.Entry<Dependency, Path> entry :
                dependencyResolution.getDependencies().entrySet()) {
            switch (entry.getKey().getScope()) {
                case TEST:
                    break;
                case TEST_ONLY:
                    if (runtime) {
                        continue;
                    }
                    break;
                case TEST_RUNTIME:
                    if (!runtime) {
                        continue; // Skip runtime dependency when only compiling.
                    }
                    break;
                default:
                    continue; // Skip non-test dependencies because they should already be in the main module-info.
            }
            Path path = entry.getValue();
            String name = dependencyResolution.getModuleName(path).orElse(null);
            if (name == null) {
                if (readAllTestModulePath) {
                    addReads.add("ALL-UNNAMED");
                }
            } else if (done.add(name)) {
                boolean modified = false;
                if (addModules != null) {
                    modified |= addModules.add(name);
                }
                if (readAllTestModulePath) {
                    modified |= addReads.add(name);
                }
                /*
                 * For making the options simpler, we do not add `--add-modules` or `--add-reads`
                 * options for modules that are required by a module that we already added. This
                 * simplification is not necessary, but makes the command-line easier to read.
                 */
                if (modified) {
                    dependencyResolution.getModuleDescriptor(path).ifPresent((descriptor) -> {
                        for (ModuleDescriptor.Requires r : descriptor.requires()) {
                            done.add(r.name());
                        }
                    });
                }
            }
        }
    }

    /**
     * Returns a patch for another module with the same {@code --add-reads} options. All other options are empty.
     * This is used when {@link #addTestModulePath(DependencyResolverResult, boolean)} has been invoked for the
     * implicit options and the callers want to replicate these default values to other modules in the sources.
     *
     * @param otherModule the other module to patch, or {@code null} or empty if none
     * @return patch for the other module, or {@code nuoo} if {@code otherModule} was null or empty
     */
    public ModuleInfoPatch patchWithSameReads(String otherModule) {
        if (otherModule == null || otherModule.isBlank()) {
            return null;
        }
        return new ModuleInfoPatch(otherModule, addReads);
    }

    /**
     * {@return the name of the module to patch, or null if unspecified and no default}.
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Writes the values of the given option if the values se is non-null.
     *
     * @param option the option for which to write the values
     * @param prefix prefix to write, followed by {@code '='}, before the value, or empty if none
     * @param values the values to write, or {@code null} if none
     * @param target where to write the option values
     */
    private static void write(String option, String prefix, Set<String> values, Options target) {
        if (!values.isEmpty()) {
            var buffer = new StringJoiner(",", (prefix != null) ? prefix + '=' : "", "");
            for (String value : values) {
                buffer.add(value);
            }
            target.addIfNonBlank("--" + option, buffer.toString());
        }
    }

    /**
     * Writes options that are qualified by module name and package name.
     *
     * @param option the option for which to write the values
     * @param values the values to write, or empty if none
     * @param target where to write the option values
     */
    private void write(String option, Map<String, Set<String>> values, Options target) {
        if (!values.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : values.entrySet()) {
                write(option, moduleName + '/' + entry.getKey(), entry.getValue(), target);
            }
        }
    }

    /**
     * Writes the {@code --add-modules} option to the given set of options. Contrarily to other
     * options managed by {@code ModuleInfoPatch}, the {@code --add-modules} option is global,
     * not an option with values defined on a per-module basis.
     *
     * @param target where to write the options
     * @param addModules the {@code --add-modules} option values, or {@code null} or empty if none
     */
    static void writeAddModules(Options target, Set<String> addModules) {
        write("add-modules", null, addModules, target);
    }

    /**
     * Writes the options.
     *
     * @param target where to write the options
     * @param opens whether to include the {@code --add-opens} options
     */
    public void writeTo(Options target, boolean opens) {
        write("limit-modules", null, limitModules, target);
        if (moduleName != null) {
            write("add-reads", moduleName, addReads, target);
            write("add-exports", addExports, target);
            if (opens) {
                write("add-opens", addOpens, target);
            }
        }
    }
}
