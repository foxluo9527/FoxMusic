---
title: Fox Admin API Documentation
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
code_clipboard: true
highlight_theme: darkula
headingLevel: 2
generator: "@tarslib/widdershins v4.0.30"

---

# Fox Admin API Documentation

Fox Admin 系统 API 文档

Base URLs:

* <a href="http://39.106.30.151:9000">线上环境: http://39.106.30.151:9000</a>

Email: <a href="mailto:admin@example.com">Fox Admin Team</a> 

# Authentication

- HTTP Authentication, scheme: bearer

# Default

## POST 批量删除音乐

POST /api/admin/music/batchDelete

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

# 客户端/账户

## POST 申请注销账号

POST /api/account/deletion

提交账号注销申请，系统会发送验证码到用户邮箱

> Body 请求参数

```json
{
  "reason": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» reason|body|string| 是 |注销原因|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "message": "注销申请已提交，请查收邮箱验证码",
    "scheduledDate": "2019-08-24T14:15:22Z",
    "deletionId": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|申请提交成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误或已有注销申请|None|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|未登录或token已过期|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» message|string|false|none||none|
|»» scheduledDate|string(date-time)|false|none||none|
|»» deletionId|number|false|none||none|

## POST 确认注销账号

POST /api/account/deletion/confirm

使用邮箱验证码确认注销申请

> Body 请求参数

```json
{
  "deletionId": 0,
  "verificationCode": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» deletionId|body|number| 是 |注销申请ID|
|» verificationCode|body|string| 是 |邮箱验证码|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "message": "注销确认成功，账号将在预定日期注销",
    "scheduledDate": "2019-08-24T14:15:22Z"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|确认成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|验证码错误或已过期|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|注销申请不存在或已处理|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» message|string|false|none||none|
|»» scheduledDate|string(date-time)|false|none||none|

## POST 取消注销申请

POST /api/account/deletion/cancel

取消正在处理中的注销申请

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "message": "注销申请已取消"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|取消成功|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|无正在处理的注销申请|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» message|string|false|none||none|

## POST 用户登录

POST /api/auth/login

> Body 请求参数

```json
{
  "username": "string",
  "password": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» username|body|string| 是 |none|
|» password|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "string",
    "user": {
      "id": 0,
      "username": "string",
      "email": "string",
      "role": "string",
      "status": "string",
      "signature": "string",
      "created_at": "2019-08-24T14:15:22Z",
      "last_login": "2019-08-24T14:15:22Z"
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|登录成功|Inline|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|用户名/邮箱错误或密码错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» token|string|false|none||none|
|»» user|object|false|none||none|
|»»» id|number|false|none||none|
|»»» username|string|false|none||none|
|»»» email|string|false|none||none|
|»»» role|string|false|none||none|
|»»» status|string|false|none||none|
|»»» signature|string|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» last_login|string(date-time)|false|none||none|

## POST 用户注册

POST /api/auth/register

> Body 请求参数

```json
{
  "username": "string",
  "password": "string",
  "email": "user@example.com"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» username|body|string| 是 |none|
|» password|body|string| 是 |none|
|» email|body|string(email)| 是 |none|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "token": "string",
    "user": {
      "id": 0,
      "username": "string",
      "email": "string",
      "role": "string",
      "status": "string",
      "created_at": "2019-08-24T14:15:22Z"
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|注册成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» token|string|false|none||none|
|»» user|object|false|none||none|
|»»» id|number|false|none||none|
|»»» username|string|false|none||none|
|»»» email|string|false|none||none|
|»»» role|string|false|none||none|
|»»» status|string|false|none||none|
|»»» created_at|string(date-time)|false|none||none|

## POST 刷新 token

POST /api/auth/refresh-token

> 返回示例

> 200 Response

```json
{
  "token": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|刷新成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» token|string|false|none||none|

## POST 用户登出

POST /api/auth/logout

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|登出成功|None|

## GET 获取用户信息

GET /api/auth/profile

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "获取用户信息成功",
  "data": {
    "id": 0,
    "username": "string",
    "email": "string",
    "role": "string",
    "status": "string",
    "nickname": "string",
    "avatar": "string",
    "signature": "string",
    "created_at": "2019-08-24T14:15:22Z",
    "updated_at": "2019-08-24T14:15:22Z",
    "last_login": "2019-08-24T14:15:22Z"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取用户信息|Inline|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|未授权|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|用户不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» id|number|false|none||none|
|»» username|string|false|none||none|
|»» email|string|false|none||none|
|»» role|string|false|none||none|
|»» status|string|false|none||none|
|»» nickname|string|false|none||none|
|»» avatar|string|false|none||none|
|»» signature|string|false|none||none|
|»» created_at|string(date-time)|false|none||none|
|»» updated_at|string(date-time)|false|none||none|
|»» last_login|string(date-time)|false|none||none|

## PUT 更新用户信息

PUT /api/auth/profile

> Body 请求参数

```json
{
  "nickname": "string",
  "avatar": "string",
  "signature": "string",
  "email": "user@example.com"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» nickname|body|string| 否 |none|
|» avatar|body|string| 否 |none|
|» signature|body|string| 否 |none|
|» email|body|string(email)| 否 |none|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "更新个人资料成功",
  "data": {
    "id": 0,
    "username": "string",
    "email": "string",
    "role": "string",
    "status": "string",
    "nickname": "string",
    "avatar": "string",
    "signature": "string",
    "created_at": "2019-08-24T14:15:22Z",
    "updated_at": "2019-08-24T14:15:22Z",
    "last_login": "2019-08-24T14:15:22Z"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|更新成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|没有要更新的字段|None|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|未授权|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» id|number|false|none||none|
|»» username|string|false|none||none|
|»» email|string|false|none||none|
|»» role|string|false|none||none|
|»» status|string|false|none||none|
|»» nickname|string|false|none||none|
|»» avatar|string|false|none||none|
|»» signature|string|false|none||none|
|»» created_at|string(date-time)|false|none||none|
|»» updated_at|string(date-time)|false|none||none|
|»» last_login|string(date-time)|false|none||none|

## POST 修改密码

POST /api/auth/change-password

> Body 请求参数

```json
{
  "newPassword": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» newPassword|body|string| 是 |新密码|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|密码修改成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误或新密码长度不足|None|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|未授权|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|用户不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## POST 请求重置密码

POST /api/auth/forgot-password

> Body 请求参数

```json
{
  "email": "user@example.com"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» email|body|string(email)| 是 |none|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "重置密码邮件已发送，请查收",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|重置密码邮件已发送|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|用户不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## POST 重置密码

POST /api/auth/reset-password

> Body 请求参数

```json
{
  "email": "user@example.com",
  "code": "string",
  "newPassword": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» email|body|string(email)| 是 |none|
|» code|body|string| 是 |none|
|» newPassword|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|密码重置成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|验证码错误或已过期|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|用户不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

# 客户端/资源/音乐

## GET 获取音乐列表

GET /api/music

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|keyword|query|string| 否 |搜索关键词|
|tag_id|query|integer| 否 |标签ID（单个）|
|sort|query|string| 否 |排序方式（latest: 最新, hot: 热门, recommend: 推荐）|

#### 枚举值

|属性|值|
|---|---|
|sort|latest|
|sort|hot|
|sort|recommend|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取音乐列表|None|

## GET 获取音乐详情

GET /api/music/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |音乐ID|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取音乐详情|None|

## POST 收藏/取消收藏音乐

POST /api/music/{id}/favorite

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |音乐ID|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|None|

## POST 记录播放历史

POST /api/music/{id}/play

> Body 请求参数

```json
{
  "duration": 0,
  "progress": 100
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |音乐ID|
|body|body|object| 是 |none|
|» duration|body|integer| 否 |播放时长（秒）|
|» progress|body|number| 否 |播放进度（0-100的百分比）|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|记录成功|None|

# 客户端/资源/音乐/专辑

## GET 获取专辑列表

GET /api/albums

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|keyword|query|string| 否 |搜索关键词|
|artist_id|query|integer| 否 |艺术家ID|
|sort|query|string| 否 |排序方式|

#### 枚举值

|属性|值|
|---|---|
|sort|latest|
|sort|hot|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 0,
        "title": "string",
        "cover_image": "string",
        "description": "string",
        "release_date": "2019-08-24",
        "type": "string",
        "language": "string",
        "publisher": "string",
        "creator_id": 0,
        "is_public": true,
        "duration": 0,
        "track_count": 0,
        "view_count": 0,
        "like_count": 0,
        "collection_count": 0,
        "is_featured": true,
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z",
        "favorite_count": 0,
        "play_count": 0,
        "isFavorite": true,
        "artists": [
          {
            "id": null,
            "name": null,
            "avatar": null,
            "cover_image": null
          }
        ]
      }
    ],
    "total": 0,
    "current": 0,
    "pageSize": 0,
    "totalPages": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取专辑列表|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» list|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»»» description|string|false|none||none|
|»»» release_date|string(date)|false|none||none|
|»»» type|string|false|none||none|
|»»» language|string|false|none||none|
|»»» publisher|string|false|none||none|
|»»» creator_id|number|false|none||none|
|»»» is_public|boolean|false|none||none|
|»»» duration|number|false|none||none|
|»»» track_count|number|false|none||none|
|»»» view_count|number|false|none||none|
|»»» like_count|number|false|none||none|
|»»» collection_count|number|false|none||none|
|»»» is_featured|boolean|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»»» favorite_count|number|false|none||none|
|»»» play_count|number|false|none||none|
|»»» isFavorite|boolean|false|none||none|
|»»» artists|[object]|false|none||none|
|»»»» id|number|false|none||none|
|»»»» name|string|false|none||none|
|»»»» avatar|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»» total|number|false|none||none|
|»» current|number|false|none||none|
|»» pageSize|number|false|none||none|
|»» totalPages|number|false|none||none|

## GET 获取专辑详情

GET /api/albums/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |专辑ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 0,
    "title": "string",
    "cover_image": "string",
    "description": "string",
    "release_date": "2019-08-24",
    "type": "string",
    "language": "string",
    "publisher": "string",
    "creator_id": 0,
    "is_public": true,
    "duration": 0,
    "track_count": 0,
    "view_count": 0,
    "like_count": 0,
    "collection_count": 0,
    "is_featured": true,
    "created_at": "2019-08-24T14:15:22Z",
    "updated_at": "2019-08-24T14:15:22Z",
    "favorite_count": 0,
    "play_count": 0,
    "isFavorite": true,
    "artists": [
      {
        "id": 0,
        "name": "string",
        "avatar": "string",
        "cover_image": "string"
      }
    ],
    "tracks": {
      "list": [
        {
          "id": 0,
          "title": "string",
          "description": "string",
          "url": "string",
          "cover_image": "string",
          "duration": 0,
          "track_number": 0,
          "disc_number": 0,
          "genre": "string",
          "language": "string",
          "lyrics": "string",
          "lyrics_trans": "string",
          "lyrics_url": "string",
          "play_count": 0,
          "like_count": 0,
          "comment_count": 0,
          "collection_count": 0,
          "avg_play_progress": 0,
          "completion_rate": 0,
          "is_explicit": true,
          "is_featured": true,
          "created_by": 0,
          "created_at": "2019-08-24T14:15:22Z",
          "updated_at": "2019-08-24T14:15:22Z",
          "isFavorite": true,
          "tags": [
            null
          ]
        }
      ],
      "total": 0,
      "current": 0,
      "pageSize": 0,
      "totalPages": 0
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取专辑详情|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|专辑不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» id|number|false|none||none|
|»» title|string|false|none||none|
|»» cover_image|string|false|none||none|
|»» description|string|false|none||none|
|»» release_date|string(date)|false|none||none|
|»» type|string|false|none||none|
|»» language|string|false|none||none|
|»» publisher|string|false|none||none|
|»» creator_id|number|false|none||none|
|»» is_public|boolean|false|none||none|
|»» duration|number|false|none||none|
|»» track_count|number|false|none||none|
|»» view_count|number|false|none||none|
|»» like_count|number|false|none||none|
|»» collection_count|number|false|none||none|
|»» is_featured|boolean|false|none||none|
|»» created_at|string(date-time)|false|none||none|
|»» updated_at|string(date-time)|false|none||none|
|»» favorite_count|number|false|none||none|
|»» play_count|number|false|none||none|
|»» isFavorite|boolean|false|none||none|
|»» artists|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» name|string|false|none||none|
|»»» avatar|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»» tracks|object|false|none||none|
|»»» list|[object]|false|none||none|
|»»»» id|number|false|none||none|
|»»»» title|string|false|none||none|
|»»»» description|string|false|none||none|
|»»»» url|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»»»» duration|number|false|none||none|
|»»»» track_number|number|false|none||none|
|»»»» disc_number|number|false|none||none|
|»»»» genre|string|false|none||none|
|»»»» language|string|false|none||none|
|»»»» lyrics|string|false|none||none|
|»»»» lyrics_trans|string|false|none||none|
|»»»» lyrics_url|string|false|none||none|
|»»»» play_count|number|false|none||none|
|»»»» like_count|number|false|none||none|
|»»»» comment_count|number|false|none||none|
|»»»» collection_count|number|false|none||none|
|»»»» avg_play_progress|number|false|none||none|
|»»»» completion_rate|number|false|none||none|
|»»»» is_explicit|boolean|false|none||none|
|»»»» is_featured|boolean|false|none||none|
|»»»» created_by|number|false|none||none|
|»»»» created_at|string(date-time)|false|none||none|
|»»»» updated_at|string(date-time)|false|none||none|
|»»»» isFavorite|boolean|false|none||none|
|»»»» tags|[object]|false|none||none|
|»»»»» id|number|false|none||none|
|»»»»» name|string|false|none||none|
|»»»»» type|string|false|none||none|
|»»»»» category|string|false|none||none|
|»»» total|number|false|none||none|
|»»» current|number|false|none||none|
|»»» pageSize|number|false|none||none|
|»»» totalPages|number|false|none||none|

## POST 收藏/取消收藏专辑

POST /api/albums/{id}/favorite

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |专辑ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "收藏成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

# 客户端/资源/音乐/艺术家

## GET 获取艺术家列表

GET /api/artists

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|keyword|query|string| 否 |搜索关键词|
|tag_id|query|integer| 否 |标签ID|
|sort|query|string| 否 |排序方式|

#### 枚举值

|属性|值|
|---|---|
|sort|latest|
|sort|hot|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 0,
        "name": "string",
        "alias": "string",
        "avatar": "string",
        "cover_image": "string",
        "description": "string",
        "region": "string",
        "birth_date": "2019-08-24",
        "gender": "string",
        "debut_date": "2019-08-24",
        "view_count": 0,
        "favorite_count": 0,
        "is_verified": true,
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z",
        "music_count": 0,
        "album_count": 0,
        "isFavorite": true,
        "tags": [
          {
            "id": null,
            "name": null,
            "type": null,
            "category": null
          }
        ]
      }
    ],
    "total": 0,
    "current": 0,
    "pageSize": 0,
    "totalPages": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取艺术家列表|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» list|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» name|string|false|none||none|
|»»» alias|string|false|none||none|
|»»» avatar|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»»» description|string|false|none||none|
|»»» region|string|false|none||none|
|»»» birth_date|string(date)|false|none||none|
|»»» gender|string|false|none||none|
|»»» debut_date|string(date)|false|none||none|
|»»» view_count|number|false|none||none|
|»»» favorite_count|number|false|none||none|
|»»» is_verified|boolean|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»»» music_count|number|false|none||none|
|»»» album_count|number|false|none||none|
|»»» isFavorite|boolean|false|none||none|
|»»» tags|[object]|false|none||none|
|»»»» id|number|false|none||none|
|»»»» name|string|false|none||none|
|»»»» type|string|false|none||none|
|»»»» category|string|false|none||none|
|»» total|number|false|none||none|
|»» current|number|false|none||none|
|»» pageSize|number|false|none||none|
|»» totalPages|number|false|none||none|

## GET 获取艺术家详情

GET /api/artists/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |艺术家ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 0,
    "name": "string",
    "alias": "string",
    "avatar": "string",
    "cover_image": "string",
    "description": "string",
    "region": "string",
    "birth_date": "2019-08-24",
    "gender": "string",
    "debut_date": "2019-08-24",
    "view_count": 0,
    "favorite_count": 0,
    "is_verified": true,
    "created_at": "2019-08-24T14:15:22Z",
    "updated_at": "2019-08-24T14:15:22Z",
    "music_count": 0,
    "album_count": 0,
    "isFavorite": true,
    "tags": [
      {
        "id": 0,
        "name": "string",
        "type": "string",
        "category": "string"
      }
    ],
    "hotMusics": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "url": "string",
        "cover_image": "string",
        "duration": 0,
        "play_count": 0,
        "like_count": 0,
        "comment_count": 0,
        "collection_count": 0,
        "is_explicit": true,
        "is_featured": true,
        "created_by": 0,
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z",
        "isFavorite": true
      }
    ],
    "albums": [
      {
        "id": 0,
        "title": "string",
        "cover_image": "string",
        "description": "string",
        "release_date": "2019-08-24",
        "type": "string",
        "language": "string",
        "publisher": "string",
        "creator_id": 0,
        "is_public": true,
        "duration": 0,
        "track_count": 0,
        "view_count": 0,
        "like_count": 0,
        "collection_count": 0,
        "is_featured": true,
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z",
        "favorite_count": 0,
        "isFavorite": true
      }
    ]
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取艺术家详情|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|艺术家不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» id|number|false|none||none|
|»» name|string|false|none||none|
|»» alias|string|false|none||none|
|»» avatar|string|false|none||none|
|»» cover_image|string|false|none||none|
|»» description|string|false|none||none|
|»» region|string|false|none||none|
|»» birth_date|string(date)|false|none||none|
|»» gender|string|false|none||none|
|»» debut_date|string(date)|false|none||none|
|»» view_count|number|false|none||none|
|»» favorite_count|number|false|none||none|
|»» is_verified|boolean|false|none||none|
|»» created_at|string(date-time)|false|none||none|
|»» updated_at|string(date-time)|false|none||none|
|»» music_count|number|false|none||none|
|»» album_count|number|false|none||none|
|»» isFavorite|boolean|false|none||none|
|»» tags|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» name|string|false|none||none|
|»»» type|string|false|none||none|
|»»» category|string|false|none||none|
|»» hotMusics|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» description|string|false|none||none|
|»»» url|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»»» duration|number|false|none||none|
|»»» play_count|number|false|none||none|
|»»» like_count|number|false|none||none|
|»»» comment_count|number|false|none||none|
|»»» collection_count|number|false|none||none|
|»»» is_explicit|boolean|false|none||none|
|»»» is_featured|boolean|false|none||none|
|»»» created_by|number|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»»» isFavorite|boolean|false|none||none|
|»» albums|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»»» description|string|false|none||none|
|»»» release_date|string(date)|false|none||none|
|»»» type|string|false|none||none|
|»»» language|string|false|none||none|
|»»» publisher|string|false|none||none|
|»»» creator_id|number|false|none||none|
|»»» is_public|boolean|false|none||none|
|»»» duration|number|false|none||none|
|»»» track_count|number|false|none||none|
|»»» view_count|number|false|none||none|
|»»» like_count|number|false|none||none|
|»»» collection_count|number|false|none||none|
|»»» is_featured|boolean|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»»» favorite_count|number|false|none||none|
|»»» isFavorite|boolean|false|none||none|

## GET 获取艺术家的音乐列表

GET /api/artists/{id}/musics

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |艺术家ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|sort|query|string| 否 |排序方式|

#### 枚举值

|属性|值|
|---|---|
|sort|latest|
|sort|hot|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "url": "string",
        "cover_image": "string",
        "duration": 0,
        "genre": "string",
        "language": "string",
        "lyrics": "string",
        "lyrics_trans": "string",
        "lyrics_url": "string",
        "play_count": 0,
        "like_count": 0,
        "comment_count": 0,
        "collection_count": 0,
        "avg_play_progress": 0,
        "completion_rate": 0,
        "is_explicit": true,
        "is_featured": true,
        "created_by": 0,
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z",
        "isFavorite": true,
        "tags": [
          {
            "id": null,
            "name": null,
            "type": null,
            "category": null
          }
        ]
      }
    ],
    "total": 0,
    "current": 0,
    "pageSize": 0,
    "totalPages": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取音乐列表|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» list|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» description|string|false|none||none|
