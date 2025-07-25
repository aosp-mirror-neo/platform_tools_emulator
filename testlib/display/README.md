
# Building xvfb

## Why do we need to rebuild xvfb and xkbcomp?

These tools are compiled to use absolute paths on the system. When run in parallel in sandbox environment, it is necesary to add their dependencies and check them in.

## Getting the sources

We need the sources for both xserver and xkbcomp

```
wget https://www.x.org/releases/X11R7.7/src/xserver/xorg-server-1.12.2.tar.gz
wget https://www.x.org/releases/X11R7.7/src/everything/xkbcomp-1.2.4.tar.gz
```

## Modifying

Two small diffs need to be applied to the configure files to allow relative paths

```
diff --git a/xkbcomp-1.2.4/configure b/xkbcomp-1.2.4/configure
index 244ebc1..89f1a2e 100755
--- a/xkbcomp-1.2.4/configure
+++ b/xkbcomp-1.2.4/configure
@@ -1176,7 +1176,7 @@ fi
 
 # Check all directory arguments for consistency.
 for ac_var in  exec_prefix prefix bindir sbindir libexecdir datarootdir \
-               datadir sysconfdir sharedstatedir localstatedir includedir \
+               sysconfdir sharedstatedir localstatedir includedir \
                oldincludedir docdir infodir htmldir dvidir pdfdir psdir \
                libdir localedir mandir
 do
```
and
```
diff --git a/xorg-server-1.12.2/configure b/xorg-server-1.12.2/configure
index 7478ee6..3f16b52 100755
--- a/xorg-server-1.12.2/configure
+++ b/xorg-server-1.12.2/configure
@@ -1865,7 +1865,7 @@ fi
 
 # Check all directory arguments for consistency.
 for ac_var in  exec_prefix prefix bindir sbindir libexecdir datarootdir \
-               datadir sysconfdir sharedstatedir localstatedir includedir \
+               sysconfdir sharedstatedir localstatedir includedir \
                oldincludedir docdir infodir htmldir dvidir pdfdir psdir \
                libdir localedir mandir
 do
```

### Building

To build Xvfb:

```
./configure --datadir=./share --with-xkb-bin-directory=./bin --with-xkb-output=/tmp/xkboutput --enable-install-setuid --prefix="$(mktemp -d)"
make -j3 -k
```
This fails to compile all targets but it will still produce a valid Xvfb at ``./hw/vfb/Xvfb``.

To build xkbcomp

```
./configure --datadir=./share --prefix="$(mktemp -d)"
make -j3 -k
```

Which should produce ``./xkbcomp``

# Getting ffmpeg

To record videos from these xvfb sessions we use ffmpeg. There is a statically linked version of it that can be obtained from
https://trac.ffmpeg.org/wiki/CompilationGuide/Ubuntu.

## Windows

The Windows version (`ffmpeg_win64.exe`) was obtained from [here](https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n5.1-latest-win64-lgpl-5.1.zip). The "-shared-" versions do not work; the executable will exit with code -1073741515 without emitting anything to stdout or stderr.