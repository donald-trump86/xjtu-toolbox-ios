import XCTest
@testable import XJTUToolbox

final class CampusKeychainHostTests: XCTestCase {
    func testKeychainRoundTripAndNamespaceIsolationInApplicationHost() {
        XCTAssertEqual(CampusStorageSmokeCheck.run(), "OK")
    }
}
