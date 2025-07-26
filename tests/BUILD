load("//build/bazel/rules:iml.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "android.tools.emulator.tests",
    enable_tests = False,
    iml_files = ["android.tools.emulator.tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/emulator/testlib:android.sdktools.testlib",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    deps = ["@intellij//:intellij-sdk"],
)

java_library(
    name = "emulator.tests",
    srcs = glob(["testSrc/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/emulator/testlib",
        "@bazel_tools//tools/jdk:TestRunner",
    ],
)