|»»» url|string|false|none||none|
|»»» cover_image|string|false|none||none|
|»»» duration|number|false|none||none|
|»»» genre|string|false|none||none|
|»»» language|string|false|none||none|
|»»» lyrics|string|false|none||none|
|»»» lyrics_trans|string|false|none||none|
|»»» lyrics_url|string|false|none||none|
|»»» play_count|number|false|none||none|
|»»» like_count|number|false|none||none|
|»»» comment_count|number|false|none||none|
|»»» collection_count|number|false|none||none|
|»»» avg_play_progress|number|false|none||none|
|»»» completion_rate|number|false|none||none|
|»»» is_explicit|boolean|false|none||none|
|»»» is_featured|boolean|false|none||none|
|»»» created_by|number|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»»» isFavorite|boolean|false|none||none|
|»»» tags|[object]|false|none||none|
|»»»» id|number|false|none||none|
|»»»» name|string|false|none||none|
|»»»» type|string|false|none||none|
|»»»» category|string|false|none||none|
|»» total|number|false|none||none|
|»» current|number|false|none||none|
|»» pageSize|number|false|none||none|
|»» totalPages|number|false|none||none|

## POST 收藏/取消收藏艺术家

POST /api/artists/{id}/favorite

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |艺术家ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "收藏成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

