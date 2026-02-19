filegroup(
    name = "xvfb",
    srcs = [
        "launch_xvfb.sh",
        "//tools/emulator/testlib/display/fonts:fonts_extended",
        "//tools/emulator/testlib/display/lib:xvfb",
        "//tools/emulator/testlib/display/x",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ffmpeg_files",
    srcs = select({
        "@platforms//os:linux": [
            "ffmpeg",
        ],
        "@platforms//os:macos": [
            "ffmpeg_mac64",
        ],
        "@platforms//os:windows": [
            "ffmpeg_win64.exe",
        ],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)
