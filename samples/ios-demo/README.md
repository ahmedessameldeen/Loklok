# Loklok iOS demo (SwiftUI)

A minimal SwiftUI chat app consuming the Loklok SDK through the KMP-built XCFramework.

## Files
- `LoklokKit/Package.swift` — local Swift Package wrapping `LoklokCore.xcframework` (a `binaryTarget`).
- `LoklokDemo/LoklokDemoApp.swift` — app entry.
- `LoklokDemo/ContentView.swift` — setup + chat screens, driven by `IosChannel`'s callback API.
- `LoklokDemo/DevToken.swift` — dev-only JWT minting (CryptoKit HMAC).

## Setup (Xcode required)
1. Build the framework:
   ```bash
   cd ../../sdk-kmp && ./build-xcframework.sh
   ```
   This produces `sdk-kmp/core/build/XCFrameworks/release/LoklokCore.xcframework`, which
   `LoklokKit/Package.swift` already points to.
2. Create an iOS App target in Xcode (SwiftUI lifecycle) and add the four `LoklokDemo/*.swift`
   files, **or** open this folder and add a new app target.
3. In the target's **Frameworks, Libraries, and Embedded Content**, add the local package
   `LoklokKit` (File ▸ Add Package Dependencies ▸ Add Local… ▸ select `LoklokKit/`).
4. Set `baseURL` in `ContentView.swift` to your deployed Worker, or `http://localhost:8787` with a
   local `wrangler dev` (allow arbitrary loads / use the simulator).
5. Run on a simulator. Open the Android demo too and chat across both into the same room.

## Why a callback bridge?
Kotlin `Flow` and `suspend` are awkward from Swift, so `iosMain/IosBridge.kt` exposes `IosChannel`
with plain callbacks (`observeMessages { … }` returning a `Cancellable`) and fire-and-forget
`send`/`setTyping`. Callbacks arrive on the main dispatcher, ready to set SwiftUI `@State`.
