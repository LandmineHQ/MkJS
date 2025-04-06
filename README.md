# MkJS - KubeJS HTTP & WebSocket 插件

MkJS 是一个 KubeJS 插件，为 Minecraft 提供了 HTTP 和 WebSocket 通信能力，让您的 KubeJS 脚本可以与外部服务器进行实时通信和数据交换。

## 功能特性

- **HTTP 客户端**: 支持同步和异步的 GET/POST 请求
- **WebSocket 客户端**: 提供实时双向通信能力
- **完整的事件处理**: 处理连接、消息、错误和关闭事件
- **自动重连机制**: WebSocket 连接中断后自动重连
- **稳定性设计**: 内置心跳机制确保长连接稳定性

## 安装方法

1. 下载最新版本的 MkJS jar 文件
2. 将 jar 文件放入 Minecraft 实例的 `mods` 目录
3. 确保已安装兼容版本的 KubeJS
4. 启动游戏，插件将自动加载

## 使用方法

### 在 KubeJS 脚本中使用 HTTP

```javascript
// 发送异步 POST 请求
function sendHttpMessage(message) {
  try {
    let url = "http://your-server.com/api/endpoint";
    let data = JSON.stringify({ text: message });
    
    // 发送请求并处理响应
    let futureResponse = HttpPlugin.postAsync(url, void 0, data);
    futureResponse.thenAccept((response) => {
      console.log("收到响应: " + response);
    });
    
    // 错误处理
    futureResponse.exceptionally((error) => {
      console.error("请求失败: " + error.getMessage());
      return null;
    });
  } catch (e) {
    console.error("HTTP请求错误:", e);
  }
}
```

### 在 KubeJS 脚本中使用 WebSocket

```javascript
// WebSocket连接实例
let wsConnection;

function setupWebSocket() {
  try {
    // 连接回调函数
    let onOpen = (msg) => console.log(`连接已建立: ${msg}`);
    let onMessage = (message) => {
      let data = JSON.parse(message);
      // 处理收到的消息
      Utils.server.tell(data.text);
    };
    let onClose = (reason) => {
      console.log(`连接已关闭: ${reason}`);
      // 自动重连
      Utils.server.scheduleInSeconds(5, () => setupWebSocket());
    };
    let onError = (e) => console.error(`WebSocket错误: ${e}`);

    // 创建连接
    wsConnection = WSPlugin.connect(
      "ws://your-websocket-server.com/socket",
      onMessage,
      onOpen,
      onClose,
      onError
    );
  } catch (e) {
    console.error("WebSocket初始化错误:", e);
  }
}

// 在服务器启动时连接
ServerEvents.loaded((event) => {
  setupWebSocket();
});

// 在服务器关闭时断开连接
ServerEvents.unloaded((event) => {
  if (wsConnection) wsConnection.close();
});
```

## 实际应用示例

`player_chats.js` 展示了如何创建一个聊天集成系统，将游戏内聊天消息发送到外部服务器，并将外部消息转发到游戏内。

```javascript
// 详见 kubejs/server_scripts/player_chats.js 
// 主要功能包括:
// - 游戏启动/关闭时的连接管理
// - 游戏内聊天消息转发到外部
// - 接收外部消息并显示在游戏内
// - 自动重连机制
```

## API 参考

### HTTP API (HttpPlugin)

```javascript
// GET 请求
HttpPlugin.get(url)
HttpPlugin.get(url, headers)
HttpPlugin.getAsync(url)
HttpPlugin.getAsync(url, headers)

// POST 请求
HttpPlugin.post(url, jsonData)
HttpPlugin.post(url, headers, jsonData)
HttpPlugin.postAsync(url, jsonData)
HttpPlugin.postAsync(url, headers, jsonData)
```

### WebSocket API (WSPlugin)

```javascript
// 创建连接
WSPlugin.connect(url, onMessage, onOpen, onClose, onError)

// WebSocketClient 对象方法
client.send(message)       // 发送消息
client.close()             // 关闭连接
client.isConnected()       // 检查连接状态
```

## 项目架构

- `MkHTTP.java`: HTTP 客户端实现，提供同步和异步请求功能
- `MkWebSocket.java`: WebSocket 客户端实现，处理连接管理和消息传输
- 脚本API: 通过 KubeJS 的插件系统暴露为 `HttpPlugin` 和 `WSPlugin`

## 使用场景

- 游戏内外聊天集成
- 游戏数据同步到外部服务
- 从外部API获取实时数据
- 创建游戏内的网页界面控制系统
- 构建复杂的事件驱动系统

## 依赖

- Minecraft Forge/Fabric
- KubeJS
- Jakarta WebSocket API
- Tyrus WebSocket 客户端

## 许可证

[MIT] - 查看 LICENSE 文件了解详情

## 贡献

欢迎通过 Issues 和 Pull Requests 贡献代码。请确保您的代码符合项目的编码风格和测试要求。

---

**注意**: 此插件需要互联网连接才能正常工作，并可能受到服务器网络策略的限制。