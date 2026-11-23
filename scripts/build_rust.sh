#!/usr/bin/env bash
# road-loader-core builds the Rust core relative to its own Gradle root; when
# this project is the root, forward to the submodule's script.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/RoadLoader/scripts/build_rust.sh" "$@"
