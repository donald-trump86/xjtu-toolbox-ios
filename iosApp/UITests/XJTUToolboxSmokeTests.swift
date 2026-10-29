import XCTest

final class XJTUToolboxSmokeTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testChineseNavigationAndLoginForm() {
        let app = XCUIApplication()
        defer { XCUIDevice.shared.orientation = .portrait }
        app.launch()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "我的")).firstMatch.waitForExistence(timeout: 20))
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "我的")).firstMatch.tap()
        XCTAssertTrue(app.staticTexts["校园账号"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["使用校园统一身份认证登录"].exists)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "学校 WebVPN")).firstMatch.exists)
        capture(name: "Account-form")
        let username = app.descendants(matching: .any)["login-username"]
        XCTAssertTrue(username.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(username.frame.width, 420.5)
        username.tap()
        username.typeText("demo-campus-user")
        let password = app.descendants(matching: .any)["login-password"]
        XCTAssertTrue(password.waitForExistence(timeout: 10))
        password.tap()
        password.typeText("12345678")
        capture(name: "Keyboard-and-secure-input")
        // Synthetic input only; the test never submits a login request.
        app.buttons["设置"].tap()
        XCTAssertTrue(app.staticTexts["深色模式"].waitForExistence(timeout: 10))
        chooseAppearance("始终深色", in: app)
        capture(name: "Chinese-settings-dark")
        chooseAppearance("始终浅色", in: app)
        capture(name: "Chinese-settings-light")
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscape = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in app.frame.width > app.frame.height },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 10), .completed)
        // The system rotation animation can outlast XCTest's geometry update.
        Thread.sleep(forTimeInterval: 0.6)
        XCTAssertTrue(app.staticTexts["深色模式"].waitForExistence(timeout: 10))
        capture(name: "Landscape-settings")
    }

    private func chooseAppearance(_ label: String, in app: XCUIApplication) {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "深色模式")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        let options = app.staticTexts.matching(identifier: label)
        XCTAssertTrue(options.firstMatch.waitForExistence(timeout: 10))
        options.element(boundBy: options.count - 1).tap()
        XCTAssertTrue(app.staticTexts["跟随系统"].waitForNonExistence(timeout: 5))
        // Compose popup animations are not tracked by XCTest's UIKit idle detection.
        Thread.sleep(forTimeInterval: 0.6)
    }

    private func capture(name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
