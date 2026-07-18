package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaKnifeItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaShurikenItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.CologneItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumeEssenceItem;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeItem;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeLoadoutService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenLedgerItem;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.FeatherBladeItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShellItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapItem;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonAppleItem;
import dev.doctor4t.wathe.api.event.AllowPlayerPunching;
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
    public static final Identifier TAROT_CARD_ID = SparkWitch.id("tarot_card");
    public static final Identifier NINJA_KNIFE_ID = SparkWitch.id("ninja_knife");
    public static final Identifier VENDETTA_KNIFE_ID = SparkWitch.id("vendetta_knife");
    public static final Identifier NINJA_SHURIKEN_ID = SparkWitch.id("ninja_shuriken");
    public static final Identifier FEATHER_BLADE_ID = SparkWitch.id("feather_blade");
    public static final Identifier BLACK_RAVEN_LEDGER_ID = SparkWitch.id("black_raven_ledger");
    public static final Identifier HUNTER_TRAP_ID = HunterTrapItem.ID;
    public static final Identifier DOUBLE_BARREL_SHOTGUN_ID = DoubleBarrelShotgunItem.ID;
    public static final Identifier DOUBLE_BARREL_SHELL_ID = DoubleBarrelShellItem.ID;
    public static final Identifier POISON_APPLE_ID = SparkWitch.id("poison_apple");
    public static final Identifier TOFANA_ELIXIR_ID = SparkWitch.id("tofana_elixir");
    private static Item ceremonialSword;
    private static Item firePoker;
    private static Item perfumeEssence;
    private static Item cologne;
    private static Item tarotCard;
    private static Item ninjaKnife;
    private static Item vendettaKnife;
    private static Item ninjaShuriken;
    private static Item featherBlade;
    private static Item blackRavenLedger;
    private static Item hunterTrap;
    private static Item doubleBarrelShotgun;
    private static Item doubleBarrelShell;
    private static Item poisonApple;
    private static Item tofanaElixir;

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
        tarotCard = Registry.register(
                Registries.ITEM,
                TAROT_CARD_ID,
                new Item(new Item.Settings().maxCount(1))
        );
        ninjaKnife = Registry.register(
                Registries.ITEM,
                NINJA_KNIFE_ID,
                new NinjaKnifeItem(new Item.Settings().maxCount(1))
        );
        vendettaKnife = Registry.register(
                Registries.ITEM,
                VENDETTA_KNIFE_ID,
                new VendettaKnifeItem(new Item.Settings().maxCount(1))
        );
        ninjaShuriken = Registry.register(
                Registries.ITEM,
                NINJA_SHURIKEN_ID,
                new NinjaShurikenItem(new Item.Settings().maxCount(1))
        );
        featherBlade = Registry.register(
                Registries.ITEM,
                FEATHER_BLADE_ID,
                new FeatherBladeItem(new Item.Settings().maxCount(1))
        );
        blackRavenLedger = Registry.register(
                Registries.ITEM,
                BLACK_RAVEN_LEDGER_ID,
                new BlackRavenLedgerItem(new Item.Settings().maxCount(1))
        );
        hunterTrap = Registry.register(
                Registries.ITEM,
                HUNTER_TRAP_ID,
                new HunterTrapItem(HunterTrapItem.createSettings())
        );
        doubleBarrelShotgun = Registry.register(
                Registries.ITEM,
                DOUBLE_BARREL_SHOTGUN_ID,
                new DoubleBarrelShotgunItem(DoubleBarrelShotgunItem.createSettings())
        );
        doubleBarrelShell = Registry.register(
                Registries.ITEM,
                DOUBLE_BARREL_SHELL_ID,
                new DoubleBarrelShellItem(DoubleBarrelShellItem.createSettings())
        );
        poisonApple = Registry.register(
                Registries.ITEM,
                POISON_APPLE_ID,
                new PoisonAppleItem(new Item.Settings().maxCount(1))
        );
        tofanaElixir = Registry.register(
                Registries.ITEM,
                TOFANA_ELIXIR_ID,
                new Item(new Item.Settings().maxCount(1))
        );
        registerMeleeSuppression();
        VendettaKnifeLoadoutService.register();
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

    public static Item tarotCard() {
        if (tarotCard == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return tarotCard;
    }

    public static Item ninjaKnife() {
        if (ninjaKnife == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ninjaKnife;
    }

    public static Item vendettaKnife() {
        if (vendettaKnife == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return vendettaKnife;
    }

    public static Item ninjaShuriken() {
        if (ninjaShuriken == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ninjaShuriken;
    }

    public static Item featherBlade() {
        if (featherBlade == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return featherBlade;
    }

    public static Item blackRavenLedger() {
        if (blackRavenLedger == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return blackRavenLedger;
    }

    public static Item hunterTrap() {
        if (hunterTrap == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return hunterTrap;
    }

    public static Item doubleBarrelShotgun() {
        if (doubleBarrelShotgun == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return doubleBarrelShotgun;
    }

    public static Item doubleBarrelShell() {
        if (doubleBarrelShell == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return doubleBarrelShell;
    }

    public static Item poisonApple() {
        if (poisonApple == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return poisonApple;
    }

    public static Item tofanaElixir() {
        if (tofanaElixir == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return tofanaElixir;
    }

    private static void registerMeleeSuppression() {
        // Kunai uses Wathe's player-only punching path so its left click keeps the native shove contract.
        // 苦无通过 Wathe 的仅玩家攻击路径处理左键，从而沿用原生击退规则。
        AllowPlayerPunching.EVENT.register((attacker, victim) ->
                attacker.getMainHandStack().isOf(ninjaKnife)
        );
        AllowPlayerPunching.EVENT.register((attacker, victim) ->
                attacker.getMainHandStack().isOf(vendettaKnife)
                        && (attacker == victim
                        ? VendettaInteractionService.isActiveVendetta(attacker)
                        : VendettaInteractionService.isExactPair(attacker, victim))
        );
        // Shuriken kills through its explicit server path and must never fall back to vanilla melee damage.
        // 手里剑只通过明确的服务端路径击杀，不能回退为原版近战伤害。
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            Item heldItem = player.getStackInHand(hand).getItem();
            return heldItem == ninjaShuriken
                    ? ActionResult.FAIL
                    : ActionResult.PASS;
        });
    }
}
