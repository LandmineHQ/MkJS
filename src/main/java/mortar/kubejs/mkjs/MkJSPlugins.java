package mortar.kubejs.mkjs;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.rhino.util.ConsString;
import dev.latvian.mods.rhino.util.DynamicFunction;

public class MkJSPlugins extends KubeJSPlugin {
    @Override
    public void init() {
        MkJSForge.LOGGER.info("Http Plugin初始化中...");
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("HttpPlugin", new HttpPlugin());
        event.add("WSPlugin", new WSPlugin());
    }
}

class WSPlugin {
    private Map<String, WebSocketClient> connections = new ConcurrentHashMap<>();

    /**
     * 连接到WebSocket服务器
     *
     * @param url       WebSocket服务器URL
     * @param onMessage 接收消息时的回调函数
     * @param onOpen    连接建立时的回调函数
     * @param onClose   连接关闭时的回调函数
     * @param onError   发生错误时的回调函数
     * @return 连接ID
     */
    public String connect(String url, Object onMessage, Object onOpen, Object onClose, Object onError) {
        try {
            String connectionId = generateConnectionId(url);
            WebSocketClient client = new WebSocketClient(url,
                    onMessage instanceof DynamicFunction ? (DynamicFunction) onMessage : null,
                    onOpen instanceof DynamicFunction ? (DynamicFunction) onOpen : null,
                    onClose instanceof DynamicFunction ? (DynamicFunction) onClose : null,
                    onError instanceof DynamicFunction ? (DynamicFunction) onError : null);

            client.connect();
            connections.put(connectionId, client);
            return connectionId;
        } catch (Exception e) {
            MkJSForge.LOGGER.error("WebSocket连接失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 发送消息到WebSocket服务器
     *
     * @param connectionId 连接ID
     * @param message      要发送的消息
     * @return 是否发送成功
     */
    public boolean send(String connectionId, String message) {
        WebSocketClient client = connections.get(connectionId);
        if (client != null && client.isConnected()) {
            return client.sendMessage(message);
        }
        return false;
    }

    /**
     * 关闭WebSocket连接
     *
     * @param connectionId 连接ID
     * @return 是否关闭成功
     */
    public boolean close(String connectionId) {
        WebSocketClient client = connections.get(connectionId);
        if (client != null) {
            boolean result = client.disconnect();
            if (result) {
                connections.remove(connectionId);
            }
            return result;
        }
        return false;
    }

    /**
     * 检查连接是否建立
     *
     * @param connectionId 连接ID
     * @return 是否已连接
     */
    public boolean isConnected(String connectionId) {
        WebSocketClient client = connections.get(connectionId);
        return client != null && client.isConnected();
    }

    // 生成连接ID
    private String generateConnectionId(String url) {
        return "ws_" + System.currentTimeMillis() + "_" + url.hashCode();
    }

    /**
     * WebSocket客户端实现
     */
    @ClientEndpoint
    private class WebSocketClient {
        private Session session;
        private String url;
        private DynamicFunction onMessageCallback;
        private DynamicFunction onOpenCallback;
        private DynamicFunction onCloseCallback;
        private DynamicFunction onErrorCallback;

        public WebSocketClient(String url, DynamicFunction onMessage, DynamicFunction onOpen,
                DynamicFunction onClose, DynamicFunction onError) {
            this.url = url;
            this.onMessageCallback = onMessage;
            this.onOpenCallback = onOpen;
            this.onCloseCallback = onClose;
            this.onErrorCallback = onError;
        }

        public void connect() throws Exception {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            this.session = container.connectToServer(this, new URI(url));
        }

        public boolean disconnect() {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                    return true;
                }
                return false;
            } catch (Exception e) {
                MkJSForge.LOGGER.error("关闭WebSocket连接失败: " + e.getMessage(), e);
                return false;
            }
        }

        public boolean isConnected() {
            return session != null && session.isOpen();
        }

        public boolean sendMessage(String message) {
            try {
                if (isConnected()) {
                    session.getBasicRemote().sendText(message);
                    return true;
                }
                return false;
            } catch (Exception e) {
                MkJSForge.LOGGER.error("发送WebSocket消息失败: " + e.getMessage(), e);
                return false;
            }
        }

        @OnOpen
        public void onOpen(Session session) {
            MkJSForge.LOGGER.info("WebSocket连接已建立: " + url);
            if (onOpenCallback != null) {
                try {
                    onOpenCallback.call(null, new Object[] { url });
                } catch (Exception e) {
                    MkJSForge.LOGGER.error("执行onOpen回调失败: " + e.getMessage(), e);
                }
            }
        }

        @OnMessage
        public void onMessage(String message) {
            if (onMessageCallback != null) {
                try {
                    onMessageCallback.call(null, new Object[] { message, url });
                } catch (Exception e) {
                    MkJSForge.LOGGER.error("执行onMessage回调失败: " + e.getMessage(), e);
                }
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            MkJSForge.LOGGER.info("WebSocket连接已关闭: " + url + ", 原因: " + reason);
            if (onCloseCallback != null) {
                try {
                    onCloseCallback.call(null,
                            new Object[] { reason.getReasonPhrase(), reason.getCloseCode().getCode(), url });
                } catch (Exception e) {
                    MkJSForge.LOGGER.error("执行onClose回调失败: " + e.getMessage(), e);
                }
            }
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            MkJSForge.LOGGER.error("WebSocket错误: " + throwable.getMessage(), throwable);
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.call(null, new Object[] { throwable.getMessage(), url });
                } catch (Exception e) {
                    MkJSForge.LOGGER.error("执行onError回调失败: " + e.getMessage(), e);
                }
            }
        }
    }
}

class HttpPlugin {
    /**
     * 执行GET请求
     * 
     * @param urlString 请求URL
     * @return 响应内容
     */
    public String get(String urlString) throws Exception {
        return sendRequest(urlString, "GET", null, null);
    }

    /**
     * 执行带有headers的GET请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @return 响应内容
     */
    public String get(String urlString, Map<String, String> headers) throws Exception {
        return sendRequest(urlString, "GET", headers, null);
    }

    /**
     * 执行POST请求
     * 
     * @param urlString 请求URL
     * @param jsonData  JSON格式数据
     * @return 响应内容
     */
    public String post(String urlString, String jsonData) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return sendRequest(urlString, "POST", headers, jsonData);
    }

    /**
     * 执行带有headers的POST请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @param jsonData  JSON格式数据
     * @return 响应内容
     */
    public String post(String urlString, Map<String, String> headers, String jsonData) throws Exception {
        if (headers == null) {
            headers = new HashMap<>();
        }
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", "application/json");
        }
        return sendRequest(urlString, "POST", headers, jsonData);
    }

    /**
     * 发送HTTP请求
     */
    private String sendRequest(String urlString, String method, Map<String, String> headers, String data)
            throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        // 发送数据
        if (data != null && !data.isEmpty()) {
            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                byte[] postData = data.getBytes(StandardCharsets.UTF_8);
                outputStream.write(postData);
                outputStream.flush();
            }
        }

        // 获取响应
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        connection.disconnect();
        return response.toString();
    }
}