load("//build/bazel/rules:iml.bzl", "iml_module", "iml_test")
load("//tools/emulator/tests:defaults.bzl", "DEFAULT_SYSTEM_IMAGE")

# managed by go/iml_to_build
iml_module(
    name = "android.tools.emulator.tests",
    enable_tests = False,
    iml_files = ["android.tools.emulator.tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/emulator/testlib:android.sdktools.testlib",
        "//tools/adt/idea/as-driver:as-driver.utils",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    deps = ["@intellij//:intellij-sdk"],
)

iml_test(
    name = "EmulatorTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        DEFAULT_SYSTEM_IMAGE,
    ],
    module = ":android.tools.emulator.tests",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorTest",
)
