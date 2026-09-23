import SwiftUI

@main
struct FoxMusicApp: App {
    init() {
        AuthStore.shared.restoreToKotlin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
