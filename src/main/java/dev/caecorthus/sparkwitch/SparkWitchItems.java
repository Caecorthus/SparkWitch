package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaKnifeItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaShurikenItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.CologneItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumeEssenceItem;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public final class SparkWitchItems {
    public static final Identifier CEREMONIAL_SWORD_ID = SparkWitch.id("ceremonial_sword");
    public static final Identifier FIRE_POKER_ID = SparkWitch.id("fire_poker");
    public static final Identifier PERFUME_ESSENCE_ID = SparkWitch.id("perfume_essence");
    public static final Identifier COLOGNE_ID = SparkWitch.id("cologne");
    public static final Identifier NINJA_KNIFE_ID = SparkWitch.id("ninja_knife");
    public static final Identifier NINJA_SHURIKEN_ID = SparkWitch.id("ninja_shuriken");
    private static Item ceremonialSword;
    private static Item firePoker;
    private static Item perfumeEssence;
    private static Item cologne;
    private static Item ninjaKnife;
    private static Item ninjaShuriken;

    private static boolean registered;

    private SparkWitchItems() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ceremonialSword = Registry.register(
                Registries.ITEM,
                CEREMONIAL_SWORD_ID,
                new CeremonialSwordItem(CeremonialSwordItem.createSettings())
        );
        firePoker = Registry.register(
                Registries.ITEM,
                FIRE_POKER_ID,
                new Item(new Item.Settings().maxCount(1))
        );
        perfumeEssence = Registry.register(
                Registries.ITEM,
                PERFUME_ESSENCE_ID,
                new PerfumeEssenceItem(new Item.Settings().maxCount(1))
        );
        cologne = Registry.register(
                Registries.ITEM,
                COLOGNE_ID,
                new CologneItem(new Item.Settings().maxCount(1))
        );
        ninjaKnife = Registry.register(
                Registries.ITEM,
                NINJA_KNIFE_ID,
                new NinjaKnifeItem(new Item.Settings().maxCount(1))
        );
        ninjaShuriken = Registry.register(
                Registries.ITEM,
                NINJA_SHURIKEN_ID,
                new NinjaShurikenItem(new Item.Settings().maxCount(1))
        );
        registerMeleeSuppression();
        registered = true;
    }

    public static Item ceremonialSword() {
        if (ceremonialSword == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ceremonialSword;
    }

    public static Item firePoker() {
        if (firePoker == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return firePoker;
    }

    public static Item perfumeEssence() {
        if (perfumeEssence == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return perfumeEssence;
    }

    public static Item cologne() {
        if (cologne == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return cologne;
    }

    public static Item ninjaKnife() {
        if (ninjaKnife == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ninjaKnife;
    }

    public static Item ninjaShuriken() {
        if (ninjaShuriken == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ninjaShuriken;
    }

    private static void registerMeleeSuppression() {
        // These weapons kill through explicit server paths and must never fall back to vanilla melee damage.
        // 这两件武器只通过明确的服务端路径击杀，不能回退为原版近战伤害。
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            Item heldItem = player.getStackInHand(hand).getItem();
            return heldItem == ninjaKnife || heldItem == ninjaShuriken
                    ? ActionResult.FAIL
                    : ActionResult.PASS;
        });
    }
}
