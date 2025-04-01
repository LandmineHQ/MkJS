package mortar.plugins.kubefilters;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

// 这里的值应该匹配META-INF/mods.toml文件中的条目
@Mod(KubeFilters.MODID)
public class KubeFilters {
    // 在一个公共位置定义模组ID，方便所有地方引用
    public static final String MODID = "kubefilters";
    // 直接引用slf4j日志记录器
    private static final Logger LOGGER = LogUtils.getLogger();
    // 创建一个延迟注册表来保存方块
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // 创建一个延迟注册表来保存物品
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // 创建一个延迟注册表来保存创造模式标签
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    public KubeFilters(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 注册commonSetup方法用于模组加载
        modEventBus.addListener(this::commonSetup);

        // 将延迟注册表注册到模组事件总线，以便方块被注册
        BLOCKS.register(modEventBus);
        // 将延迟注册表注册到模组事件总线，以便物品被注册
        ITEMS.register(modEventBus);
        // 将延迟注册表注册到模组事件总线，以便标签被注册
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册自身以监听服务器和其他我们感兴趣的游戏事件
        MinecraftForge.EVENT_BUS.register(this);

        // 注册构建创造模式标签内容的事件监听器
        modEventBus.addListener(this::addCreative);

        // 注册我们模组的ForgeConfigSpec，以便Forge可以为我们创建和加载配置文件
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 一些通用设置代码
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // 添加物品到创造模式标签
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 此处可以添加物品到创造模式标签
    }

    // 您可以使用SubscribeEvent并让事件总线发现需要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 服务器启动时执行操作
        LOGGER.info("HELLO from server starting");
    }

    // 您可以使用EventBusSubscriber自动注册类中所有带有@SubscribeEvent注解的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 一些客户端设置代码
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}