package mortar.plugins.kubefilters;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 配置类示例。这不是必需的，但拥有一个配置类可以让你的配置保持有序。
// 演示如何使用Forge的配置API
@Mod.EventBusSubscriber(modid = KubeFilters.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        static final ForgeConfigSpec SPEC = BUILDER.build();

        private static boolean validateItemName(final Object obj) {
                return obj instanceof final String itemName
                                && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
        }

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                // 从配置中加载值
        }
}