package dev.caecorthus.sparkwitch;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
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
                                    SparkWitchItems.shriekGun(),
                                    SparkWitchItems.deepDarkSporeFlask(),
                                    // Potion Gunner (witch) / 药炮手（魔女）
                                    SparkWitchItems.potionLauncher(),
                                    SparkWitchItems.gwDkShell(),
                                    SparkWitchItems.gwAcShell(),
                                    SparkWitchItems.gwMrShell(),
                                    SparkWitchItems.trShell(),
                                    SparkWitchItems.riftGate(),
                                    // Neutral / 中立
                                    SparkWitchItems.firePoker(),
                                    // Civilian / 平民
                                    SparkWitchItems.perfumeEssence(),
                                    SparkWitchItems.cologne(),
                                    SparkWitchItems.tarotCard(),
                                    SparkWitchItems.prophetNecrology(),
                                    SparkWitchItems.vendettaKnife(),
                                    SparkWitchItems.disruptor(),
                                    SparkWitchItems.taser(),
                                    SparkWitchItems.shockDevice(),
                                    SparkWitchItems.holyFlash(),
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
                                    SparkWitchItems.whiteCane(),
                                    SparkWitchItems.comTac(),
                                    // Killer / 杀手
                                    SparkWitchItems.ninjaKnife(),
                                    SparkWitchItems.ninjaShuriken(),
                                    SparkWitchItems.ninjaGrapplingHook(),
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
                                    SparkWitchItems.timeStealerGiftWatch(),
                                    SparkWitchItems.timeStamp()
                            );
                            factionOrder.forEach(entries::add);
                            // Any other item in our namespace follows in registration order, so new items join automatically.
                            // Operator-only items are the exception: they live in vanilla's Operator Utilities tab only.
                            // 本模组命名空间下的其他物品按注册顺序追加在后，新增物品无需再手动加入。
                            // 管理员专用物品例外：只放在原版「管理员用品」物品栏。
                            List<Item> operatorOnly = operatorOnlyItems();
                            for (Item item : Registries.ITEM) {
                                if (SparkWitch.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())
                                        && !factionOrder.contains(item) && !operatorOnly.contains(item)) {
                                    entries.add(item);
                                }
                            }
                        })
                        .build()
        );
        // Vanilla shows this tab only to operators with "Operator Items Tab" on (Wathe's barrier blocks live here too).
        // 原版只向开启「显示管理员用品」的管理员显示该物品栏（Wathe 的屏障方块也在这里）。
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR)
                .register(entries -> operatorOnlyItems().forEach(entries::add));
        registered = true;
    }

    /** SparkWitch items kept out of the SparkWitch tab and listed under Operator Utilities. / 管理员专用物品。 */
    private static List<Item> operatorOnlyItems() {
        return List.of(SparkWitchItems.riftGateRemover());
    }
}
