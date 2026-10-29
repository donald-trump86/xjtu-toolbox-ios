#if DEBUG
import ToolboxShared

// XCTest calls through the application so Kotlin/Native is linked and initialized once.
enum CampusStorageSmokeCheck {
    static func run() -> String {
        HostedStorageCheckKt.verifyHostedKeychainStorage()
    }
}
#endif
