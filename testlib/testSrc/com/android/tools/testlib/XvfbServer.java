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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** An X server potentially backed by Xvfb. */
// LINT.IfChange
public class XvfbServer implements TrackableDisplay {
    private static final String DEFAULT_RESOLUTION = "1280x1024x24";
    private static final int MAX_RETRIES_TO_FIND_DISPLAY = 20;
    private static final String XVFB_LAUNCHER =
            "tools/emulator/testlib/display/launch_xvfb.sh";
    private static final String FFMPEG = "tools/emulator/testlib/display/ffmpeg";

    private final Path outputVideo;
    private final Path workspaceRoot;
    private final Path testOutputDir;
    private final ProcessService processService;

    private Process process;

    /** The display we're using on Linux. This will start with a colon, e.g. ":40981". */
    private String display;

    private Process recorder;

    private final String resolution;

    public XvfbServer() throws IOException {
        this(
            DEFAULT_RESOLUTION,
            Environment.getTestOutputDir().resolve("recording.mp4"),
            Environment.getWorkspaceRoot(),
            Environment.getTestOutputDir()
        );
    }

    public XvfbServer(String resolution) throws IOException {
        this(
            resolution,
            Environment.getTestOutputDir().resolve("recording.mp4"),
            Environment.getWorkspaceRoot(),
            Environment.getTestOutputDir()
        );
    }

    public XvfbServer(
        Path outputVideo,
        Path workspaceRoot,
        Path testOutputDir
    ) throws IOException {
        this(DEFAULT_RESOLUTION, outputVideo, workspaceRoot, testOutputDir);
    }

    public XvfbServer(
        Path outputVideo,
        Path workspaceRoot,
        Path testOutputDir,
        ProcessService processService
    ) throws IOException {
        this(DEFAULT_RESOLUTION, outputVideo, workspaceRoot, testOutputDir, processService);
    }

    public XvfbServer(
        String resolution,
        Path outputVideo,
        Path workspaceRoot,
        Path testOutputDir
    ) throws IOException {
        this(resolution, outputVideo, workspaceRoot, testOutputDir, ProcessService.DEFAULT);
    }

    public XvfbServer(
        String resolution,
        Path outputVideo,
        Path workspaceRoot,
        Path testOutputDir,
        ProcessService processService
    ) throws IOException {
        this.outputVideo = outputVideo;
        this.workspaceRoot = workspaceRoot;
        this.testOutputDir = testOutputDir;
        this.processService = processService;
        String display = System.getenv("DISPLAY");
        this.resolution = resolution;
        if (display == null || display.isEmpty()) {
            // If a display is provided use that, otherwise create one.
            this.display = launchUnusedDisplay();
            this.recorder = launchRecorder(this.display);
            System.out.println("Display: " + this.display);
        } else {
            this.display = display;
            System.out.println("Display inherited from parent: " + display);
        }
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public Long getRecorderPid() {
        return recorder != null ? recorder.pid() : null;
    }

    @Override
    public Long getProcessPid() {
        return process != null ? process.pid() : null;
    }

    private Process launchRecorder(String display) throws IOException {
        Path mp4 = outputVideo;
        Path ffmpeg = workspaceRoot.resolve(FFMPEG);

        // Note that -pix_fmt is required by some players:
        // https://trac.ffmpeg.org/wiki/Encode/H.264#Encodingfordumbplayers
        ProcessBuilder pb =
                new ProcessBuilder(
                        ffmpeg.toString(),
                        "-framerate",
                        "25",
                        "-f",
                        "x11grab",
                        "-i",
                        display,
                        "-pix_fmt",
                        "yuv420p",
                        "-movflags",
                        "faststart",
                        mp4.toString());
        pb.redirectOutput(testOutputDir.resolve("ffmpeg_stdout.txt").toFile());
        pb.redirectError(testOutputDir.resolve("ffmpeg_stderr.txt").toFile());
        return processService.startProcess(pb);
    }

    public String launchUnusedDisplay() {
        int retry = MAX_RETRIES_TO_FIND_DISPLAY;
        Random random = new Random();
        while (retry-- > 0) {
            int candidate = random.nextInt(65535);
            // The only mechanism with our version of Xvfb to know when it's ready
            // to accept connections is to check for the following file. Additionally,
            // this serves as a check to know if another server is using the same
            // display.
            Path socket = Paths.get("/tmp/.X11-unix", "X" + candidate);
            if (Files.exists(socket)) {
                continue;
            }
            String display = String.format(Locale.ROOT, ":%d", candidate);
            Process process = launchDisplay(display);
            try {
                boolean exited = false;
                while (!exited && !Files.exists(socket)) {
                    exited = process.waitFor(1, TimeUnit.SECONDS);
                }
                if (!exited) {
                    this.process = process;
                    System.out.println("Launched xvfb on \"" + display + "\"");
                    return display;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Xvfb was interrupted", e);
            }
        }
        throw new RuntimeException("Cannot find an unused display");
    }

    private Process launchDisplay(String display) {
        this.display = display;
        Path launcher = workspaceRoot.resolve(XVFB_LAUNCHER);
        if (Files.notExists(launcher)) {
            throw new IllegalStateException(
                    "Xvfb runfiles does not exist. "
                            + "Add a data dependency on the runfiles for Xvfb. "
                            + "It will look something like "
                            + "//tools/emulator/testlib/display:xvfb");
        }
        try {
            ProcessBuilder pb =
                    new ProcessBuilder(
                            launcher.toString(),
                            display,
                            workspaceRoot.toString(),
                            resolution)
                            .redirectErrorStream(true)
                            .redirectOutput(testOutputDir.resolve("xvfb.log").toFile());
            return processService.startProcess(pb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        if (recorder != null) {
            recorder.destroy();
            recorder = null;
        }
    }
}
// LINT.ThenChange(/adt-testutils/src/main/java/com/android/tools/tests/XvfbServer.java)
