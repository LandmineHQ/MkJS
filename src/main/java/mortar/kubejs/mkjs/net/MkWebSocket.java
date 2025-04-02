package mortar.kubejs.mkjs.net;

import java.util.function.Consumer;

import mortar.kubejs.mkjs.MkJSForge;

public class MkWebSocket {

    public void echo(String message, Consumer<String> callback) {
        callback.accept(message);
        MkJSForge.LOGGER.info("Echo:" + message);
    }
}
