package mortar.kubejs.mkjs.net;

import java.util.function.Consumer;
import java.net.URI;

import org.glassfish.tyrus.client.ClientManager;
import mortar.kubejs.mkjs.MkJSForge;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.CloseReason;

public class MkWebSocket {
    /**
     * 连接到WebSocket服务器
     *
     * @param url       WebSocket服务器URL
     * @param onMessage 接收消息时的回调函数 (message) -> {}
     * @param onOpen    连接建立时的回调函数 (message) -> {}
     * @param onClose   连接关闭时的回调函数 (reasonPhrase) -> {}
     * @param onError   发生错误时的回调函数 (errorMessage) -> {}
     * @return 连接ID
     */
    public ClientManager connect(String url,
            Consumer<String> onMessage,
            Consumer<String> onOpen,
            Consumer<String> onClose,
            Consumer<String> onError) {
        try {
            // 创建ClientManager实例
            ClientManager client = ClientManager.createClient();

            // 创建端点配置
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

            // 创建自定义Endpoint来处理事件
            Endpoint endpoint = new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    if (onOpen != null) {
                        onOpen.accept("WebSocket连接已建立");
                    }

                    // 添加消息处理器
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (onMessage != null) {
                                onMessage.accept(message);
                            }
                        }
                    });
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (onClose != null) {
                        onClose.accept(closeReason.getReasonPhrase());
                    }
                }

                @Override
                public void onError(Session session, Throwable throwable) {
                    if (onError != null) {
                        onError.accept(throwable.getMessage());
                    }
                }
            };

            // 连接到WebSocket服务器
            client.connectToServer(endpoint, config, new URI(url));

            return client;
        } catch (Exception e) {
            MkJSForge.LOGGER.error("WebSocket连接失败: " + e.getMessage(), e);
            return null;
        }
    }
}