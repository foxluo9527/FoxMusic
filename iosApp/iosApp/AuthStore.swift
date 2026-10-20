import Foundation
import Security
import FoxShared

/// Keychain 持久化登录态；Kotlin [FoxAuthStore] 只持内存令牌给 Ktor。
final class AuthStore: ObservableObject {
    static let shared = AuthStore()

    @Published private(set) var isLoggedIn = false

    private let service = "com.fox.music.iosapp.auth"
    private enum Key: String {
        case token, refresh, userId, username, nickname, avatar, email, signature
    }

    private init() {}

    func restoreToKotlin() {
        // Kotlin 侧 401 自动续期成功后要回写 Keychain —— 服务端会**轮换** refreshToken
        // （旧 token 作废），不回写的话下次冷启动就续不了期、只能重新登录。
        // Kotlin 只改内存态，落盘归这里（与 persistFromKotlin 同一套 Keychain 写入口）。
        FoxAuthStore.shared.onTokensRefreshed = { [weak self] token, refresh in
            guard let self else { return }
            // 续期发生在 Ktor/Darwin 的后台线程，回到主线程再写（isLoggedIn 是 @Published）
            DispatchQueue.main.async {
                self.set(token, for: .token)
                self.set(refresh, for: .refresh)
                self.isLoggedIn = (token?.isEmpty == false)
            }
        }

        // 会话真正结束（401 刷新时服务端拒绝 refreshToken，Kotlin 侧已同步 clear 内存态）
        // → 清 Keychain + 置未登录 + 通知实时链路断开。否则会停在「UI 显示已登录、
        // 但所有鉴权接口全 401、页面空白」的僵尸态（对齐 Android 的 requireReLogin）。
        // Ktor 在后台线程回调，UI 状态必须回主线程改。
        FoxAuthStore.shared.onSessionExpired = { [weak self] in
            guard let self else { return }
            DispatchQueue.main.async {
                self.logout()
            }
        }

        let token = string(for: .token)
        let refresh = string(for: .refresh)
        let user: AuthUser? = {
            guard let idStr = string(for: .userId), let id = Int64(idStr),
                  let username = string(for: .username) else { return nil }
            return AuthUser(
                id: id,
                username: username,
                nickname: string(for: .nickname) ?? username,
                avatarUrl: string(for: .avatar),
                email: string(for: .email)
            )
        }()
        FoxAuthStore.shared.restore(token: token, refreshToken: refresh, user: user)
        isLoggedIn = FoxAuthStore.shared.isLoggedIn
    }

    func persistFromKotlin() {
        guard let token = FoxAuthStore.shared.token, let user = FoxAuthStore.shared.user else {
            clear()
            return
        }
        set(token, for: .token)
        set(FoxAuthStore.shared.refreshToken, for: .refresh)
        set(String(user.id), for: .userId)
        set(user.username, for: .username)
        set(user.nickname, for: .nickname)
        set(user.avatarUrl, for: .avatar)
        set(user.email, for: .email)
        isLoggedIn = true
        // 通知实时链路：登录后要建 WebSocket 并补拉一次未读
        FoxRealtimeService.shared.onAuthChanged()
    }

    func logout() {
        FoxAuthStore.shared.clear()
        clear()
        isLoggedIn = false
        // 通知实时链路：登出后 Coordinator 会主动断开连接并停掉保活
        FoxRealtimeService.shared.onAuthChanged()
    }

    private func clear() {
        [Key.token, .refresh, .userId, .username, .nickname, .avatar, .email].forEach { delete($0) }
    }

    /// 当前登录用户 id（未登录为 0）—— 社区等需要判断「是否自己的内容」的页面用。
    /// Kotlin 侧 Long 不接 nil，所以用 0 表示未登录。
    var currentUserId: Int64 {
        Int64(string(for: .userId) ?? "") ?? 0
    }

    var nickname: String { string(for: .nickname) ?? string(for: .username) ?? "" }
    var avatarUrl: String? { string(for: .avatar) }
    var signature: String? { string(for: .signature) }

    /// 资料更新后同步 Keychain + Kotlin 内存态。
    func updateFrom(user: UserDto) {
        set(user.nickname, for: .nickname)
        set(user.avatar, for: .avatar)
        set(user.signature, for: .signature)
        set(user.email, for: .email)
        FoxAuthStore.shared.updateUser(user: user.toAuthUser())
        objectWillChange.send()
    }

    private func string(for key: Key) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func set(_ value: String?, for key: Key) {
        delete(key)
        guard let value, let data = value.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
            kSecValueData as String: data,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private func delete(_ key: Key) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key.rawValue,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
