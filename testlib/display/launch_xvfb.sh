#!/bin/bash

set -o errexit
if [[ -n "$VERBOSE" ]]; then
  set -o xtrace
fi

# Deprecated. Pass a file descriptor as the 4th argument instead.
DISPLAYNUM="$1"
# The absolute path to the workspace root.
WORKSPACE="$2"
# The resolution of the screen to create, e.g., "1280x1024x24".
RESOLUTION="$3"
# A file descriptor. Xvfb will choose an unused display number and write it to
# this FD when it is ready to accept connections.
DISPLAYFD="$4"

# Kills child processes and removes temporary Xvfb and font directories.
cleanup() {
  rv=$?
  rm -rf "$NEW_XVFB_ROOT"
  rm -rf "$TMP_FONTDIR"
  pkill -P "$BASHPID"
  exit $rv
}

if [[ ! -d "$TEST_TMPDIR" ]]; then
  echo "TEST_TMPDIR not set to a directory, falling back to /tmp"
  TEST_TMPDIR=/tmp
fi

if [[ -z "$RESOLUTION" ]]; then
  RESOLUTION="1280x1024x24"
fi

FONTDIR="$WORKSPACE/tools/emulator/testlib/display/fonts/fonts_extended"
XVFB_ROOT="$WORKSPACE/tools/emulator/testlib/display/x"

# Copy runfiles to a unique directory so that we can write inside the directory
# structure
NEW_XVFB_ROOT="$(mktemp -d $TEST_TMPDIR/xvfbtmpXXXXX)"
cp -r "$XVFB_ROOT/"* "$NEW_XVFB_ROOT/"

# Symlink fontdir because X has a 255 char limit for string args
TMP_FONTDIR="$(mktemp -d /tmp/fontsXXXXX)"
NEW_FONTDIR="$TMP_FONTDIR/fonts"
ln -s "$FONTDIR" "$NEW_FONTDIR"

# Cleanup temporary directories and child processes before exiting.
trap "cleanup" EXIT

cd "$NEW_XVFB_ROOT"

# We compile Xvfb and xkbcomp so that they use relative paths for
# /usr/local/share/X11. We have to hack the build system to allow this because
# they (reasonably) assume that they will be absolute paths.  Accordingly,
# they behave to happily cd to relative directories from anywhere.  These
# symlinks ensure that when this is attempted, we end up in the right place.
#
# Note, this creates a system of infinitely nested directories/symlinks.
find "$PWD/"* -type d -exec chmod 755 {} \;
find "$PWD/"* -type d -exec ln -s "$PWD/share" {}/share \;

declare -a args
args=(
  -fp "$NEW_FONTDIR"
  -noreset
  -nolisten tcp
  -ac
  -screen 0 "$RESOLUTION"
)

if [[ -n "$DISPLAYFD" ]]; then
  args+=(-displayfd "$DISPLAYFD")
else
  args+=("$DISPLAYNUM")
fi

LD_LIBRARY_PATH="$WORKSPACE/tools/emulator/testlib/display/lib" \
  "$NEW_XVFB_ROOT/bin/Xvfb" "${args[@]}" & wait

