/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.testlib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class Environment {

    public enum Arch {
        X86,
        X86_64,
        ARM64,
        UNKNOWN
    }

    public enum Os {
        WIN,
        MAC,
        LINUX
    }

    private static String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final Arch ARCH = fromString(System.getProperty("os.arch"));
    private static Path workspaceRoot;
    private static Path testOutput;

    public static boolean runningFromBazel() {
        return System.getenv().containsKey("TEST_WORKSPACE");
    }

    public static Path getTestOutputDir() throws IOException {
        if (testOutput != null) {
            return testOutput;
        }
        // If running via bazel, returns the sandboxed test output dir.
        String testOutputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
        if (testOutputDir != null) {
            testOutput = Paths.get(testOutputDir);
        } else {
            testOutput = Files.createTempDirectory(".test_output");
        }
        return testOutput;
    }

    static boolean isWindows() {
        return OS.contains("win");
    }

    static boolean isMac() {
        return OS.contains("mac");
    }

    static boolean isArm64() {
        return ARCH == Arch.ARM64;
    }

    private static Arch fromString(String arch) {
        if ("x86_64".equals(arch) || "amd64".equals(arch)) return Arch.X86_64;
        if ("i386".equals(arch) || "x86".equals(arch)) return Arch.X86;
        if ("aarch64".equals(arch) || "arm64".equals(arch)) return Arch.ARM64;
        return Arch.UNKNOWN;
    }

    public static synchronized Path getWorkspaceRoot() {
        // The logic below depends on the current working directory, so we save the results and hope
        // the first call is early enough for the user.dir property to be unchanged.
        if (workspaceRoot == null) {
            // If it is provided by environment variables, use it.
            if (System.getenv("AGP_WORKSPACE_LOCATION") != null) {
                workspaceRoot = Paths.get(System.getenv("AGP_WORKSPACE_LOCATION"));
                return workspaceRoot;
            }

            // If we are using Bazel (which defines the following env vars), simply use
            // the sandboxed root they provide us.
            String workspace = System.getenv("TEST_WORKSPACE");
            String workspaceParent = System.getenv("TEST_SRCDIR");
            if (workspace != null && workspaceParent != null) {
                workspaceRoot = Paths.get(workspaceParent, workspace);

                try {
                    // Bazel munges Windows paths. Which triggers CodeInsightTestFixtureImpl
                    // ::assertFileEndsWithCaseSensitivePath. This is a (hacky?) workaround.
                    workspaceRoot = workspaceRoot.toRealPath();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }

                return workspaceRoot;
            }

            Path currDir = Paths.get("").toAbsolutePath();
            Path initialDir = currDir;

            // If we're using a non-Bazel build system. At this point, assume our working directory
            // is located underneath our codebase's root folder, so keep navigating up until we find
            // it. If we're using Bazel, we should still look to see if there's a larger outermost
            // workspace since we might be within a nested workspace.
            while (currDir != null) {
                Path workspacePath = currDir.resolve("WORKSPACE");
                // Ensure that the workspacePath being looked at is NOT a directory.
                if (Files.isRegularFile(workspacePath)) {
                    workspaceRoot = currDir;
                }
                currDir = currDir.getParent();
            }

            if (workspaceRoot == null) {
                throw new IllegalStateException(
                        "Could not find WORKSPACE root. Is the original working directory a "
                                + "subdirectory of the Android Studio codebase?\n\n"
                                + "pwd = "
                                + initialDir);
            }
        }

        return workspaceRoot;
    }
}
