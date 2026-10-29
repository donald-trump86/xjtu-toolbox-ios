package com.xjtu.toolbox.platform

// AnonymousId persists its own cryptographically random installation ID on this platform.
actual fun deviceInstallId(salt: String): String? = null
