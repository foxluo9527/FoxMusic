# WebSocket 客户端接入文档

## 一、连接配置

### 1.1 连接地址

环境 地址格式 开发环境 ws\://localhost:9000/ws?token={JWT\_TOKEN} 生产环境 ws\://39.106.30.151:9000/ws?token={JWT\_TOKEN}

### 1.2 连接参数

参数 类型 必填 说明 token string 是 用户登录后获取的 JWT Token（需去除 Bearer 前缀）

## 二、客户端集成

### 2.1 初始化连接

```
import { wsService } from '@/utils/
websocket'

// 获取 Token（从 localStorage 或登录
响应中获取）
const token = localStorage.getItem
('token')?.replace('Bearer ', '')

// 建立连接
wsService.connect(token)
```

### 2.2 监听连接状态

```
// 连接成功回调（通过 onopen 内部处理）
wsService.connect(token)

// 检查当前连接状态
const isConnected = wsService.
isConnected()
console.log('WebSocket 连接状态:', 
isConnected)

// 断开连接（用户退出登录时调用）
wsService.disconnect()
```

### 2.3 设置通知处理器

```
// 设置通知横幅处理器
wsService.setNotificationBanner({
  showNotification: (notification) 
  => {
    // 处理收到的通知
    console.log('收到通知:', 
    notification)
    // 显示通知 UI 或触发业务逻辑
  }
})
```

## 三、数据结构

### 3.1 通知消息格式

```
interface Notification {
  id: number                    // 
  通知唯一标识
  user_id: number               // 
  目标用户 ID
  type: string                  // 
  通知类型（见 3.2 节）
  title: string                 // 
  通知标题
  content: string               // 
  通知内容
  target_type: string | null    // 
  关联资源类型（post/comment/music 
  等）
  target_id: number | null      // 
  关联资源 ID
  is_read: number               // 
  是否已读（0=未读，1=已读）
  is_pushed: number             // 
  是否已推送（0=未推送，1=已推送）
  created_at: string            // 
  创建时间（ISO 格式）
  updated_at: string            // 
  更新时间（ISO 格式）
}
```

### 3.2 通知类型列表

类型 标题 触发场景 comment 新评论提醒 他人评论了用户的内容 like 点赞提醒 他人点赞了用户的内容 follow 关注提醒 有新用户关注 mention 提及提醒 用户被 @ 提及 chat 聊天消息提醒 收到新的聊天消息 system 系统通知 系统公告或重要消息

### 3.3 消息接收示例

```
// 收到的原始消息（JSON 字符串）
{
  "id": 123,
  "user_id": 456,
  "type": "comment",
  "title": "新评论提醒",
  "content": "用户「张三」评论了你的帖
  子：这是一个很棒的分享！",
  "target_type": "post",
  "target_id": 789,
  "is_read": 0,
  "is_pushed": 1,
  "created_at": 
  "2024-01-15T10:30:00Z",
  "updated_at": 
  "2024-01-15T10:30:00Z"
}
```

## 四、断线重连机制

### 4.1 重连策略

场景 行为 网络异常断开 自动触发重连 服务端主动断开 自动触发重连 客户端主动断开 不重连

### 4.2 重连参数

参数 值 说明 最大重试次数 5 次 超过次数后停止重连 重试间隔 3000ms 每次重试间隔时间

### 4.3 重连状态监听

```
// 通过 onclose 事件判断是否需要手动处理
// 服务端心跳超时会触发 close 事件
（code: 1006）
// 客户端可在此时做降级处理或提示用户
```

## 五、数据场景覆盖

### 5.1 在线通知推送

当用户在线时，服务端实时推送通知：

```
用户 A 评论用户 B 的帖子
    ↓
服务端收到评论请求
    ↓
检查用户 B 是否在线
    ↓
在线 → 直接推送 WebSocket 消息
离线 → 存储到数据库，待上线时推送
```

### 5.2 离线通知补发

用户重新连接时，服务端自动补发未推送的通知：

```
// 连接成功后，服务端自动发送未推送通知
// 客户端无需额外请求，直接通过 
onmessage 接收
```

### 5.3 多端同步

支持同一用户多设备同时在线：

```
用户在手机和电脑同时登录
    ↓
服务端为每个连接维护独立的 socket
    ↓
通知会发送到所有在线连接
    ↓
客户端各自处理通知展示
```

## 六、API 接口配合

### 6.1 获取通知列表

