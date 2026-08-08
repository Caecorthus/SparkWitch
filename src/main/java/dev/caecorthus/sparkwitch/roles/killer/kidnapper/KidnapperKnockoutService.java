package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthCoronerCompat;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** 绑架者迷药的发放、商店、死亡奖励、清理与回放注册。 */
public final class KidnapperKnockoutService {
    private static boolean registered;

    private KidnapperKnockoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        BuildShopEntries.EVENT.register(KidnapperKnockoutService::buildShopEntries);
        RoleAssigned.EVENT.register(KidnapperKnockoutService::assignForRole);
        ResetPlayer.EVENT.register(player -> KidnapperControlComponent.KEY.get(player).resetAll());
        KillPlayer.AFTER.register(KidnapperKnockoutService::afterKill);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                KidnapperControlComponent.KEY.get(handler.getPlayer()).reset());
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> clearWorld(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> clearWorld(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                server.getWorlds().forEach(KidnapperKnockoutService::clearWorld));
        registerReplayFormatters();
    }

    public static boolean canUseKnockoutDrug(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return KidnapperRules.isKidnapper(role) || SparkStrengthCoronerCompat.hasKidnapperDisguise(player);
    }

    public static ShopEntry shopEntry() {
        return shopEntry(null);
    }

    public static ShopEntry shopEntry(@Nullable PlayerEntity player) {
        ShopEntry entry = buildBaseShopEntry();
        /*
         * 这里把条目交给 SparkTraits 的公共折扣 API 处理。
         * 这样当绑架者自己拥有【魅力】时，商店里显示的价格和最终购买价都会一致地打折。
         */
        return SparkTraitsCharismaBridge.discountShopEntry(player, entry);
    }

    private static ShopEntry buildBaseShopEntry() {
        return new ShopEntry.Builder(
                KidnapperRules.KNOCKOUT_DRUG_ENTRY_ID,
                displayStack(),
                KidnapperRules.KNOCKOUT_DRUG_PRICE,
                ShopEntry.Type.POISON
        ).actualStack(SparkWitchItems.knockoutDrug().getDefaultStack()).build();
    }

    public static ItemStack displayStack() {
        ItemStack stack = SparkWitchItems.knockoutDrug().getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.sparkwitch.knockout_drug"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkwitch.knockout_drug.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }

    public static void addKnockoutDrugShopEntry(BuildShopEntries.ShopContext context, int index) {
        addKnockoutDrugShopEntry(context, index, null);
    }

    public static void addKnockoutDrugShopEntry(BuildShopEntries.ShopContext context, int index, @Nullable PlayerEntity player) {
        context.addEntry(Math.max(0, Math.min(index, context.size())), shopEntry(player));
    }

    private static void assignForRole(PlayerEntity player, Role role) {
        KidnapperControlComponent component = KidnapperControlComponent.KEY.get(player);
        component.resetAll();
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !KidnapperRules.isKidnapper(role)) {
            return;
        }

        /*
         * 真实绑架者开局自带一瓶迷药，并立即进入 40 秒开局冷却。
         * 这只处理 SparkWitch 的真实绑架者；验尸官伪装的临时迷药由 SparkStrength 通过软兼容发放。
         */
        serverPlayer.giveItemStack(SparkWitchItems.knockoutDrug().getDefaultStack());
        serverPlayer.getItemCooldownManager().set(
                SparkWitchItems.knockoutDrug(),
                KidnapperRules.KNOCKOUT_DRUG_START_COOLDOWN_TICKS
        );
        component.startRoundCooldowns();
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!KidnapperRules.isKidnapper(role)) {
            return;
        }

        /*
         * 绑架者继承 Wathe 默认杀手商店，只替换毒药槽：
         * 删除毒药瓶与蝎子，并在原毒药瓶位置放入 SparkWitch 自己的迷药。
         */
        int poisonIndex = findEntryIndex(context, "poison_vial");
        context.getEntries().removeIf(entry ->
                "poison_vial".equals(entry.id()) || "scorpion".equals(entry.id()));
        int insertIndex = poisonIndex >= 0 ? Math.min(poisonIndex, context.size()) : context.size();
        addKnockoutDrugShopEntry(context, insertIndex, player);
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        KidnapperControlComponent victimControl = KidnapperControlComponent.KEY.get(victim);
        if (killer != null
                && !killer.getUuid().equals(victim.getUuid())
                && victimControl.isControlledBy(killer)
                && canUseKnockoutDrug(killer)) {
            PlayerShopComponent.KEY.get(killer).addToBalance(KidnapperRules.CONTROLLED_KILL_REWARD);
            killer.sendMessage(Text.translatable(
                    "message.sparkwitch.kidnapper.controlled_kill_reward",
                    KidnapperRules.CONTROLLED_KILL_REWARD
            ).withColor(KidnapperRules.COLOR), true);
        }
        victimControl.reset();
    }

    private static int findEntryIndex(BuildShopEntries.ShopContext context, String id) {
        for (int i = 0; i < context.size(); i++) {
            if (id.equals(context.getEntry(i).id())) {
                return i;
            }
        }
        return -1;
    }

    private static void clearWorld(net.minecraft.world.World world) {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getPlayers().forEach(player -> KidnapperControlComponent.KEY.get(player).resetAll());
        }
    }

    private static void registerReplayFormatters() {
        ReplayRegistry.registerItemUseFormatter(KidnapperRules.KNOCKOUT_DRUG_ID, KidnapperKnockoutService::formatDrugUse);
        ReplayRegistry.registerSkillFormatter(KidnapperRules.RELEASE_EVENT_ID, KidnapperKnockoutService::formatRelease);
    }

    private static @Nullable Text formatDrugUse(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        if (!event.data().containsUuid("actor") || !event.data().containsUuid("target")) {
            return null;
        }
        return Text.translatable(
                "replay.item_use.sparkwitch.knockout_drug",
                ReplayGenerator.formatPlayerName(event.data().getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(event.data().getUuid("target"), playerInfo)
        );
    }

    private static @Nullable Text formatRelease(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(event.data().getUuid("actor"), playerInfo);
        if (event.data().containsUuid("target")) {
            return Text.translatable(
                    "replay.skill_use.sparkwitch.kidnapper.release",
                    actor,
                    ReplayGenerator.formatPlayerName(event.data().getUuid("target"), playerInfo)
            );
        }
        return Text.translatable("replay.skill_use.sparkwitch.kidnapper.release_end", actor);
    }
}
