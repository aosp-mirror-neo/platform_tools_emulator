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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapshotSetup {

    private static final Path RUNFILES_DIR = Paths.get(System.getenv("TEST_SRCDIR"));
    private static final Path WORKSPACE_ROOT = RUNFILES_DIR.resolve("_main");

    public static boolean setupFromSnapshot(TestFileSystem fileSystem, String avdName, Path systemImage) throws IOException {
        String snapshotPath = System.getProperty("emulator.test.snapshot.path");
        if (snapshotPath == null || snapshotPath.isEmpty()) {
            throw new IOException("Emulator test snapshot path is not set. Cannot setup from snapshot.");
        }

        Path sourceZipFile = Paths.get(snapshotPath);
        if (!Files.exists(sourceZipFile)) {
            sourceZipFile = WORKSPACE_ROOT.resolve(snapshotPath);
        }

        if (!Files.exists(sourceZipFile)) {
            throw new IOException("Source snapshot archive not found: " + sourceZipFile.toAbsolutePath());
        }

        Path avdHome = fileSystem.getAndroidHome().resolve("avd");
        unzip(sourceZipFile, avdHome);

        File avdHomeDir = avdHome.toFile();
        File[] avdDirs = avdHomeDir.listFiles((d, name) -> name.endsWith(".avd"));
        File[] iniFiles = avdHomeDir.listFiles((d, name) -> name.endsWith(".ini"));

        String sourceAvdName = null;

        if (avdDirs != null && avdDirs.length == 1 && avdDirs[0].isDirectory() && iniFiles != null && iniFiles.length == 1 && iniFiles[0].isFile()) {

            String avdDirName = avdDirs[0].getName();
            String iniFileName = iniFiles[0].getName();

            String avdBaseName = avdDirName.substring(0, avdDirName.lastIndexOf(".avd"));
            String iniBaseName = iniFileName.substring(0, iniFileName.lastIndexOf(".ini"));

            if (avdBaseName.equals(iniBaseName)) {
                sourceAvdName = avdBaseName;
                TestLogger.log("Detected AVD name from archive: " + sourceAvdName);
            }
        }

        if (sourceAvdName == null) {
            throw new IOException("Failed to determine AVD name from snapshot contents in " + avdHome.toString() + ". Expected one *.avd directory and one *.ini file with matching base names.");
        }

        Path extractedAvdDir = avdHome.resolve(sourceAvdName + ".avd");
        Path extractedIniFile = avdHome.resolve(sourceAvdName + ".ini");

        Path destAvdDir = avdHome.resolve(avdName + ".avd");
        Path destIniFile = avdHome.resolve(avdName + ".ini");

        if (Files.exists(extractedAvdDir) && !extractedAvdDir.equals(destAvdDir)) {
            Files.move(extractedAvdDir, destAvdDir, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(extractedIniFile) && !extractedIniFile.equals(destIniFile)) {
            Files.move(extractedIniFile, destIniFile, StandardCopyOption.REPLACE_EXISTING);
        }
        TestLogger.log("Renamed extracted snapshot files to use AVD name: " + avdName);

        modifyIniFilesForCurrentEnvironment(destIniFile, destAvdDir, avdName, systemImage);
        makePathRelative(destIniFile, destAvdDir.resolve("config.ini"), avdName);

        TestLogger.log("Snapshot setup complete for AVD: " + avdName);
        return true;
    }

    private static void modifyIniFilesForCurrentEnvironment(Path iniFile, Path avdDir, String avdName, Path systemImage) throws IOException {
        if (Files.exists(iniFile)) {
            List<String> lines = Files.readAllLines(iniFile);
            List<String> modifiedLines = new ArrayList<>();
            for (String line : lines) {
                if (line.trim().startsWith("path=")) {
                    modifiedLines.add("path=" + avdDir.toAbsolutePath());
                } else {
                    modifiedLines.add(line);
                }
            }
            Files.write(iniFile, modifiedLines, StandardOpenOption.TRUNCATE_EXISTING);
            TestLogger.log("Updated 'path' in " + iniFile.getFileName());
        }

        Path configIniFile = avdDir.resolve("config.ini");
        if (Files.exists(configIniFile)) {
            List<String> lines = Files.readAllLines(configIniFile);
            List<String> modifiedLines = new ArrayList<>();
            for (String line : lines) {
                if (line.trim().startsWith("image.sysdir.1=")) {
                    modifiedLines.add("image.sysdir.1=" + systemImage.toAbsolutePath());
                } else if (line.trim().startsWith("avd.name=")) {
                    modifiedLines.add("avd.name=" + avdName);
                } else {
                    modifiedLines.add(line);
                }
            }
            Files.write(configIniFile, modifiedLines, StandardOpenOption.TRUNCATE_EXISTING);
            TestLogger.log("Updated 'image.sysdir.1' and 'avd.name' in " + configIniFile.getFileName());
        }
    }

    private static void makePathRelative(Path iniFile, Path configIniFile, String avdName) throws IOException {
        List<String> mainIniLines = Files.readAllLines(iniFile);
        List<String> modifiedMainIniLines = new ArrayList<>();
        for (String line : mainIniLines) {
            if (line.trim().startsWith("path=")) {
                modifiedMainIniLines.add("path=" + avdName + ".avd");
            } else {
                modifiedMainIniLines.add(line);
            }
        }
        Files.write(iniFile, modifiedMainIniLines, StandardOpenOption.TRUNCATE_EXISTING);

        if (!Files.exists(configIniFile)) return;

        List<String> configLines = Files.readAllLines(configIniFile);
        List<String> modifiedConfigLines = new ArrayList<>();
        Pattern sysdirPattern = Pattern.compile("^(image\\.sysdir\\.1\\s*=\\s*)(.*)$");

        for (String line : configLines) {
            Matcher m = sysdirPattern.matcher(line);
            if (m.matches()) {
                String key = m.group(1);
                String pathStr = m.group(2).trim();
                Path path = Paths.get(pathStr);

                Path currentPath = sanitizePath(path);
                String currentPathStr = currentPath.toAbsolutePath().toString();

                String repoMarker = "/external/";
                // The relative path "../../../../../" assumes that the AVD's config.ini
                // is located five directory levels deep from the WORKSPACE_ROOT
                // (e.g., TEST_SRCDIR/_main), where the "external/" directory resides.
                // For example, if WORKSPACE_ROOT is /tmp/runfiles/_main, and the system image
                // is at /tmp/runfiles/_main/external/..., this implies the config.ini
                // is at /tmp/runfiles/_main/dir1/dir2/dir3/dir4/dir5/.avd/config.ini.
                int repoIndex = currentPathStr.indexOf(repoMarker);
                if (repoIndex != -1) {
                    String repoPath = currentPathStr.substring(repoIndex + repoMarker.length());
                    modifiedConfigLines.add(key + "../../../../../" + repoPath);
                    continue;
                }

                String runfilesMarker = ".runfiles/";
                int runfilesIndex = currentPathStr.indexOf(runfilesMarker);
                if (runfilesIndex != -1) {
                    String suffix = currentPathStr.substring(runfilesIndex + runfilesMarker.length());
                    modifiedConfigLines.add(key + "../../../../../" + suffix);
                    continue;
                }
            }
            modifiedConfigLines.add(line);
        }
        Files.write(configIniFile, modifiedConfigLines, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static Path sanitizePath(Path path) {
        if (Files.exists(path)) {
            return path;
        }
        String pathStr = path.toString();
        String[] markers = {"/external/", "/prebuilts/", "/tools/"};
        for (String marker : markers) {
            int index = pathStr.indexOf(marker);
            if (index != -1) {
                String relativePathStr = pathStr.substring(index + 1);
                Path candidate = WORKSPACE_ROOT.resolve(relativePathStr);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        int mainIndex = pathStr.indexOf("/_main/");
        if (mainIndex != -1) {
            String relativePathStr = pathStr.substring(mainIndex + 7);
            Path candidate = WORKSPACE_ROOT.resolve(relativePathStr);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return path;
    }

    private static void unzip(Path zipFilePath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFilePath.toFile()))) {
            java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path newPath = destDir.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }
        TestLogger.log("Unzipped " + zipFilePath.getFileName() + " to " + destDir);
    }
}