# 客户端/资源/音乐/评论

## GET 获取音乐评论列表

GET /api/music-comments

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|music_id|query|integer| 是 |音乐ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 发表评论

POST /api/music-comments

> Body 请求参数

```json
{
  "music_id": 0,
  "content": "string",
  "parent_id": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» music_id|body|integer| 是 |音乐ID|
|» content|body|string| 是 |评论内容|
|» parent_id|body|integer| 否 |父评论ID（回复时需要）|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取评论的回复列表

GET /api/music-comments/{id}/replies

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |评论ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## DELETE 删除评论

DELETE /api/music-comments/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |评论ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

# 客户端/资源/音乐/播放历史

## GET 获取播放历史列表

GET /api/music-history

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

## DELETE 删除指定音乐的播放历史

DELETE /api/music-history

> Body 请求参数

```json
{
  "musicIds": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» musicIds|body|[integer]| 是 |要删除的音乐ID列表|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|删除成功|None|

# 客户端/资源/音乐/歌单分类

## GET 获取歌单分类列表

GET /api/playlist-categories

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|categoryType|query|string| 否 |分类类型筛选|

#### 枚举值

|属性|值|
|---|---|
|categoryType|recommended|
|categoryType|fixed|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取分类列表|None|

## GET 获取推荐分类列表

GET /api/playlist-categories/recommended

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取推荐分类列表|None|

## GET 获取固定分类列表

GET /api/playlist-categories/fixed

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取固定分类列表|None|

## GET 获取分类下的歌单列表

GET /api/playlist-categories/{id}/playlists

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |分类ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取分类下的歌单列表|None|

## GET 获取分类详情

GET /api/playlist-categories/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |分类ID|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取分类详情|None|

# 客户端/资源/音乐/歌单

## GET 获取用户歌单列表

GET /api/playlists

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|user_id|query|integer| 否 |用户ID（不传则获取当前用户的歌单）|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取歌单列表|None|

## POST 创建歌单

POST /api/playlists

> Body 请求参数

```json
{
  "title": "string",
  "description": "string",
  "cover_image": "string",
  "is_public": true,
  "tags": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» title|body|string| 是 |none|
|» description|body|string| 否 |none|
|» cover_image|body|string| 否 |none|
|» is_public|body|boolean| 否 |none|
|» tags|body|[integer]| 否 |标签ID列表（最多3个）|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|创建成功|None|

## PUT 更新歌单信息

PUT /api/playlists/{id}

> Body 请求参数

```json
{
  "title": "string",
  "description": "string",
  "cover_image": "string",
  "is_public": true,
  "tags": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|
|body|body|object| 是 |none|
|» title|body|string| 否 |none|
|» description|body|string| 否 |none|
|» cover_image|body|string| 否 |none|
|» is_public|body|boolean| 否 |none|
|» tags|body|[integer]| 否 |标签ID列表（最多3个）|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|更新成功|None|

## GET 获取歌单详情

GET /api/playlists/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取歌单详情|None|

## DELETE 删除歌单

DELETE /api/playlists/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|删除成功|None|

## GET 获取推荐歌单

GET /api/playlists/recommended

根据播放量和收藏量排序获取推荐的公开歌单

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取推荐歌单|None|

## POST 向歌单批量添加音乐

POST /api/playlists/{id}/tracks

> Body 请求参数

```json
{
  "musicIds": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|
|body|body|object| 是 |none|
|» musicIds|body|[integer]| 是 |音乐ID列表|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|添加成功|None|

## DELETE 从歌单中移除音乐

DELETE /api/playlists/{id}/tracks/{musicId}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|
|musicId|path|integer| 是 |音乐ID|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|移除成功|None|

## DELETE 从歌单中批量移除音乐

DELETE /api/playlists/{id}/batch/tracks

> Body 请求参数

```json
{
  "musicIds": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |歌单ID|
|body|body|object| 是 |none|
|» musicIds|body|[integer]| 是 |音乐ID列表|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|批量移除成功|None|

# 客户端/资源/音乐/标签

## GET 获取音乐标签列表

GET /api/tags/music

获取所有活跃的音乐标签

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取音乐标签列表|None|

# 客户端/资源/音乐/导入

## POST 导入音乐（歌单/专辑）

POST /api/import/music

> Body 请求参数

```json
{
  "url": "string",
  "platform": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» url|body|string| 否 |音乐链接（网易云/QQ音乐歌单/专辑）|
|» platform|body|string| 否 |平台类型|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "albumId": 0,
    "taskId": "string",
    "isImporting": true
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» albumId|number|false|none||专辑ID|
|»» taskId|string|false|none||任务ID|
|»» isImporting|boolean|false|none||是否正在导入|
|» message|string|false|none||none|

## POST 导入所有网易云排行榜

POST /api/import/netease/charts

导入所有网易云音乐排行榜

> Body 请求参数

```json
{
  "platform": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 否 |none|
|» platform|body|string| 否 |平台类型|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "success": true,
        "chartId": 0,
        "chartName": "string",
        "albumId": 0,
        "error": "string"
      }
    ],
    "total": 0,
    "successCount": 0,
    "failedCount": 0
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» results|[object]|false|none||none|
|»»» success|boolean|false|none||是否成功|
|»»» chartId|number|false|none||排行榜ID|
|»»» chartName|string|false|none||排行榜名称|
|»»» albumId|number|false|none||专辑ID|
|»»» error|string|false|none||错误信息|
|»» total|number|false|none||总任务数|
|»» successCount|number|false|none||成功任务数|
|»» failedCount|number|false|none||失败任务数|
|» message|string|false|none||none|

# 客户端/收藏列表

## GET 获取收藏列表

GET /api/favorites

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|query|string| 否 |收藏类型|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|
|type|post|
|type|artist|
|type|album|
|type|playlist|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 0,
        "user_id": 0,
        "type": "music",
        "target_id": 0,
        "title": "string",
        "created_at": "2019-08-24T14:15:22Z",
        "updated_at": "2019-08-24T14:15:22Z"
      }
    ],
    "total": 0,
    "current": 0,
    "pageSize": 0,
    "totalPages": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取收藏列表|Inline|
