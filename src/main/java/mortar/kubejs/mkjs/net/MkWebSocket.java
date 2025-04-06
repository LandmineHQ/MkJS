package mortar.kubejs.mkjs.net;

import java.util.function.Consumer;
import java.net.URI;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import mortar.kubejs.mkjs.MkJSForge;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.CloseReason;

// 导入 Tyrus 的 ClientManager 类
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;

public class MkWebSocket {

    /**
     * 创建一个WebSocket客户端并在后台异步连接
     *
     * @param url       WebSocket服务器URL
     * @param onMessage 接收消息时的回调函数 (message) -> {}
     * @param onOpen    连接建立时的回调函数 (message) -> {}
     * @param onClose   连接关闭时的回调函数 (reasonPhrase) -> {}
     * @param onError   发生错误时的回调函数 (errorMessage) -> {}
     * @return 一个WebSocket客户端对象
     */
    public WebSocketClient connect(String url,
            Consumer<String> onMessage,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onError) {

        // 创建并返回WebSocket客户端
        WebSocketClient client = new WebSocketClient(url, onMessage, onOpen, onClose, onError);
        // 启动异步连接
        client.connectAsync();
        return client;
    }

    /**
     * WebSocket客户端类
     */
    public class WebSocketClient {
        private final String url;
        private final Consumer<String> onMessage;
        private final Consumer<String> onOpen;
        private final Consumer<String> onClose;
        private final Consumer<String> onError;

        private Session session;
        private CompletableFuture<Session> connectionFuture;
        private volatile boolean heartbeatRunning = false;
        // 创建一个单独的MessageHandler实例，而不是每次都创建匿名内部类
        private final MessageHandler.Whole<String> messageHandler;

