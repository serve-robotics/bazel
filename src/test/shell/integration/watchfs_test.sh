#!/bin/bash
#
# Copyright 2021 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# --- begin runfiles.bash initialization ---
# Copy-pasted from Bazel's Bash runfiles library (tools/bash/runfiles/runfiles.bash).
if [[ ! -d "${RUNFILES_DIR:-/dev/null}" && ! -f "${RUNFILES_MANIFEST_FILE:-/dev/null}" ]]; then
  if [[ -f "$0.runfiles_manifest" ]]; then
    export RUNFILES_MANIFEST_FILE="$0.runfiles_manifest"
  elif [[ -f "$0.runfiles/MANIFEST" ]]; then
    export RUNFILES_MANIFEST_FILE="$0.runfiles/MANIFEST"
  elif [[ -f "$0.runfiles/bazel_tools/tools/bash/runfiles/runfiles.bash" ]]; then
    export RUNFILES_DIR="$0.runfiles"
  fi
fi
if [[ -f "${RUNFILES_DIR:-/dev/null}/bazel_tools/tools/bash/runfiles/runfiles.bash" ]]; then
  source "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"
elif [[ -f "${RUNFILES_MANIFEST_FILE:-/dev/null}" ]]; then
  source "$(grep -m1 "^bazel_tools/tools/bash/runfiles/runfiles.bash " \
            "$RUNFILES_MANIFEST_FILE" | cut -d ' ' -f 2-)"
else
  echo >&2 "ERROR: cannot find @bazel_tools//tools/bash/runfiles:runfiles.bash"
  exit 1
fi
# --- end runfiles.bash initialization ---

source "$(rlocation "io_bazel/src/test/shell/integration_test_setup.sh")" \
  || { echo "integration_test_setup.sh not found!" >&2; exit 1; }

cd "$TEST_TMPDIR"

function set_up() {
    cd ${WORKSPACE_DIR}
}

function tear_down() {
  # Start with a fresh bazel server.
  bazel shutdown
}

function helper() {
  startup_option="$1"
  command_option="$2"
  # Name of the calling function to end up using distinct packages.
  local -r pkg=${FUNCNAME[1]}
  mkdir $pkg || fail "mkdir $pkg"
  cat > "$pkg/BUILD" << 'EOF'
genrule(
    name = "foo",
    srcs = ["whocares.in"],
    outs = ["foo.out"],
    cmd = "touch $@",
)
EOF
  touch "$pkg/whocares.in"
  bazel $startup_option build $command_option "//$pkg:foo" &> "$TEST_log" \
    || fail "Expected success."
  bazel $startup_option build $command_option "//$pkg:foo" \
    --record_full_profiler_data \
    --noslim_profile \
    --profile=/tmp/profile.log &> "$TEST_log" || fail "Expected success."
  cat /tmp/profile.log | grep "VFS stat" > "$TEST_log" \
    || fail "Missing profile file."
}

function test_nowatchfs() {
  helper "" ""
  local -r pkg=${FUNCNAME[0]}
  expect_log "VFS stat.*${pkg}/whocares.in"
}

function test_startup() {
  helper "--watchfs" ""
  local -r pkg=${FUNCNAME[0]}
  expect_not_log "VFS stat.*${pkg}/whocares.in"
}

function test_command() {
  helper "" "--watchfs"
  local -r pkg=${FUNCNAME[0]}
  expect_not_log "VFS stat.*${pkg}/whocares.in"
}

function test_both() {
  helper "--watchfs" "--watchfs"
  local -r pkg=${FUNCNAME[0]}
  expect_not_log "VFS stat.*${pkg}/whocares.in"
}

run_suite "Integration tests for --watchfs."