|401|[Unauthorized](https://tools.ietf.org/html/rfc7235#section-3.1)|未授权|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» list|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» user_id|number|false|none||none|
|»»» type|string|false|none||none|
|»»» target_id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» created_at|string(date-time)|false|none||none|
|»»» updated_at|string(date-time)|false|none||none|
|»» total|number|false|none||none|
|»» current|number|false|none||none|
|»» pageSize|number|false|none||none|
|»» totalPages|number|false|none||none|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|
|type|post|
|type|artist|
|type|album|
|type|playlist|

# 客户端/好友

## GET 获取好友列表

GET /api/friends

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "获取好友列表成功",
  "data": [
    {
      "id": 0,
      "username": "string",
      "nickname": "string",
      "avatar": "string",
      "signature": "string",
      "mark": "string"
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取好友列表|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|[object]|false|none||none|
|»» id|number|false|none||none|
|»» username|string|false|none||none|
|»» nickname|string|false|none||none|
|»» avatar|string|false|none||none|
|»» signature|string|false|none||none|
|»» mark|string|false|none||none|

## GET 获取好友申请列表

GET /api/friends/requests

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "获取好友申请列表成功",
  "data": [
    {
      "id": 0,
      "user_id": 0,
      "created_at": "2019-08-24T14:15:22Z",
      "message": "string",
      "nickname": "string",
      "avatar": "string",
      "signature": "string"
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功获取好友申请列表|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|[object]|false|none||none|
|»» id|number|false|none||none|
|»» user_id|number|false|none||none|
|»» created_at|string(date-time)|false|none||none|
|»» message|string|false|none||none|
|»» nickname|string|false|none||none|
|»» avatar|string|false|none||none|
|»» signature|string|false|none||none|

## GET 搜索用户信息

GET /api/friends/search

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|keyword|query|string| 是 |搜索关键词（用户名/昵称）|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "搜索用户成功",
  "data": [
    {
      "id": 0,
      "nickname": "string",
      "avatar": "string",
      "signature": "string",
      "mark": "string",
      "is_requested": true,
      "is_friend": true
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功搜索用户信息|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|[object]|false|none||none|
|»» id|number|false|none||none|
|»» nickname|string|false|none||none|
|»» avatar|string|false|none||none|
|»» signature|string|false|none||none|
|»» mark|string|false|none||none|
|»» is_requested|boolean|false|none||none|
|»» is_friend|boolean|false|none||none|

## POST 发送好友申请

POST /api/friends/request

> Body 请求参数

```json
{
  "friend_id": 0,
  "message": "string",
  "mark": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» friend_id|body|integer| 是 |接收方用户ID|
|» message|body|string| 是 |申请消息|
|» mark|body|string| 否 |好友备注|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "好友申请发送成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|好友申请发送成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|不能添加自己为好友或已存在未处理的好友申请|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## POST 接受好友申请

POST /api/friends/accept

> Body 请求参数

```json
{
  "requestId": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» requestId|body|integer| 是 |好友申请ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "好友添加成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|好友申请接受成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|无效的好友申请|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## DELETE 删除好友

DELETE /api/friends/{friendId}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|friendId|path|integer| 是 |要删除的好友ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "好友删除成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|好友删除成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## POST 设置好友备注名称

POST /api/friends/remark

> Body 请求参数

```json
{
  "friendId": 0,
  "remark": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» friendId|body|integer| 是 |好友ID|
|» remark|body|string| 是 |新的备注名称（最多20字符）|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "备注设置成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|备注设置成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|不能为自己设置备注或备注名称过长或好友关系不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

# 客户端/历史记录

## POST 更新历史记录

POST /api/history

> Body 请求参数

```json
{
  "targetType": "music",
  "targetId": "string",
  "progress": {},
  "settings": {}
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» targetType|body|string| 是 |资源类型|
|» targetId|body|string| 是 |资源ID|
|» progress|body|object| 是 |进度信息|
|» settings|body|object| 否 |播放/阅读设置|

#### 枚举值

|属性|值|
|---|---|
|» targetType|music|
|» targetType|video|
|» targetType|novel|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "_id": "string",
    "user": "string",
    "target": "string",
    "targetType": "string",
    "mediaProgress": {},
    "readingProgress": {},
    "settings": {},
    "createdAt": "2019-08-24T14:15:22Z",
    "updatedAt": "2019-08-24T14:15:22Z"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|更新成功|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|资源不存在|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» _id|string|false|none||none|
|»» user|string|false|none||none|
|»» target|string|false|none||none|
|»» targetType|string|false|none||none|
|»» mediaProgress|object|false|none||none|
|»» readingProgress|object|false|none||none|
|»» settings|object|false|none||none|
|»» createdAt|string(date-time)|false|none||none|
|»» updatedAt|string(date-time)|false|none||none|

## GET 获取历史记录列表

GET /api/history

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|query|string| 否 |资源类型|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "_id": "string",
        "user": "string",
        "target": {
          "_id": "string",
          "title": "string",
          "description": "string"
        },
        "targetType": "string",
        "progress": {},
        "settings": {},
        "createdAt": "2019-08-24T14:15:22Z",
        "updatedAt": "2019-08-24T14:15:22Z"
      }
    ],
    "total": 0,
    "totalPages": 0,
    "currentPage": 0,
    "pageSize": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» list|[object]|false|none||none|
|»»» _id|string|false|none||none|
|»»» user|string|false|none||none|
|»»» target|object|false|none||none|
|»»»» _id|string|false|none||none|
|»»»» title|string|false|none||none|
|»»»» description|string|false|none||none|
|»»» targetType|string|false|none||none|
|»»» progress|object|false|none||none|
|»»» settings|object|false|none||none|
|»»» createdAt|string(date-time)|false|none||none|
|»»» updatedAt|string(date-time)|false|none||none|
|»» total|number|false|none||none|
|»» totalPages|number|false|none||none|
|»» currentPage|number|false|none||none|
|»» pageSize|number|false|none||none|

## GET 获取特定资源的历史记录

GET /api/history/{targetId}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|targetId|path|string| 是 |资源ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "_id": "string",
    "user": "string",
    "target": "string",
    "targetType": "string",
    "mediaProgress": {},
    "readingProgress": {},
    "settings": {},
    "createdAt": "2019-08-24T14:15:22Z",
    "updatedAt": "2019-08-24T14:15:22Z"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|any|false|none||none|

*oneOf*

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|»» *anonymous*|object|false|none||none|
|»»» _id|string|false|none||none|
|»»» user|string|false|none||none|
|»»» target|string|false|none||none|
|»»» targetType|string|false|none||none|
|»»» mediaProgress|object|false|none||none|
|»»» readingProgress|object|false|none||none|
|»»» settings|object|false|none||none|
|»»» createdAt|string(date-time)|false|none||none|
|»»» updatedAt|string(date-time)|false|none||none|

*xor*

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|»» *anonymous*|object|false|none||none|
|»»» progress|object|false|none||none|
|»»»» position|number|false|none||none|
|»»»» percentage|number|false|none||none|

## DELETE 删除历史记录

DELETE /api/history/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|string| 是 |历史记录ID|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|删除成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## DELETE 批量删除历史记录

DELETE /api/history/batch

> Body 请求参数

```json
{
  "ids": [
    "string"
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» ids|body|[string]| 是 |历史记录ID数组|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "批量删除成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|批量删除成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请选择要删除的记录|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

## DELETE 清空历史记录

DELETE /api/history/all/{type}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|path|string| 否 |资源类型（可选，不指定则清空所有类型）|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "清空成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|清空成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|null|false|none||none|

# 客户端/消息

## POST 发送消息

POST /api/messages

> Body 请求参数

```json
{
  "receiverId": 0,
  "content": "string",
  "type": "text"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» receiverId|body|integer| 是 |接收者ID|
|» content|body|string| 是 |消息内容|
|» type|body|string| 否 |消息类型|

#### 枚举值

|属性|值|
|---|---|
|» type|text|
|» type|image|
|» type|audio|
|» type|file|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 撤回消息

POST /api/messages/{id}/recall

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |消息ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 标记聊天已读

POST /api/messages/read

> Body 请求参数

```json
{
  "targetId": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» targetId|body|integer| 是 |对方用户ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取消息列表

GET /api/messages/unread

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

# 客户端/通知

## GET 获取通知列表

GET /api/notifications

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

## DELETE 删除通知

DELETE /api/notifications

> Body 请求参数

```json
{
  "ids": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» ids|body|[integer]| 是 |通知ID列表|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|删除成功|None|

## GET 获取未读通知数量

GET /api/notifications/unread

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

## POST 标记通知为已读

POST /api/notifications/read

> Body 请求参数

```json
{
  "ids": [
    0
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» ids|body|[integer]| 是 |通知ID列表|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|标记成功|None|

## POST 标记所有通知为已读

POST /api/notifications/read-all

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|标记成功|None|

# 客户端/帖子

## GET 获取帖子列表

GET /api/posts

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|keyword|query|string| 否 |搜索关键词|
|tag|query|string| 否 |标签|
|sort|query|string| 否 |排序方式|

#### 枚举值

|属性|值|
|---|---|
|sort|latest|
|sort|hot|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 创建帖子

POST /api/posts

> Body 请求参数

```json
{
  "title": "string",
  "content": "string",
  "tags": [
    "string"
  ],
  "images": [
    "string"
  ],
  "status": "published"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» title|body|string| 是 |帖子标题|
|» content|body|string| 是 |帖子内容|
|» tags|body|[string]| 否 |标签列表|
|» images|body|[string]| 否 |图片URL列表|
|» status|body|string| 否 |帖子状态|

#### 枚举值

|属性|值|
|---|---|
|» status|published|
|» status|draft|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取帖子详情

GET /api/posts/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## PUT 更新帖子

PUT /api/posts/{id}

> Body 请求参数

```json
{
  "title": "string",
  "content": "string",
  "tags": [
    "string"
  ],
  "images": [
    "string"
  ],
  "status": "published"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|
|body|body|object| 是 |none|
|» title|body|string| 否 |帖子标题|
|» content|body|string| 否 |帖子内容|
|» tags|body|[string]| 否 |标签列表|
|» images|body|[string]| 否 |图片URL列表|
|» status|body|string| 否 |帖子状态|

#### 枚举值

|属性|值|
|---|---|
|» status|published|
|» status|draft|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## DELETE 删除帖子

DELETE /api/posts/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 点赞/取消点赞帖子

POST /api/posts/{id}/like

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 收藏/取消收藏帖子

POST /api/posts/{id}/favorite

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取帖子评论列表

GET /api/posts/{id}/comments

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## POST 发表评论

POST /api/posts/{id}/comments

> Body 请求参数

```json
{
  "content": "string",
  "parent_id": 0,
  "images": [
    "string"
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |帖子ID|
|body|body|object| 是 |none|
|» content|body|string| 是 |评论内容|
|» parent_id|body|integer| 否 |父评论ID（回复评论时使用）|
|» images|body|[string]| 否 |图片URL列表|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取评论的回复列表

GET /api/posts/comments/{id}/replies

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |评论ID|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## PUT 更新评论

PUT /api/posts/comments/{id}

> Body 请求参数

```json
{
  "content": "string",
  "images": [
    "string"
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |评论ID|
|body|body|object| 是 |none|
|» content|body|string| 是 |评论内容|
|» images|body|[string]| 否 |图片URL列表|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## DELETE 删除评论

DELETE /api/posts/comments/{id}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|id|path|integer| 是 |评论ID|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取用户草稿帖子列表

GET /api/posts/drafts

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

## GET 获取帖子统计数据

GET /api/posts/statistics

> 返回示例

> 200 Response

```
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|Inline|

### 返回数据结构

# 客户端/举报

## POST 提交举报

POST /api/reports

> Body 请求参数

```json
{
  "target_type": "post",
  "target_id": 0,
  "reason": "spam",
  "description": "string",
  "evidence": {}
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» target_type|body|string| 是 |举报目标类型|
|» target_id|body|integer| 是 |举报目标ID|
|» reason|body|string| 是 |举报原因|
|» description|body|string| 否 |举报详细说明|
|» evidence|body|object| 否 |举报证据（可以包含图片URL等）|

#### 枚举值

|属性|值|
|---|---|
|» target_type|post|
|» target_type|comment|
|» target_type|user|
|» target_type|music|
|» target_type|novel|
|» target_type|video|
|» reason|spam|
|» reason|abuse|
|» reason|porn|
|» reason|copyright|
|» reason|illegal|
|» reason|other|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|举报提交成功|None|

## GET 获取用户的举报历史

GET /api/reports

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|status|query|string| 否 |举报状态（可选）|

#### 枚举值

|属性|值|
|---|---|
|status|pending|
|status|processing|
|status|resolved|
|status|rejected|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

# 客户端/搜索

## GET 获取热门搜索词

GET /api/search/hot-keywords

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|query|string| 否 |搜索类型|
|limit|query|integer| 否 |返回数量|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|
|type|post|
|type|artist|
|type|album|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

## GET 获取用户搜索历史

GET /api/search/history

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|query|string| 否 |搜索类型|
|limit|query|integer| 否 |返回数量|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|
|type|post|
|type|artist|
|type|album|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取成功|None|

## DELETE 清空用户搜索历史

DELETE /api/search/history

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|type|query|string| 否 |搜索类型（可选，不传则清空所有类型）|

#### 枚举值

|属性|值|
|---|---|
|type|music|
|type|video|
|type|novel|
|type|post|
|type|artist|
|type|album|

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|清空成功|None|

# 文件

## POST 上传图片

POST /api/upload/image

> Body 请求参数

```json
{
  "url": "string",
  "filename": "string"
}
```

```yaml
url: string
filename: string

```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» url|body|string| 否 |图片URL|
|» filename|body|string| 否 |文件名(可选)|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "url": "string",
    "filename": "string",
    "size": 0
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» url|string|false|none||图片URL|
|»» filename|string|false|none||文件名|
|»» size|number|false|none||文件大小|
|» message|string|false|none||none|

## POST 上传视频

POST /api/upload/video

> Body 请求参数

```yaml
file: ""

```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» file|body|string(binary)| 否 |none|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "url": "string",
    "filename": "string",
    "size": 0
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» url|string|false|none||视频URL|
|»» filename|string|false|none||文件名|
|»» size|number|false|none||文件大小|
|» message|string|false|none||none|

## POST 上传音频

POST /api/upload/audio

> Body 请求参数

```json
{
  "url": "string",
  "filename": "string"
}
```

```yaml
url: string
filename: string

```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» url|body|string| 否 |音频文件URL|
|» filename|body|string| 否 |文件名(可选)|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "url": "string",
    "filename": "string",
    "size": 0
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» url|string|false|none||音频URL|
|»» filename|string|false|none||文件名|
|»» size|number|false|none||文件大小|
|» message|string|false|none||none|

## POST 上传小说

POST /api/upload/novel

> Body 请求参数

```yaml
file: ""

```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» file|body|string(binary)| 否 |小说文件（支持 txt、epub、pdf 格式）|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "url": "string",
    "filename": "string",
    "size": 0
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» url|string|false|none||小说文件URL|
|»» filename|string|false|none||文件名|
|»» size|number|false|none||文件大小|
|» message|string|false|none||none|

## POST 通用文件上传

POST /api/upload/file

> Body 请求参数

```yaml
file: ""

```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» file|body|string(binary)| 否 |none|

> 返回示例

> 200 Response

```json
{
  "success": true,
  "data": {
    "url": "string",
    "filename": "string",
    "size": 0,
    "mimetype": "string"
  },
  "message": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|操作成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|请求参数错误|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» success|boolean|false|none||none|
|» data|object|false|none||none|
|»» url|string|false|none||文件URL|
|»» filename|string|false|none||文件名|
|»» size|number|false|none||文件大小|
|»» mimetype|string|false|none||文件MIME类型|
|» message|string|false|none||none|

# 公共服务/文件

## GET 处理文件请求

GET /api/files/{fileType}/{filename}

支持临时文件、永久文件和OSS文件的获取

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|fileType|path|string| 是 |文件类型|
|filename|path|string| 是 |文件名|

> 返回示例

> 200 Response

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|文件获取成功|string|
|206|[Partial Content](https://tools.ietf.org/html/rfc7233#section-4.1)|部分文件获取成功（Range请求）|string|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|文件不存在|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器内部错误|None|

## POST 测试文件确认（仅管理员）

POST /api/files/confirm

> Body 请求参数

```json
{
  "url": "http://localhost:9000/uploads/temp/image/1735618781190_temp_5utg82.png"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» url|body|string| 否 |临时文件URL|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "message": "文件确认请求已接收"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|文件确认请求已接收|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|缺少文件URL|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|服务器内部错误|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» message|string|false|none||none|

# 公共服务/音乐

## GET QQ音乐搜索

GET /api/music/search/qq

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|keyword|query|string| 是 |搜索关键词|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|type|query|integer| 否 |搜索类型|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 0,
    "items": [
      {
        "id": "string",
        "title": "string",
        "description": "string",
        "language": "string",
        "url": "string",
        "singers": [
          {
            "name": null,
            "cover_image": null
          }
        ],
        "album": {
          "title": "string",
          "cover_image": "string"
        },
        "lrc": {
          "lyric": "string",
          "lyric_trans": "string"
        },
        "source": "string"
      }
    ],
    "page": 0,
    "limit": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|搜索成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|搜索关键词不能为空|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|音乐搜索服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» total|number|false|none||none|
|»» items|[object]|false|none||none|
|»»» id|string|false|none||none|
|»»» title|string|false|none||none|
|»»» description|string|false|none||none|
|»»» language|string|false|none||none|
|»»» url|string|false|none||none|
|»»» singers|[object]|false|none||none|
|»»»» name|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»»» album|object|false|none||none|
|»»»» title|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»»» lrc|object|false|none||none|
|»»»» lyric|string|false|none||none|
|»»»» lyric_trans|string|false|none||none|
|»»» source|string|false|none||none|
|»» page|number|false|none||none|
|»» limit|number|false|none||none|

## GET 网易云音乐搜索

GET /api/music/search/netease

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|keyword|query|string| 是 |搜索关键词|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |每页数量|
|type|query|integer| 否 |搜索类型|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 0,
    "items": [
      {
        "id": 0,
        "title": "string",
        "duration": 0,
        "description": "string",
        "language": "string",
        "url": "string",
        "singers": [
          {
            "id": null,
            "name": null,
            "alias": null,
            "cover_image": null,
            "avatar": null
          }
        ],
        "album": {
          "title": "string",
          "cover_image": "string",
          "release_date": "2019-08-24",
          "publisher": "string"
        },
        "lrc": {
          "lyric": "string",
          "lyric_trans": "string"
        },
        "source": "string"
      }
    ],
    "page": 0,
    "limit": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|搜索成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|搜索关键词不能为空|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|网易云音乐搜索服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» total|number|false|none||none|
|»» items|[object]|false|none||none|
|»»» id|number|false|none||none|
|»»» title|string|false|none||none|
|»»» duration|number|false|none||none|
|»»» description|string|false|none||none|
|»»» language|string|false|none||none|
|»»» url|string|false|none||none|
|»»» singers|[object]|false|none||none|
|»»»» id|number|false|none||none|
|»»»» name|string|false|none||none|
|»»»» alias|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»»»» avatar|string|false|none||none|
|»»» album|object|false|none||none|
|»»»» title|string|false|none||none|
|»»»» cover_image|string|false|none||none|
|»»»» release_date|string(date)|false|none||none|
|»»»» publisher|string|false|none||none|
|»»» lrc|object|false|none||none|
|»»»» lyric|string|false|none||none|
|»»»» lyric_trans|string|false|none||none|
|»»» source|string|false|none||none|
|»» page|number|false|none||none|
|»» limit|number|false|none||none|

## GET 网易云登录状态查询

GET /api/music/netease/login_status

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "account": {
      "id": 0,
      "userName": "string",
      "type": 0,
      "status": 0,
      "whitelistAuthority": 0,
      "createTime": 0,
      "salt": "string",
      "tokenVersion": 0,
      "ban": 0,
      "baId": "string",
      "expireTime": 0,
      "code": 0
    },
    "profile": {
      "nickname": "string",
      "avatarUrl": "string",
      "userId": 0,
      "userType": 0,
      "gender": 0,
      "accountType": 0,
      "vipType": 0,
      "level": 0,
      "listenSongs": 0
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|登录状态查询成功|Inline|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|网易云音乐登录状态查询服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» account|object|false|none||none|
|»»» id|number|false|none||none|
|»»» userName|string|false|none||none|
|»»» type|number|false|none||none|
|»»» status|number|false|none||none|
|»»» whitelistAuthority|number|false|none||none|
|»»» createTime|number|false|none||none|
|»»» salt|string|false|none||none|
|»»» tokenVersion|number|false|none||none|
|»»» ban|number|false|none||none|
|»»» baId|string|false|none||none|
|»»» expireTime|number|false|none||none|
|»»» code|number|false|none||none|
|»» profile|object|false|none||none|
|»»» nickname|string|false|none||none|
|»»» avatarUrl|string|false|none||none|
|»»» userId|number|false|none||none|
|»»» userType|number|false|none||none|
|»»» gender|number|false|none||none|
|»»» accountType|number|false|none||none|
|»»» vipType|number|false|none||none|
|»»» level|number|false|none||none|
|»»» listenSongs|number|false|none||none|

## POST 发送网易云登录验证码

POST /api/music/netease/login/sendCode

> Body 请求参数

```json
{
  "phone": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» phone|body|string| 是 |手机号|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": 200,
    "message": "验证码发送成功"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|验证码发送成功|Inline|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|网易云音乐登录服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» code|number|false|none||none|
|»» message|string|false|none||none|

## POST 网易云登录

POST /api/music/netease/login

> Body 请求参数

```json
{
  "phone": "string",
  "code": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» phone|body|string| 是 |手机号|
|» code|body|string| 是 |验证码|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "string",
    "cookie": "string",
    "account": {
      "id": 0,
      "userName": "string"
    },
    "profile": {
      "nickname": "string",
      "avatarUrl": "string",
      "userId": 0
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|登录成功|Inline|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|网易云音乐登录服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» token|string|false|none||none|
|»» cookie|string|false|none||none|
|»» account|object|false|none||none|
|»»» id|number|false|none||none|
|»»» userName|string|false|none||none|
|»» profile|object|false|none||none|
|»»» nickname|string|false|none||none|
|»»» avatarUrl|string|false|none||none|
|»»» userId|number|false|none||none|

## GET 网易云退出

GET /api/music/netease/logout

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": 200,
    "message": "退出登录成功"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|退出成功|Inline|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|网易云音乐退出服务异常|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» code|number|false|none||none|
|»» message|string|false|none||none|

## POST 获取音乐磁场播放链接

POST /api/music/hifiii/url

> Body 请求参数

```json
{
  "detailUrl": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|body|body|object| 是 |none|
|» detailUrl|body|string| 是 |音乐详情页URL|

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "detailUrl": "string",
    "audioUrl": "string"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|获取播放链接成功|Inline|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|detailUrl 不能为空或格式不正确|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|未能获取到播放链接|None|
|500|[Internal Server Error](https://tools.ietf.org/html/rfc7231#section-6.6.1)|获取播放链接失败|None|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|number|false|none||none|
|» message|string|false|none||none|
|» data|object|false|none||none|
|»» detailUrl|string|false|none||none|
|»» audioUrl|string|false|none||none|

# 数据模型

<h2 id="tocS_Album">Album</h2>

<a id="schemaalbum"></a>
<a id="schema_Album"></a>
<a id="tocSalbum"></a>
<a id="tocsalbum"></a>

```json
{
  "id": 0,
  "title": "string",
  "cover_image": "string",
  "description": "string",
  "release_date": "2019-08-24",
  "type": "studio",
  "language": "string",
  "publisher": "string",
  "is_featured": true,
  "duration": 0,
  "track_count": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|false|none||专辑ID|
|title|string|false|none||专辑标题|
|cover_image|string|false|none||封面图URL|
|description|string|false|none||专辑简介|
|release_date|string(date)|false|none||发行日期|
|type|string|false|none||专辑类型|
|language|string|false|none||语言|
|publisher|string|false|none||发行公司|
|is_featured|boolean|false|none||是否推荐|
|duration|integer|false|none||总时长(秒)|
|track_count|integer|false|none||音轨数量|

#### 枚举值

|属性|值|
|---|---|
|type|studio|
|type|live|
|type|compilation|
|type|remix|
|type|single|
|type|ep|

<h2 id="tocS_Novel">Novel</h2>

<a id="schemanovel"></a>
<a id="schema_Novel"></a>
<a id="tocSnovel"></a>
<a id="tocsnovel"></a>

```json
{
  "id": 0,
  "title": "string",
  "description": "string",
  "cover_image": "string",
  "author": "string",
  "status": "ongoing",
  "genre": "string",
  "is_vip": true,
  "price": 0,
  "word_count": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|false|none||小说ID|
|title|string|false|none||小说标题|
|description|string|false|none||小说简介|
|cover_image|string|false|none||封面图URL|
|author|string|false|none||作者名称|
|status|string|false|none||连载状态|
|genre|string|false|none||小说类型|
|is_vip|boolean|false|none||是否VIP章节|
|price|number|false|none||价格|
|word_count|integer|false|none||总字数|

#### 枚举值

|属性|值|
|---|---|
|status|ongoing|
|status|completed|
|status|hiatus|

<h2 id="tocS_Playlist">Playlist</h2>

<a id="schemaplaylist"></a>
<a id="schema_Playlist"></a>
<a id="tocSplaylist"></a>
<a id="tocsplaylist"></a>

```json
{
  "id": 0,
  "title": "string",
  "cover_image": "string",
  "description": "string",
  "release_date": "2019-08-24",
  "type": "playlist",
  "language": "string",
  "is_featured": true,
  "duration": 0,
  "track_count": 0,
  "created_at": "2019-08-24T14:15:22Z",
  "updated_at": "2019-08-24T14:15:22Z"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|false|none||歌单ID|
|title|string|false|none||歌单标题|
|cover_image|string|false|none||封面图URL|
|description|string|false|none||歌单简介|
|release_date|string(date)|false|none||创建日期|
|type|string|false|none||资源类型|
|language|string|false|none||语言|
|is_featured|boolean|false|none||是否推荐|
|duration|integer|false|none||总时长(秒)|
|track_count|integer|false|none||歌曲数量|
|created_at|string(date-time)|false|none||创建时间|
|updated_at|string(date-time)|false|none||更新时间|

#### 枚举值

|属性|值|
|---|---|
|type|playlist|

<h2 id="tocS_Post">Post</h2>

<a id="schemapost"></a>
<a id="schema_Post"></a>
<a id="tocSpost"></a>
<a id="tocspost"></a>

```json
{
  "id": 0,
  "title": "string",
  "content": "string",
  "author_id": 0,
  "view_count": 0,
  "like_count": 0,
  "collection_count": 0,
  "comment_count": 0,
  "is_pinned": true,
  "is_featured": true,
  "created_at": "2019-08-24T14:15:22Z",
  "updated_at": "2019-08-24T14:15:22Z"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|false|none||帖子ID|
|title|string|false|none||帖子标题|
|content|string|false|none||帖子内容|
|author_id|integer|false|none||作者ID|
|view_count|integer|false|none||浏览量|
|like_count|integer|false|none||点赞数|
|collection_count|integer|false|none||收藏数|
|comment_count|integer|false|none||评论数|
|is_pinned|boolean|false|none||是否置顶|
|is_featured|boolean|false|none||是否推荐|
|created_at|string(date-time)|false|none||创建时间|
|updated_at|string(date-time)|false|none||更新时间|

<h2 id="tocS_Video">Video</h2>

<a id="schemavideo"></a>
<a id="schema_Video"></a>
<a id="tocSvideo"></a>
<a id="tocsvideo"></a>

```json
{
  "id": 0,
  "title": "string",
  "description": "string",
  "cover_image": "string",
  "url": "string",
  "duration": 0,
  "status": "draft",
  "category": "string",
  "is_featured": true,
  "view_count": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|false|none||视频ID|
|title|string|false|none||视频标题|
|description|string|false|none||视频简介|
|cover_image|string|false|none||封面图URL|
|url|string|false|none||视频URL|
|duration|integer|false|none||视频时长(秒)|
|status|string|false|none||视频状态|
|category|string|false|none||视频分类|
|is_featured|boolean|false|none||是否推荐|
|view_count|integer|false|none||播放次数|

#### 枚举值

|属性|值|
|---|---|
|status|draft|
|status|published|
|status|hidden|

