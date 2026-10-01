package dev.caecorthus.sparkwitch;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

import java.util.List;

public final class SparkWitchItemGroups {
    // Registry id sparkwitch:items stays stable; other mods should key on the id, not on this class.
    // 注册 ID sparkwitch:items 保持稳定；其他模组应按该 ID 引用，而非依赖本类。
    public static final RegistryKey<ItemGroup> ITEMS_GROUP =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, SparkWitch.id("items"));
    private static boolean registered;

    private SparkWitchItemGroups() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Registry.register(
                Registries.ITEM_GROUP,
                ITEMS_GROUP,
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.sparkwitch.items"))
                        .icon(() -> new ItemStack(SparkWitchItems.poisonApple()))
                        .entries((displayContext, entries) -> {
                            // Default stacks only: the creative search index builds their tooltips off-thread with a null player.
                            // 只列出默认物品堆：创造模式搜索索引会在后台线程以空玩家构建其提示文本。
                            List<Item> factionOrder = List.of(
                                    // Witch / 魔女
                                    SparkWitchItems.ceremonialSword(),
                                    // Potion Gunner (witch) / 药炮手（魔女）
                                    SparkWitchItems.potionLauncher(),
                                    SparkWitchItems.gwDkShell(),
                                    SparkWitchItems.gwAcShell(),
                                    SparkWitchItems.gwMrShell(),
                                    SparkWitchItems.trShell(),
                                    // Neutral / 中立
                                    SparkWitchItems.firePoker(),
                                    // Civilian / 平民
                                    SparkWitchItems.perfumeEssence(),
                                    SparkWitchItems.cologne(),
                                    SparkWitchItems.tarotCard(),
                                    SparkWitchItems.vendettaKnife(),
                                    SparkWitchItems.disruptor(),
                                    SparkWitchItems.taser(),
                                    SparkWitchItems.shockDevice(),
                                    SparkWitchItems.seekerCar(),
                                    SparkWitchItems.seekerCamera(),
                                    SparkWitchItems.fishingRod(),
                                    SparkWitchItems.fishBait(),
                                    SparkWitchItems.salmon(),
                                    SparkWitchItems.cod(),
                                    SparkWitchItems.clownfish(),
                                    SparkWitchItems.goldfish(),
                                    SparkWitchItems.keyFish(),
                                    SparkWitchItems.swordfish(),
                                    SparkWitchItems.glimmerfish(),
                                    // Killer / 杀手
                                    SparkWitchItems.ninjaKnife(),
                                    SparkWitchItems.ninjaShuriken(),
                                    SparkWitchItems.featherBlade(),
                                    SparkWitchItems.blackRavenLedger(),
                                    SparkWitchItems.blackRavenMask(),
                                    SparkWitchItems.poisonApple(),
                                    SparkWitchItems.tofanaElixir(),
                                    SparkWitchItems.hunterTrap(),
                                    SparkWitchItems.doubleBarrelShotgun(),
                                    SparkWitchItems.doubleBarrelShell(),
                                    SparkWitchItems.knockoutDrug(),
                                    SparkWitchItems.tollBell(),
                                    SparkWitchItems.timeStealerClock(),
                                    SparkWitchItems.timeStamp()
                            );
                            factionOrder.forEach(entries::add);
                            // Any other item in our namespace follows in registration order, so new items join automatically.
                            // 本模组命名空间下的其他物品按注册顺序追加在后，新增物品无需再手动加入。
                            for (Item item : Registries.ITEM) {
                                if (SparkWitch.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())
                                        && !factionOrder.contains(item)) {
                                    entries.add(item);
                                }
                            }
                        })
                        .build()
        );
        registered = true;
    }
}
