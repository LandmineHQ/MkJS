package mortar.kubejs.mkjs.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import mortar.kubejs.mkjs.MkJSForge;

public class MkWebSocket {
    private Map<String, WebSocketClient> connections = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
        private WebSocket webSocket;
        private final String url;
        private final BiConsumer<String, String> onMessageCallback;
        private final Consumer<String> onOpenCallback;
        private final TriConsumer<String, Integer, String> onCloseCallback;
        private final BiConsumer<String, String> onErrorCallback;
        private boolean connected = false;
        private CompletableFuture<WebSocket> futureWebSocket;

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
            WebSocket.Listener listener = new WebSocket.Listener() {
                StringBuilder messageBuilder = new StringBuilder();

                @Override
                public void onOpen(WebSocket webSocket) {
                    connected = true;
                    MkJSForge.LOGGER.info("WebSocket连接已建立: " + url);
                    if (onOpenCallback != null) {
                        try {
                            // 直接调用回调函数
                            onOpenCallback.accept(url);
                        } catch (Exception e) {
                            MkJSForge.LOGGER.error("执行onOpen回调失败: " + e.getMessage(), e);
                        }
                    }
                    // 请求接收消息
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    // 累积消息片段
                    messageBuilder.append(data);

                    // 如果这是最后一个片段，处理完整消息
                    if (last) {
                        String completeMessage = messageBuilder.toString();
                        messageBuilder = new StringBuilder();

                        if (onMessageCallback != null) {
                            try {
                                // 直接调用回调函数
                                onMessageCallback.accept(completeMessage, url);
                            } catch (Exception e) {
                                MkJSForge.LOGGER.error("执行onMessage回调失败: " + e.getMessage(), e);
                            }
                        }
                    }

                    // 请求接收更多消息
                    webSocket.request(1);
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    connected = false;
                    MkJSForge.LOGGER.info("WebSocket连接已关闭: " + url + ", 状态码: " + statusCode + ", 原因: " + reason);
                    if (onCloseCallback != null) {
                        try {
                            // 直接调用回调函数
                            onCloseCallback.accept(reason, statusCode, url);
                        } catch (Exception e) {
                            MkJSForge.LOGGER.error("执行onClose回调失败: " + e.getMessage(), e);
                        }
                    }
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    MkJSForge.LOGGER.error("WebSocket错误: " + error.getMessage(), error);
                    if (onErrorCallback != null) {
                        try {
                            // 直接调用回调函数
                            onErrorCallback.accept(error.getMessage(), url);
                        } catch (Exception e) {
                            MkJSForge.LOGGER.error("执行onError回调失败: " + e.getMessage(), e);
                        }
                    }
                }

                @Override
                public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                    // 请求接收更多消息
                    webSocket.request(1);
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                    // 请求接收更多消息
                    webSocket.request(1);
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                    // 请求接收更多消息
                    webSocket.request(1);
                    return CompletableFuture.completedFuture(null);
                }
            };

            try {
                futureWebSocket = httpClient.newWebSocketBuilder()
                        .buildAsync(URI.create(url), listener);

                // 获取WebSocket对象
                webSocket = futureWebSocket.join();
            } catch (Exception e) {
                connected = false;
                if (onErrorCallback != null) {
                    onErrorCallback.accept("连接失败: " + e.getMessage(), url);
                }
                throw e;
            }
        }

        public boolean disconnect() {
            try {
                if (webSocket != null) {
                    // 关闭WebSocket连接（正常关闭）
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "客户端主动关闭").join();
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
            return connected;
        }

        public boolean sendMessage(String message) {
            try {
                if (isConnected()) {
                    // 异步发送消息
                    webSocket.sendText(message, true).join();
                    return true;
                }
                return false;
            } catch (Exception e) {
                MkJSForge.LOGGER.error("发送WebSocket消息失败: " + e.getMessage(), e);
                return false;
            }
        }
    }
}