        /**
         * 创建WebSocket客户端
         */
        public WebSocketClient(String url, Consumer<String> onMessage, Consumer<String> onOpen,
                Consumer<String> onClose, Consumer<String> onError) {
            this.url = url;
            this.onMessage = onMessage;
            this.onOpen = onOpen;
            this.onClose = onClose;
            this.onError = onError;
            
            // 提前创建消息处理器，在onOpen中使用
            this.messageHandler = new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    MkJSForge.LOGGER.debug("收到WebSocket消息: " + message);
                    if (onMessage != null) {
                        onMessage.accept(message);
                    }
                }
            };
        }

        /**
         * 异步连接到WebSocket服务器
         */
        private void connectAsync() {
            connectionFuture = new CompletableFuture<>();

            new Thread(() -> {
                try {
                    // 创建ClientManager
                    ClientManager container = ClientManager.createClient();

                    // 设置客户端属性以保持连接活跃

                    // 增加连接超时时间（毫秒）
                    container.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, 30000);

                    // 是否共享容器（这有助于减少资源使用）
                    container.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

                    // 共享容器空闲超时（秒）
                    container.getProperties().put(ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT, 300); // 5分钟

                    // 启用HTTP重定向支持
                    container.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);

                    // 设置最大重定向次数
                    container.getProperties().put(ClientProperties.REDIRECT_THRESHOLD, 5);

                    // 启用503服务不可用后重试
                    container.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, true);

                    // 增加传入缓冲区大小
                    container.getProperties().put(ClientProperties.INCOMING_BUFFER_SIZE, 65536); // 64KB

                    // 记录HTTP升级消息
                    container.getProperties().put(ClientProperties.LOG_HTTP_UPGRADE, true);

                    // 开启更详细的日志记录（帮助诊断问题）
                    MkJSForge.LOGGER.info("准备连接到WebSocket服务器: " + url);

                    // 创建端点配置
                    ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

                    // 创建自定义Endpoint来处理事件
                    Endpoint endpoint = new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            WebSocketClient.this.session = session; // 保存session引用

                            // 设置更长的会话超时时间
                            session.setMaxIdleTimeout(0); // 0表示无限

                            // 添加连接建立日志
                            MkJSForge.LOGGER.info("WebSocket连接已建立！ID: " + session.getId());

                            // 关键修复：确保先注册消息处理器，并添加错误处理
                            try {
                                MkJSForge.LOGGER.info("正在注册WebSocket消息处理器...");
                                // 这里不使用String.class参数，而是直接使用MessageHandler.Whole接口
                                session.addMessageHandler(messageHandler);
                                MkJSForge.LOGGER.info("WebSocket消息处理器注册成功");
                            } catch (Exception e) {
                                MkJSForge.LOGGER.error("注册消息处理器失败: " + e.getMessage(), e);
                                if (onError != null) {
                                    onError.accept("注册消息处理器失败: " + e.getMessage());
                                }
                            }

                            // 只有在成功注册消息处理器后才触发onOpen回调
                            if (onOpen != null) {
                                onOpen.accept("WebSocket连接已建立");
                            }

                            // 启动心跳包线程保持连接活跃
                            startHeartbeat(session);

                            // 连接成功，完成future
                            connectionFuture.complete(session);
                        }

                        @Override
                        public void onClose(Session session, CloseReason closeReason) {
                            // 停止心跳线程
                            heartbeatRunning = false;

                            // 添加连接关闭日志
                            MkJSForge.LOGGER.info("WebSocket连接关闭: " + closeReason.getReasonPhrase() +
                                    " (代码: " + closeReason.getCloseCode() + ")");

                            if (onClose != null) {
                                onClose.accept(closeReason.getReasonPhrase());
                            }
                        }

                        @Override
                        public void onError(Session session, Throwable throwable) {
                            // 添加错误日志
                            MkJSForge.LOGGER.error(
                                    "WebSocket错误: " + (throwable != null ? throwable.getMessage() : "未知错误"), throwable);

                            if (onError != null) {
                                onError.accept(throwable != null ? throwable.getMessage() : "未知错误");
                            }

                            // 如果future尚未完成，则使其异常完成
                            if (!connectionFuture.isDone()) {
                                connectionFuture.completeExceptionally(
                                        throwable != null ? throwable : new Exception("未知WebSocket错误"));
                            }
                        }
                    };

                    // 连接到WebSocket服务器
                    container.connectToServer(endpoint, config, new URI(url));
                    MkJSForge.LOGGER.info("WebSocket连接请求已发送");

                } catch (Exception e) {
                    MkJSForge.LOGGER.error("WebSocket连接失败: " + e.getMessage(), e);
                    connectionFuture.completeExceptionally(e);
                    if (onError != null) {
                        onError.accept("连接失败: " + e.getMessage());
                    }
                }
            }, "WebSocket-Connect-Thread").start();
        }

        /**
         * 启动心跳包线程以保持连接活跃
         */
        private void startHeartbeat(Session session) {
            heartbeatRunning = true;
            new Thread(() -> {
                try {
                    while (heartbeatRunning && session.isOpen()) {
                        try {
                            // 每30秒发送一次心跳包
                            Thread.sleep(30000);
                            if (heartbeatRunning && session.isOpen()) {
                                // 发送ping包保持连接活跃
                                session.getBasicRemote().sendPing(java.nio.ByteBuffer.wrap("heartbeat".getBytes()));
                                MkJSForge.LOGGER.debug("发送WebSocket心跳包");
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            if (heartbeatRunning && session.isOpen()) {
                                MkJSForge.LOGGER.debug("发送心跳包错误: " + e.getMessage());
                            } else {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    MkJSForge.LOGGER.debug("心跳线程终止: " + e.getMessage());
                }
            }, "WebSocket-Heartbeat-Thread").start();
        }

        /**
         * 发送文本消息
         * 
         * @param message 要发送的消息
         * @return 表示发送操作的CompletableFuture
         */
        public CompletableFuture<Void> send(String message) {
            CompletableFuture<Void> sendFuture = new CompletableFuture<>();

            // 如果已经连接，直接发送
            if (session != null && session.isOpen()) {
                try {
                    MkJSForge.LOGGER.debug("发送WebSocket消息: " + message);
                    session.getBasicRemote().sendText(message);
                    sendFuture.complete(null);
                    return sendFuture;
                } catch (IOException e) {
                    MkJSForge.LOGGER.error("发送消息失败: " + e.getMessage(), e);
                    sendFuture.completeExceptionally(e);
                    if (onError != null) {
                        onError.accept("发送失败: " + e.getMessage());
                    }
                    return sendFuture;
                }
            }

            // 连接完成后发送消息
            connectionFuture.thenAccept(session -> {
                try {
                    MkJSForge.LOGGER.debug("发送WebSocket消息: " + message);
                    session.getBasicRemote().sendText(message);
                    sendFuture.complete(null);
                } catch (IOException e) {
                    MkJSForge.LOGGER.error("发送消息失败: " + e.getMessage(), e);
                    sendFuture.completeExceptionally(e);
                    if (onError != null) {
                        onError.accept("发送失败: " + e.getMessage());
                    }
                }
            }).exceptionally(ex -> {
                sendFuture.completeExceptionally(ex);
                return null;
            });

            return sendFuture;
        }

        /**
         * 关闭WebSocket连接
         * 
         * @return 表示关闭操作的CompletableFuture
         */
        public CompletableFuture<Void> close() {
            CompletableFuture<Void> closeFuture = new CompletableFuture<>();

            // 停止心跳线程
            heartbeatRunning = false;

            // 如果已经连接，直接关闭
            if (session != null && session.isOpen()) {
                try {
                    MkJSForge.LOGGER.info("主动关闭WebSocket连接");
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "客户端主动断开连接"));
                    closeFuture.complete(null);
                    return closeFuture;
                } catch (IOException e) {
                    MkJSForge.LOGGER.error("关闭连接失败: " + e.getMessage(), e);
                    closeFuture.completeExceptionally(e);
                    return closeFuture;
                }
            }

            // 如果连接尚未建立，等待连接建立后再关闭
            connectionFuture.thenAccept(session -> {
                try {
                    if (session.isOpen()) {
                        MkJSForge.LOGGER.info("主动关闭WebSocket连接");
                        session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "客户端主动断开连接"));
                    }
                    closeFuture.complete(null);
                } catch (IOException e) {
                    MkJSForge.LOGGER.error("关闭连接失败: " + e.getMessage(), e);
                    closeFuture.completeExceptionally(e);
                }
            }).exceptionally(ex -> {
                closeFuture.completeExceptionally(ex);
                return null;
            });

            return closeFuture;
        }

        /**
         * 检查连接是否已经建立
         * 
         * @return 连接是否已经建立
         */
        public boolean isConnected() {
            return session != null && session.isOpen();
        }

        /**
         * 获取连接建立后的Session对象（会阻塞等待连接完成）
         * 
         * @return WebSocket会话对象
         * @throws Exception 如果连接失败
         */
        public Session getSession() throws Exception {
            return connectionFuture.get(); // 注意：这会阻塞直到连接完成
        }

        /**
         * 获取底层的连接Future
         * 
         * @return 连接过程的CompletableFuture
         */
        public CompletableFuture<Session> getConnectionFuture() {
            return connectionFuture;
        }
    }
}