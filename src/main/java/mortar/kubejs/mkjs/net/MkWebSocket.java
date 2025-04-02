package mortar.kubejs.mkjs.net;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import mortar.kubejs.mkjs.MkJSForge;

public class MkWebSocket {
    private Map<String, WebSocketClient> connections = new ConcurrentHashMap<>();

    /**
     * 定义三参数消费者接口，用于onClose回调
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    /**
     * 连接到WebSocket服务器
     *
     * @param url       WebSocket服务器URL
     * @param onMessage 接收消息时的回调函数 (message, url) -> {}
     * @param onOpen    连接建立时的回调函数 (url) -> {}
     * @param onClose   连接关闭时的回调函数 (reasonPhrase, code, url) -> {}
     * @param onError   发生错误时的回调函数 (errorMessage, url) -> {}
     * @return 连接ID
     */
    public String connect(String url,
            BiConsumer<String, String> onMessage,
            Consumer<String> onOpen,
            TriConsumer<String, Integer, String> onClose,
            BiConsumer<String, String> onError) {
        try {
            String connectionId = generateConnectionId(url);
            WebSocketClient client = new WebSocketClient(url, onMessage, onOpen, onClose, onError);

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
    private class WebSocketClient {
        private final String url;
        private final BiConsumer<String, String> onMessageCallback;
        private final Consumer<String> onOpenCallback;
        private final TriConsumer<String, Integer, String> onCloseCallback;
        private final BiConsumer<String, String> onErrorCallback;
        private boolean connected = false;
        private Session session;

        public WebSocketClient(String url,
                BiConsumer<String, String> onMessage,
                Consumer<String> onOpen,
                TriConsumer<String, Integer, String> onClose,
                BiConsumer<String, String> onError) {
            this.url = url;
            this.onMessageCallback = onMessage;
            this.onOpenCallback = onOpen;
            this.onCloseCallback = onClose;
            this.onErrorCallback = onError;
        }

        public void connect() throws Exception {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();

                // 创建WebSocket端点并连接
                session = container.connectToServer(new WebSocketEndpoint(), URI.create(url));
            } catch (DeploymentException | IOException e) {
                connected = false;
                if (onErrorCallback != null) {
                    onErrorCallback.accept("连接失败: " + e.getMessage(), url);
                }
                throw e;
            }
        }

        public boolean disconnect() {
            try {
                if (session != null && session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "客户端主动关闭"));
                    connected = false;
                    return true;
                }
                return false;
            } catch (Exception e) {
                MkJSForge.LOGGER.error("关闭WebSocket连接失败: " + e.getMessage(), e);
                return false;
            }
        }

        public boolean isConnected() {
            return connected && session != null && session.isOpen();
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

        @ClientEndpoint
        public class WebSocketEndpoint {

            @OnOpen
            public void onOpen(Session session) {
                connected = true;
                MkJSForge.LOGGER.info("WebSocket连接已建立: " + url);
                if (onOpenCallback != null) {
                    try {
                        onOpenCallback.accept(url);
                    } catch (Exception e) {
                        MkJSForge.LOGGER.error("执行onOpen回调失败: " + e.getMessage(), e);
                    }
                }
            }

            @OnMessage
            public void onMessage(String message) {
                if (onMessageCallback != null) {
                    try {
                        onMessageCallback.accept(message, url);
                    } catch (Exception e) {
                        MkJSForge.LOGGER.error("执行onMessage回调失败: " + e.getMessage(), e);
                    }
                }
            }

            @OnMessage
            public void onBinary(ByteBuffer data) {
                // 二进制消息处理，如有需要可以实现
            }

            @OnClose
            public void onClose(Session session, CloseReason closeReason) {
                connected = false;
                MkJSForge.LOGGER.info("WebSocket连接已关闭: " + url +
                        ", 状态码: " + closeReason.getCloseCode().getCode() +
                        ", 原因: " + closeReason.getReasonPhrase());
                if (onCloseCallback != null) {
                    try {
                        onCloseCallback.accept(
                                closeReason.getReasonPhrase(),
                                closeReason.getCloseCode().getCode(),
                                url);
                    } catch (Exception e) {
                        MkJSForge.LOGGER.error("执行onClose回调失败: " + e.getMessage(), e);
                    }
                }
            }

            @OnError
            public void onError(Session session, Throwable error) {
                MkJSForge.LOGGER.error("WebSocket错误: " + error.getMessage(), error);
                if (onErrorCallback != null) {
                    try {
                        onErrorCallback.accept(error.getMessage(), url);
                    } catch (Exception e) {
                        MkJSForge.LOGGER.error("执行onError回调失败: " + e.getMessage(), e);
                    }
                }
            }
        }
    }
}