接口 ： GET /api/notifications

请求参数 ：

参数 类型 必填 默认值 page number 否 1 limit number 否 20

响应示例 ：

```
{
  "success": true,
  "data": {
    "list": [
      {
        "id": 123,
        "user_id": 456,
        "type": "comment",
        "title": "新评论提醒",
        "content": "用户「张三」评论了
        你的帖子",
        "target_type": "post",
        "target_id": 789,
        "is_read": 0,
        "is_pushed": 1,
        "created_at": 
        "2024-01-15T10:30:00Z",
        "updated_at": 
        "2024-01-15T10:30:00Z"
      }
    ],
    "total": 50,
    "current": 1,
    "pageSize": 20,
    "totalPages": 3
  }
}
```

### 6.2 标记通知已读

接口 ： PUT /api/notifications/read

请求体 ：

```
{
  "ids": [123, 124, 125]
}
```

### 6.3 标记全部已读

接口 ： PUT /api/notifications/read-all

### 6.4 删除通知

接口 ： DELETE /api/notifications

请求体 ：

```
{
  "ids": [123, 124]
}
```

## 七、注意事项

### 7.1 Token 处理

```
// 错误：保留了 Bearer 前缀
const token = localStorage.getItem
('token')  // "Bearer xxx.xxx.xxx"

// 正确：去除 Bearer 前缀
const token = localStorage.getItem
('token')?.replace('Bearer ', 
'')  // "xxx.xxx.xxx"
```

### 7.2 连接时机

- 推荐时机 ：用户登录成功后立即连接
- 避免时机 ：应用启动时未登录状态下连接

### 7.3 资源释放

```
// 用户退出登录时必须断开连接
logout() {
  wsService.disconnect()
  localStorage.removeItem('token')
}
```

### 7.4 消息解析异常处理

```
// 服务端发送的消息可能不是 JSON 格式，需
处理解析异常
wsService.setNotificationBanner({
  showNotification: (data) => {
    try {
      const notification = typeof 
      data === 'string' ? JSON.parse
      (data) : data
      // 处理通知
    } catch (error) {
      console.error('通知解析失败:', 
      error)
    }
  }
})
```

### 7.5 网络环境适配

场景 处理策略 网络切换 依赖自动重连机制 弱网环境 消息可能延迟，需显示加载状态 跨域问题 服务端已配置 CORS，无需额外处理

### 7.6 浏览器兼容性

浏览器 支持情况 Chrome ✅ 支持 Firefox ✅ 支持 Safari ✅ 支持 Edge ✅ 支持 IE 11 ⚠️ 部分支持，建议降级为轮询

## 八、完整集成示例

```
import { wsService } from '@/utils/
websocket'
import { getToken } from '@/utils/
auth'

class NotificationManager {
  constructor() {
    this.init()
  }

  private init() {
    // 获取 Token
    const token = getToken()
    
    if (!token) {
      console.warn('未登录，跳过 
      WebSocket 连接')
      return
    }

    // 设置通知处理器
    wsService.setNotificationBanner
    ({
      showNotification: this.
      handleNotification.bind(this)
    })

    // 建立连接
    wsService.connect(token)
  }

  private handleNotification
  (notification: Notification) {
    // 根据通知类型处理
    switch (notification.type) {
      case 'comment':
        this.showCommentNotification
        (notification)
        break
      case 'like':
        this.showLikeNotification
        (notification)
        break
      case 'follow':
        this.showFollowNotification
        (notification)
        break
      default:
        this.showDefaultNotification
        (notification)
    }
  }

  private showCommentNotification
  (notification: Notification) {
    // 显示评论通知 UI
    console.log('新评论:', 
    notification.content)
  }

  private showLikeNotification
  (notification: Notification) {
    // 显示点赞通知 UI
    console.log('新点赞:', 
    notification.content)
  }

  private showFollowNotification
  (notification: Notification) {
    // 显示关注通知 UI
    console.log('新关注:', 
    notification.content)
  }

  private showDefaultNotification
  (notification: Notification) {
    // 显示通用通知 UI
    console.log('新通知:', 
    notification.title, 
    notification.content)
  }

  public disconnect() {
    wsService.disconnect()
  }
}

// 使用示例
const notificationManager = new 
NotificationManager()

// 退出登录时断开连接
// notificationManager.disconnect()
```

文档版本 ：v1.0
生成日期 ：2026-06-01
适用场景 ：客户端 WebSocket 集成开发
