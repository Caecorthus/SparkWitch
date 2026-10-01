package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaKnifeItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaShurikenItem;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.DisruptorItem;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ShockDeviceItem;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.TaserItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FishBaitItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishKind;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FishingRodItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.KeyFishItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.CologneItem;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraItem;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarItem;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumeEssenceItem;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeItem;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeLoadoutService;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.TollBellItem;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenLedgerItem;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.FeatherBladeItem;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenMaskItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShellItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapItem;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KnockoutDrugItem;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampItem;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerClockItem;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonAppleItem;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun.ShriekGunItem;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkSporeFlaskItem;
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
    public static final Identifier BLACK_RAVEN_MASK_ID = BlackRavenDisguiseRules.MASK_ITEM_ID;
    public static final Identifier HUNTER_TRAP_ID = HunterTrapItem.ID;
    public static final Identifier DOUBLE_BARREL_SHOTGUN_ID = DoubleBarrelShotgunItem.ID;
    public static final Identifier DOUBLE_BARREL_SHELL_ID = DoubleBarrelShellItem.ID;
    public static final Identifier POISON_APPLE_ID = SparkWitch.id("poison_apple");
    public static final Identifier TOFANA_ELIXIR_ID = SparkWitch.id("tofana_elixir");
    public static final Identifier TOLL_BELL_ID = SparkWitch.id("toll_bell");
    public static final Identifier TIME_STEALER_CLOCK_ID = TimeStealerRules.CLOCK_ID;
    public static final Identifier TIME_STAMP_ID = TimeStealerRules.STAMP_ID;
    public static final Identifier KNOCKOUT_DRUG_ID = SparkWitch.id("knockout_drug");
    public static final Identifier DISRUPTOR_ID = ControlExpertRules.DISRUPTOR_ID;
    public static final Identifier TASER_ID = ControlExpertRules.TASER_ID;
    public static final Identifier SHOCK_DEVICE_ID = ControlExpertRules.SHOCK_DEVICE_ID;
    public static final Identifier SEEKER_CAR_ID = SeekerRules.CAR_ITEM_ID;
    public static final Identifier SEEKER_CAMERA_ID = SeekerRules.CAMERA_ITEM_ID;
    public static final Identifier FISHING_ROD_ID = FisherRules.FISHING_ROD_ID;
    public static final Identifier FISH_BAIT_ID = FisherRules.BAIT_ID;
    public static final Identifier SALMON_ID = FisherRules.SALMON_ID;
    public static final Identifier COD_ID = FisherRules.COD_ID;
    public static final Identifier CLOWNFISH_ID = FisherRules.CLOWNFISH_ID;
    public static final Identifier GOLDFISH_ID = FisherRules.GOLDFISH_ID;
    public static final Identifier KEY_FISH_ID = FisherRules.KEY_FISH_ID;
    public static final Identifier SWORDFISH_ID = FisherRules.SWORDFISH_ID;
    public static final Identifier GLIMMERFISH_ID = FisherRules.GLIMMERFISH_ID;
    public static final Identifier SHRIEK_GUN_ID = AbyssListenerRules.GUN_ITEM_ID;
    public static final Identifier DEEP_DARK_SPORE_FLASK_ID = AbyssListenerRules.FLASK_ITEM_ID;
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
    private static Item blackRavenMask;
    private static Item hunterTrap;
    private static Item doubleBarrelShotgun;
    private static Item doubleBarrelShell;
    private static Item poisonApple;
    private static Item tofanaElixir;
    private static Item tollBell;
    private static Item timeStealerClock;
    private static Item timeStamp;
    private static Item knockoutDrug;
    private static Item disruptor;
    private static Item taser;
    private static Item shockDevice;
    private static Item seekerCar;
    private static Item seekerCamera;
    private static Item fishingRod;
    private static Item fishBait;
    private static Item salmon;
    private static Item cod;
    private static Item clownfish;
    private static Item goldfish;
    private static Item keyFish;
    private static Item swordfish;
    private static Item glimmerfish;
    private static Item shriekGun;
    private static Item deepDarkSporeFlask;

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
        blackRavenMask = Registry.register(
                Registries.ITEM,
                BLACK_RAVEN_MASK_ID,
                new BlackRavenMaskItem(new Item.Settings().maxCount(1))
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
        tollBell = Registry.register(
                Registries.ITEM,
                TOLL_BELL_ID,
                new TollBellItem(TollBellItem.createSettings())
        );
        timeStealerClock = Registry.register(
                Registries.ITEM,
                TIME_STEALER_CLOCK_ID,
                new TimeStealerClockItem(TimeStealerClockItem.createSettings())
        );
        timeStamp = Registry.register(
                Registries.ITEM,
                TIME_STAMP_ID,
                new TimeStampItem(TimeStampItem.createSettings())
        );
        knockoutDrug = Registry.register(
                Registries.ITEM,
                KNOCKOUT_DRUG_ID,
                new KnockoutDrugItem(new Item.Settings().maxCount(4))
        );
        disruptor = Registry.register(
                Registries.ITEM,
                DISRUPTOR_ID,
                new DisruptorItem(DisruptorItem.createSettings())
        );
        taser = Registry.register(
                Registries.ITEM,
                TASER_ID,
                new TaserItem(TaserItem.createSettings())
        );
        shockDevice = Registry.register(
                Registries.ITEM,
                SHOCK_DEVICE_ID,
                new ShockDeviceItem(ShockDeviceItem.createSettings())
        );
        seekerCar = Registry.register(
                Registries.ITEM,
                SEEKER_CAR_ID,
                new SeekerCarItem(SeekerCarItem.createSettings())
        );
        seekerCamera = Registry.register(
                Registries.ITEM,
                SEEKER_CAMERA_ID,
                new SeekerCameraItem(SeekerCameraItem.createSettings())
        );
        fishingRod = Registry.register(
                Registries.ITEM,
                FISHING_ROD_ID,
                new FishingRodItem(FishingRodItem.createSettings())
        );
        fishBait = Registry.register(
                Registries.ITEM,
                FISH_BAIT_ID,
                new FishBaitItem(FishBaitItem.createSettings())
        );
        salmon = Registry.register(
                Registries.ITEM,
                SALMON_ID,
                new FisherFishItem(FisherFishItem.createSettings(), FisherFishKind.SALMON)
        );
        cod = Registry.register(
                Registries.ITEM,
                COD_ID,
                new FisherFishItem(FisherFishItem.createSettings(), FisherFishKind.COD)
        );
        clownfish = Registry.register(
                Registries.ITEM,
                CLOWNFISH_ID,
                new FisherFishItem(FisherFishItem.createSettings(), FisherFishKind.CLOWNFISH)
        );
        goldfish = Registry.register(
                Registries.ITEM,
                GOLDFISH_ID,
                new FisherFishItem(FisherFishItem.createSettings(), FisherFishKind.GOLDFISH)
        );
        keyFish = Registry.register(
                Registries.ITEM,
                KEY_FISH_ID,
                new KeyFishItem(KeyFishItem.createSettings())
        );
        swordfish = Registry.register(
                Registries.ITEM,
                SWORDFISH_ID,
                new SwordfishItem(SwordfishItem.createSettings())
        );
        glimmerfish = Registry.register(
                Registries.ITEM,
                GLIMMERFISH_ID,
                new FisherFishItem(FisherFishItem.createSettings(), FisherFishKind.GLIMMERFISH)
        );
        shriekGun = Registry.register(
                Registries.ITEM,
                SHRIEK_GUN_ID,
                new ShriekGunItem(ShriekGunItem.createSettings())
        );
        deepDarkSporeFlask = Registry.register(
                Registries.ITEM,
                DEEP_DARK_SPORE_FLASK_ID,
                new DeepDarkSporeFlaskItem(DeepDarkSporeFlaskItem.createSettings())
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

    public static Item blackRavenMask() {
        if (blackRavenMask == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return blackRavenMask;
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

    public static Item tollBell() {
        if (tollBell == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return tollBell;
    }

    public static Item timeStealerClock() {
        if (timeStealerClock == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return timeStealerClock;
    }

    public static Item timeStamp() {
        if (timeStamp == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return timeStamp;
    }

    public static Item knockoutDrug() {
        if (knockoutDrug == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return knockoutDrug;
    }

    public static Item disruptor() {
        if (disruptor == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return disruptor;
    }

    public static Item taser() {
        if (taser == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return taser;
    }

    public static Item shockDevice() {
        if (shockDevice == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return shockDevice;
    }

    public static Item seekerCar() {
        if (seekerCar == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return seekerCar;
    }

    public static Item seekerCamera() {
        if (seekerCamera == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return seekerCamera;
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

    public static Item fishingRod() {
        if (fishingRod == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return fishingRod;
    }

    public static Item fishBait() {
        if (fishBait == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return fishBait;
    }

    public static Item salmon() {
        if (salmon == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return salmon;
    }

    public static Item cod() {
        if (cod == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return cod;
    }

    public static Item clownfish() {
        if (clownfish == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return clownfish;
    }

    public static Item goldfish() {
        if (goldfish == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return goldfish;
    }

    public static Item keyFish() {
        if (keyFish == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return keyFish;
    }

    public static Item swordfish() {
        if (swordfish == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return swordfish;
    }

    public static Item glimmerfish() {
        if (glimmerfish == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return glimmerfish;
    }

    public static Item shriekGun() {
        if (shriekGun == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return shriekGun;
    }

    public static Item deepDarkSporeFlask() {
        if (deepDarkSporeFlask == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return deepDarkSporeFlask;
    }
}
