#!/usr/bin/env bash
# Build the iOS XCFramework for the Loklok SDK (requires Xcode).
# Output: core/build/XCFrameworks/release/LoklokCore.xcframework
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :core:assembleLoklokCoreReleaseXCFramework
echo
echo "XCFramework built at:"
echo "  $(pwd)/core/build/XCFrameworks/release/LoklokCore.xcframework"
echo
echo "Point samples/ios-demo/LoklokKit/Package.swift at it (already wired), or drag it into Xcode."
