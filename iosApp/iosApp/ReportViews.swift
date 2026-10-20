import SwiftUI
import FoxShared

// MARK: - 举报弹窗

/// 举报理由（与 Android ReportBottomSheet.REPORT_REASONS 对齐）。
let REPORT_REASONS: [(key: String, label: String)] = [
    ("spam", "垃圾信息"),
    ("abuse", "辱骂/骚扰"),
    ("porn", "色情内容"),
    ("copyright", "侵权"),
    ("illegal", "违法内容"),
    ("other", "其他"),
]

/// 通用举报弹窗：理由单选 + 可选说明 → 提交 POST api/reports。
/// 使用统一 SheetHeader + 自定义行，与其他底部弹窗风格一致。
struct ReportSheet: View {
    let targetType: String
    let targetId: Int64
    let targetName: String
    let onSubmitted: () -> Void

    @State private var selectedReason = "spam"
    @State private var description = ""
    @State private var submitting = false

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(title: "举报")

            // 举报对象
            Text("举报对象：\(targetName)")
                .font(.system(size: 14))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

            // 理由列表
            ForEach(REPORT_REASONS, id: \.key) { reason in
                Button {
                    selectedReason = reason.key
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: selectedReason == reason.key ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: 18))
                            .foregroundStyle(selectedReason == reason.key ? Color.accentColor : .secondary)
                            .frame(width: 22)
                        Text(reason.label)
                            .font(.system(size: 16))
                            .foregroundStyle(.primary)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 13)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            // 详细说明
            VStack(alignment: .leading, spacing: 8) {
                Text("详细说明（可选）")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
                TextField("请描述具体情况", text: $description, axis: .vertical)
                    .textFieldStyle(.plain)
                    .lineLimit(2...4)
                    .padding(10)
                    .background(Color(.systemGroupedBackground), in: RoundedRectangle(cornerRadius: 8))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            // 提交按钮
            Button {
                submit()
            } label: {
                Text(submitting ? "提交中..." : "提交举报")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(submitting ? Color.accentColor.opacity(0.5) : Color.accentColor, in: RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .disabled(submitting)
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity, alignment: .top)
    }

    private func submit() {
        submitting = true
        FoxMusicApi().submitReport(
            targetType: targetType,
            targetId: targetId,
            reason: selectedReason,
            description: description.isEmpty ? nil : description
        ) { _, error in
            DispatchQueue.main.async {
                submitting = false
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("举报已提交，感谢反馈")
                    onSubmitted()
                    BottomSheet.dismissTop()
                }
            }
        }
    }
}

// MARK: - 举报历史页

/// 举报状态 → 中文 + 颜色（与 Android statusLabelAndColor 对齐）。
private func reportStatusLabel(_ status: String?) -> (String, Color) {
    switch status {
    case "pending": return ("待处理", .orange)
    case "processing": return ("处理中", .blue)
    case "resolved": return ("已处理", .green)
    case "rejected": return ("已驳回", .red)
    default: return (status?.isEmpty == false ? status! : "未知", .gray)
    }
}

private func reportReasonLabel(_ key: String?) -> String {
    REPORT_REASONS.first { $0.key == key }?.label ?? (key ?? "未知")
}

private func reportTargetLabel(_ type: String?) -> String {
    switch type {
    case "music": return "歌曲"
    case "post": return "帖子"
    case "comment", "post-comment", "music-comment": return "评论"
    case "user": return "用户"
    default: return type ?? "未知"
    }
}

/// 举报历史页：分页列表（设置 → 举报历史入口）。
struct ReportHistoryView: View {
    @State private var items: [ReportDto] = []
    @State private var page = 1
    @State private var loading = false
    @State private var hasMore = true
    @State private var errorMessage: String?

    var body: some View {
        List {
            if items.isEmpty && !loading && errorMessage == nil {
                VStack(spacing: 8) {
                    Image(systemName: "flag").font(.largeTitle).foregroundStyle(.secondary)
                    Text("暂无举报记录").foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
            } else if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            } else {
                ForEach(items, id: \.id) { report in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(reportTargetLabel(report.targetType))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                            let (label, color) = reportStatusLabel(report.status)
                            Text(label)
                                .font(.caption)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(color.opacity(0.15), in: Capsule())
                                .foregroundStyle(color)
                        }
                        Text("原因：\(reportReasonLabel(report.reason))")
                            .font(.subheadline)
                        if let desc = report.description_, !desc.isEmpty {
                            Text(desc).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }
                        if let time = report.createdAt, !time.isEmpty {
                            Text(time.replacingOccurrences(of: "T", with: " ").prefix(16).description)
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                    .onAppear {
                        if report.id == items.last?.id && hasMore && !loading { loadMore() }
                    }
                }
                if loading { ProgressView().frame(maxWidth: .infinity).padding() }
            }
        }
        .navigationTitle("举报历史")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { reload() }
        .onAppear { if items.isEmpty { reload() } }
    }

    private func reload() {
        page = 1
        hasMore = true
        items = []
        loadMore()
    }

    private func loadMore() {
        guard !loading && hasMore else { return }
        loading = true
        errorMessage = nil
        FoxMusicApi().fetchReports(page: Int32(page), limit: 20, status: nil) { response, error in
            DispatchQueue.main.async {
                loading = false
                if let error { errorMessage = error.localizedDescription; return }
                guard let response, let list = response.list as? [ReportDto] else { return }
                items.append(contentsOf: list)
                hasMore = list.count >= 20
                page += 1
            }
        }
    }
}
