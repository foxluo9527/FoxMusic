import Foundation
import Security
import FoxShared

/// Keychain 持久化登录态；Kotlin [FoxAuthStore] 只持内存令牌给 Ktor。
final class AuthStore: ObservableObject {
    static let shared = AuthStore()

    @Published private(set) var isLoggedIn = false

    private let service = "com.fox.music.iosapp.auth"
    private enum Key: String {
        case token, refresh, userId, username, nickname, avatar, email
    }

    private init() {}

    func restoreToKotlin() {
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
    }

    func logout() {
        FoxAuthStore.shared.clear()
        clear()
        isLoggedIn = false
    }

    private func clear() {
        [Key.token, .refresh, .userId, .username, .nickname, .avatar, .email].forEach { delete($0) }
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
