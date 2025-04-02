package mortar.kubejs.mkjs.plugins;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import mortar.kubejs.mkjs.net.MkHTTP;
import mortar.kubejs.mkjs.net.MkWebSocket;

public class MkJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("HttpPlugin", new MkHTTP());
        event.add("WSPlugin", new MkWebSocket());
    }
}
