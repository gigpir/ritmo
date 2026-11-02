// swift-tools-version: 6.0
import PackageDescription

// Only for running the timer logic tests from the terminal (`swift test`).
// The app itself is built from Ritmo.xcodeproj.
let package = Package(
    name: "Ritmo",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "RitmoCore", path: "Ritmo/Core"),
        .testTarget(name: "RitmoCoreTests", dependencies: ["RitmoCore"], path: "Tests"),
    ]
)
