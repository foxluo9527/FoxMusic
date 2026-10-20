import SwiftUI
import PhotosUI
import FoxShared

// MARK: - 编辑资料页

struct EditProfileView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var auth = AuthStore.shared

    @State private var nickname: String = ""
    @State private var signature: String = ""
    @State private var avatarUrl: String?
    @State private var showAvatarPicker = false
    @State private var saving = false

    var body: some View {
        Form {
            Section {
                HStack {
                    Spacer()
                    Button {
                        showAvatarPicker = true
                    } label: {
                        VStack(spacing: 8) {
                            AsyncImage(url: URL(string: avatarUrl ?? "")) { img in
                                img.resizable().scaledToFill()
                            } placeholder: {
                                Color.gray.opacity(0.3)
                            }
                            .frame(width: 80, height: 80)
                            .clipShape(Circle())
                            Text("点击更换头像").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                }
                .listRowBackground(Color.clear)
            }
            Section("昵称") {
                TextField("请输入昵称", text: $nickname)
                    .textInputAutocapitalization(.never)
            }
            Section("个性签名") {
                TextField("介绍一下自己", text: $signature, axis: .vertical)
                    .lineLimit(2...4)
            }
        }
        .navigationTitle("编辑资料")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(saving ? "保存中..." : "保存") { save() }
                    .disabled(saving)
            }
        }
        .onAppear {
            nickname = auth.nickname
            signature = auth.signature ?? ""
            avatarUrl = auth.avatarUrl
        }
        .sheet(isPresented: $showAvatarPicker) {
            AvatarPickerView { url in
                avatarUrl = url
            }
        }
    }

    private func save() {
        saving = true
        FoxMusicApi().updateProfile(
            nickname: nickname,
            avatar: avatarUrl,
            signature: signature,
            email: nil,
            homepageBackground: nil
        ) { user, error in
            DispatchQueue.main.async {
                saving = false
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("资料已更新")
                    if let user {
                        AuthStore.shared.updateFrom(user: user)
                    }
                    dismiss()
                }
            }
        }
    }
}

/// 头像选择器：PHPicker 选图 → 上传 → 返回远程 URL。
struct AvatarPickerView: UIViewControllerRepresentable {
    let onPicked: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: AvatarPickerView
        init(_ parent: AvatarPickerView) { self.parent = parent }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let result = results.first else { parent.dismiss(); return }
            result.itemProvider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                guard let url else { DispatchQueue.main.async { self.parent.dismiss() }; return }
                let dst = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                do {
                    if FileManager.default.fileExists(atPath: dst.path) { try FileManager.default.removeItem(at: dst) }
                    try FileManager.default.copyItem(at: url, to: dst)
                    FoxMusicApi().uploadImage(filePath: dst.path) { remoteUrl, error in
                        DispatchQueue.main.async {
                            if let remoteUrl { self.parent.onPicked(remoteUrl) }
                            self.parent.dismiss()
                        }
                    }
                } catch {
                    DispatchQueue.main.async { self.parent.dismiss() }
                }
            }
        }
    }
}

// MARK: - 隐私设置页

struct PrivacySettingsView: View {
    @State private var isPrivate = false
    @State private var isSearchable = true
    @State private var blacklist: [BlacklistUserDto] = []
    @State private var loading = false

    var body: some View {
        Form {
            Section("隐私") {
                Toggle(isOn: Binding(
                    get: { isPrivate },
                    set: { newValue in
                        isPrivate = newValue
                        updatePrivacy(isPrivate: newValue)
                    }
                )) {
                    Label("私密账号", systemImage: "lock")
                }
                Toggle(isOn: Binding(
                    get: { isSearchable },
                    set: { newValue in
                        isSearchable = newValue
                        updatePrivacy(isSearchable: newValue)
                    }
                )) {
                    Label("允许被搜索到", systemImage: "magnifyingglass")
                }
            }

            Section("黑名单") {
                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding()
                } else if blacklist.isEmpty {
                    Text("暂无拉黑用户").foregroundStyle(.secondary)
                } else {
                    ForEach(blacklist, id: \.id) { user in
                        HStack {
                            AsyncImage(url: URL(string: user.avatar ?? "")) { img in
                                img.resizable().scaledToFill()
                            } placeholder: { Color.gray.opacity(0.3) }
                                .frame(width: 36, height: 36).clipShape(Circle())
                            Text(user.nickname ?? "用户")
                            Spacer()
                            Button("取消拉黑") {
                                unblock(userId: user.id)
                            }
                            .buttonStyle(.bordered).controlSize(.small)
                        }
                    }
                }
            }
        }
        .navigationTitle("隐私设置")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { load() }
    }

    private func load() {
        loading = true
        let api = FoxMusicApi()
        api.fetchPrivacySettings { settings, _ in
            if let settings {
                DispatchQueue.main.async {
                    isPrivate = settings.isPrivate
                    isSearchable = settings.isSearchable
                }
            }
        }
        api.fetchBlacklist { list, _ in
            DispatchQueue.main.async {
                blacklist = (list as? [BlacklistUserDto]) ?? []
                loading = false
            }
        }
    }

    private func updatePrivacy(isPrivate: Bool? = nil, isSearchable: Bool? = nil) {
        FoxMusicApi().updatePrivacySettings(
            isPrivate: isPrivate.map { KotlinBoolean(bool: $0) },
            isSearchable: isSearchable.map { KotlinBoolean(bool: $0) }
        ) { _, error in
            if let error { DispatchQueue.main.async { ToastCenter.shared.show(error.localizedDescription) } }
        }
    }

    private func unblock(userId: Int64) {
        FoxMusicApi().unblockUser(userId: userId) { success, error in
            DispatchQueue.main.async {
                if success?.boolValue == true {
                    blacklist.removeAll { $0.id == userId }
                    ToastCenter.shared.show("已取消拉黑")
                } else {
                    ToastCenter.shared.show(error?.localizedDescription ?? "操作失败")
                }
            }
        }
    }
}
