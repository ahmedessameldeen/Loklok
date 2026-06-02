// swift-tools-version:5.9
import PackageDescription

// Local Swift Package wrapping the KMP-built XCFramework.
// Build the framework first:  cd ../../sdk-kmp && ./build-xcframework.sh
// Then this package resolves against the produced LoklokCore.xcframework.
let package = Package(
    name: "LoklokKit",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "LoklokKit", targets: ["LoklokCore"]),
    ],
    targets: [
        .binaryTarget(
            name: "LoklokCore",
            path: "../../../sdk-kmp/core/build/XCFrameworks/release/LoklokCore.xcframework"
        ),
    ]
)
