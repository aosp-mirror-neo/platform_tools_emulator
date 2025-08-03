/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.tools.testlib.Adb;
import com.android.tools.testlib.AndroidSdk;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.Emulator;
import com.android.tools.testlib.TestFileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmulatorTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void runEmulatorTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());

    String sdkPath = getProperty("emulator.test.sdk.path");
    AndroidSdk sdk = new AndroidSdk(Paths.get(sdkPath));

    String allFiles = getProperty("emulator.test.system.image.files");
    Path systemImageDir = getPackageDirectory(allFiles.split(" "));

    boolean isEmuNext = Optional.ofNullable(System.getProperty("emulator.test.emulator.is-emu-next")).map(s -> s.equals("1")).orElse(false);

    Emulator.createEmulator(fileSystem, "emu", systemImageDir, isEmuNext);

    String emuBin = getProperty("emulator.test.emulator.path");

    try (Display display = Display.createDefault();
         Adb adb = Adb.start(sdk, fileSystem);
         Emulator emulator = Emulator.start(fileSystem,
                                            Paths.get(emuBin),
                                            isEmuNext,
                                            sdk.getSourceDir(),
                                            display, "emu", 8554, new ArrayList<>())) {
      emulator.waitForBoot();
      adb.waitForDevice(emulator);
    }
  }

  Path getPackageDirectory(String[] files) {
    for (String file : files) {
      Path path = Paths.get(file);
      Path name = path.getFileName();
      if (name.toString().equals("source.properties")) {
        return path.getParent().toAbsolutePath().normalize();
      }
    }
    throw new IllegalStateException("source.properties not found");
  }

  String getProperty(String property) {
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException("Property " + property + " must be set.");
    }
    return value;
  }
}
