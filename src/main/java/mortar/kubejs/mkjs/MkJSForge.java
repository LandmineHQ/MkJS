package mortar.kubejs.mkjs;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MkJSForge.MODID)
public class MkJSForge {
    public static final String MODID = "mkjs";
    public static final Logger LOGGER = LogManager.getLogger(MkJSForge.MODID);

    public MkJSForge() {
        LOGGER.info(MkJSForge.MODID + "初始化中...");
        MinecraftForge.EVENT_BUS.addListener(this::reloadListener);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientCommon);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onServerCommon);
        MinecraftForge.EVENT_BUS.addListener(this::onServerEnding);
    }

    private void reloadListener(AddReloadListenerEvent event) {
    }

    private void onClientCommon(FMLClientSetupEvent event) {
    }

    private void onServerCommon(FMLDedicatedServerSetupEvent event) {
    }

    private void onServerEnding(ServerStoppedEvent e) {
    }
}