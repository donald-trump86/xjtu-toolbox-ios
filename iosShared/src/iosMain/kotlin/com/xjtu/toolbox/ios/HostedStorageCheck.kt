package com.xjtu.toolbox.ios

import com.xjtu.toolbox.campus.secureCampusStore
import platform.Foundation.NSUUID

/** Called by the app-hosted XCTest bridge; never opens the real account namespaces. */
fun verifyHostedKeychainStorage(): String {
    val namespace = "hosted-test-${NSUUID().UUIDString}"
    val first = secureCampusStore(namespace)
    val other = secureCampusStore("$namespace-other")
    var result = "OK"
    try {
        first.putString("record", "test-value")
        first.putInt("number", 7)
        first.putBoolean("flag", true)
        first.putStringSet("set", setOf("first", "second"))
        other.putString("record", "other-value")
        val restored = secureCampusStore(namespace)
        check(restored.getString("record") == "test-value") { "跨实例读取失败" }
        check(restored.getInt("number", 0) == 7 && restored.getBoolean("flag", false)) { "类型往返失败" }
        check(restored.getStringSet("set") == setOf("first", "second")) { "集合往返失败" }
        restored.remove("number")
        check(!first.contains("number")) { "删除键失败" }
        restored.clear()
        check(first.getString("record") == null) { "命名空间清理失败" }
        check(other.getString("record") == "other-value") { "清理越过命名空间" }
    } catch (failure: Exception) {
        result = failure.message ?: "受保护存储检查失败"
    } finally {
        listOf(first, other).forEach { target ->
            runCatching { target.clear() }.onFailure {
                if (result == "OK") result = it.message ?: "测试数据清理失败"
            }
        }
    }
    return result
}
