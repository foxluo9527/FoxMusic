import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import AVFoundation
import FoxShared

// MARK: - 聊天媒体/语音处理

/// 处理聊天页的图片/视频/文件/语音选择：选本地文件 → 上传 → 通过 FoxChatInputBridge 通知 Kotlin 发送。
@MainActor
final class ChatMediaPicker: NSObject, ObservableObject {
    static let shared = ChatMediaPicker()

    /// 触发相册选图
    func pickImage(presenter: UIViewController) {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    /// 触发拍照
    func takePhoto(presenter: UIViewController) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            ToastCenter.shared.show("设备不支持相机")
            return
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = ["public.image"]
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    /// 触发录像
    func captureVideo(presenter: UIViewController) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            ToastCenter.shared.show("设备不支持相机")
            return
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = ["public.movie"]
        picker.videoQuality = .typeMedium
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    /// 触发文件选择
    func pickFile(presenter: UIViewController) {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }

    // MARK: - 语音录制

    private var audioRecorder: AVAudioRecorder?
    private var recordingStartTime: Date?
    private var recordingURL: URL?

    /// 切到语音输入模式时提前申请麦克风权限。
    /// 若留到「按住说话」时才弹系统权限弹窗，弹窗会打断并取消正在按住的触摸手势。
    func requestMicPermissionIfNeeded() {
        if #available(iOS 17.0, *) {
            switch AVAudioApplication.shared.recordPermission {
            case .granted:
                break
            case .denied:
                ToastCenter.shared.show("请在系统设置中开启麦克风权限")
            case .undetermined:
                AVAudioApplication.requestRecordPermission { _ in }
            @unknown default:
                break
            }
        } else {
            let session = AVAudioSession.sharedInstance()
            switch session.recordPermission {
            case .granted:
                break
            case .denied:
                ToastCenter.shared.show("请在系统设置中开启麦克风权限")
            case .undetermined:
                session.requestRecordPermission { _ in }
            @unknown default:
                break
            }
        }
    }

    func startVoiceRecording() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
        } catch {
            ToastCenter.shared.show("无法启动录音：\(error.localizedDescription)")
            return
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("voice_\(Int(Date().timeIntervalSince1970)).m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        ]
        do {
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            // 无麦克风权限时 record() 返回 false（不抛错），必须显式判断
            guard recorder.record() else {
                restorePlaybackSession()
                ToastCenter.shared.show("无法录音，请检查麦克风权限")
                return
            }
            audioRecorder = recorder
            recordingURL = url
            recordingStartTime = Date()
        } catch {
            restorePlaybackSession()
            ToastCenter.shared.show("录音失败：\(error.localizedDescription)")
        }
    }

    func endVoiceRecording() {
        guard let recorder = audioRecorder, recorder.isRecording else { return }
        let duration = Date().timeIntervalSince(recordingStartTime ?? Date())
        recorder.stop()
        audioRecorder = nil
        restorePlaybackSession()
        guard duration >= 1.0 else {
            ToastCenter.shared.show("语音时间太短")
            return
        }
        guard let url = recordingURL else { return }
        uploadAndSend(type: "audio", url: url, fileName: url.lastPathComponent, durationMs: Int64(duration * 1000))
    }

    func cancelVoiceRecording() {
        audioRecorder?.stop()
        audioRecorder = nil
        recordingURL = nil
        recordingStartTime = nil
        restorePlaybackSession()
    }

    /// 录音结束后还原播放会话（PlayerModel 初始化时设置的是 .playback）。
    /// 否则 .playAndRecord 会一直生效，后续音乐/语音播放被路由到听筒。
    private func restorePlaybackSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)
    }

    // MARK: - 上传 + 通知 Kotlin

    private func uploadAndSend(type: String, url: URL, fileName: String, durationMs: Int64? = nil) {
        guard url.isFileURL, FileManager.default.fileExists(atPath: url.path) else {
            ToastCenter.shared.show("文件不存在")
            return
        }
        ToastCenter.shared.show("上传中...")
        let filePath = url.path
        let api = FoxMusicApi()
        let completion: (String?, Error?) -> Void = { remoteUrl, error in
            DispatchQueue.main.async {
                if let error {
                    ToastCenter.shared.show("上传失败：\(error.localizedDescription)")
                    return
                }
                guard let remoteUrl else {
                    ToastCenter.shared.show("上传失败：无返回地址")
                    return
                }
                FoxChatInputBridge.shared.notifyMediaResult(
                    type: type,
                    url: remoteUrl,
                    durationMs: durationMs.map { KotlinLong(value: $0) },
                    fileName: fileName
                )
            }
        }
        switch type {
        case "image":
            api.uploadImage(filePath: filePath, completionHandler: completion)
        case "video":
            api.uploadVideo(filePath: filePath, completionHandler: completion)
        case "audio":
            api.uploadAudio(filePath: filePath, completionHandler: completion)
        default:
            api.uploadFile(filePath: filePath, completionHandler: completion)
        }
    }
}

// MARK: - UIApplication 辅助

extension UIApplication {
    var topViewController: UIViewController? {
        guard let scene = connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return nil }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        return top
    }
}

// MARK: - PHPicker Delegate

extension ChatMediaPicker: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let result = results.first else { return }
        result.itemProvider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, error in
            guard let url else {
                DispatchQueue.main.async { ToastCenter.shared.show("读取图片失败") }
                return
            }
            // PHPicker 返回的是临时 URL，需要复制到 tmp 持久化
            let dst = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
            do {
                if FileManager.default.fileExists(atPath: dst.path) { try FileManager.default.removeItem(at: dst) }
                try FileManager.default.copyItem(at: url, to: dst)
                DispatchQueue.main.async {
                    self.uploadAndSend(type: "image", url: dst, fileName: dst.lastPathComponent)
                }
            } catch {
                DispatchQueue.main.async { ToastCenter.shared.show("读取图片失败") }
            }
        }
    }
}

// MARK: - UIImagePickerController Delegate

extension ChatMediaPicker: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        let mediaType = info[.mediaType] as? String
        if mediaType == "public.image" {
            guard let image = info[.originalImage] as? UIImage else { return }
            guard let data = image.jpegData(compressionQuality: 0.85) else { return }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("photo_\(Int(Date().timeIntervalSince1970)).jpg")
            do {
                try data.write(to: url)
                uploadAndSend(type: "image", url: url, fileName: url.lastPathComponent)
            } catch {
                ToastCenter.shared.show("保存图片失败")
            }
        } else if mediaType == "public.movie" {
            guard let url = info[.mediaURL] as? URL else { return }
            uploadAndSend(type: "video", url: url, fileName: url.lastPathComponent)
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
}

// MARK: - UIDocumentPicker Delegate

extension ChatMediaPicker: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        let fileName = url.lastPathComponent
        uploadAndSend(type: "file", url: url, fileName: fileName)
    }
}
