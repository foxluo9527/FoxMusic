import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:chat —— 聊天/社交消息模块的跨端部分。
//
//   commonMain：12 个文件——纯 Compose 组件（ChatInputBar / EmojiPickerPanel /
//     ChatAttachmentPanel / VoiceHoldButton / UserListItem / MessageCategoryItem /
//     ConversationActionMenu / EmojiData）+ 纯 Kotlin 工具（DisplayName /
//     NotificationFilter / ChatMessageMediaHelper）+ ChatMediaPreviewType enum
//     （从 ChatMediaPreviewDialog 抽出，便于 commonMain 引用）
//   :feature:chat-android：12 个 @HiltViewModel + 全部屏幕外壳（依赖 uCrop / 相机 /
//     权限 xxpermissions / 视频压缩 ffmpeg-kit / Media3 transformer / VoiceRecorder 等
//     android-only 能力）+ ChatRoutes（含 android.net.Uri 编码）
//   注：ConversationItem 引用 core:data.mapper（android.library 非 KMP），留 -android；
//       core:data / core:datastore 均 android-only，不可进 commonMain。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.feature.chat.shared"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder { }.configure { }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:ui"))
            implementation(project(":core:domain"))
            // ConversationItem 用 core.data.mapper 的 previewForMessage / toChatConversation，
            // 这些映射已随批次 31 下沉到 :core:data 的 commonMain（原先在 android-only 层，
            // 因此 ConversationItem 当时只能留在 -android）。
            implementation(project(":core:data"))
            implementation(libs.kotlinx.datetime)

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
