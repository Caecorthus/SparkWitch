package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Marks hostile Saint killers and triggers their UUID-bound Karma after confirmed actions.
 * 标记击杀业火圣徒的敌对玩家，并在已确认成功的动作后触发其 UUID 绑定业障。
 */
public final class SaintKarmaService {
    private SaintKarmaService() {
    }

    public static void mark(ServerPlayerEntity player) {
        if (SaintRules.isKarmaImmune(currentRole(player))) {
            return;
        }
        WitchWorldComponent worldComponent = WitchWorldComponent.KEY.get(player.getServerWorld());
        worldComponent.markSaintKarma(player.getUuid());
        updatePlayerMirror(player, true, worldComponent.getSaintKarmaTicks(player.getUuid()));
        sendBell(player, 1.0F);
        sendBell(player, 0.97F);
        player.sendMessage(Text.translatable("message.sparkwitch.saint.karma.marked"), true);
    }

    public static void trigger(ServerPlayerEntity player) {
        WitchWorldComponent worldComponent = WitchWorldComponent.KEY.get(player.getServerWorld());
        if (!worldComponent.hasSaintKarma(player.getUuid())) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        Role role = gameComponent.getRole(player);
        if (SaintRules.isKarmaImmune(role)) {
            updatePlayerMirror(player, true, 0);
            return;
        }
        int remainingTicks = worldComponent.triggerSaintKarma(player.getUuid(), SaintRules.karmaFor(role));
        SaintKarmaCooldownService.apply(player, remainingTicks);
        updatePlayerMirror(player, true, remainingTicks);
        player.sendMessage(
                Text.translatable("message.sparkwitch.saint.karma.triggered", seconds(remainingTicks)),
                true
        );
    }

    /**
     * NoellesRoles records these calls only after a timed-bomb transfer or poison-needle hit succeeds.
     * NoellesRoles 只会在定时炸弹传递或毒针命中成功后记录这些调用。
     */
    public static void onRecordedItemUse(
            ServerPlayerEntity actor,
            Identifier itemId,
            ServerPlayerEntity target,
            NbtCompound extra
    ) {
        String action = extra == null ? null : extra.getString("action");
        if (SaintRules.isKarmaRecordTrigger(itemId, action)) {
            trigger(actor);
        }
    }

    private static Role currentRole(ServerPlayerEntity player) {
        return GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
    }

    private static void sendBell(ServerPlayerEntity player, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                RegistryEntry.of(SparkWitchSounds.SAINT_BELL),
                SoundCategory.PLAYERS,
                player.getX(),
                player.getY(),
                player.getZ(),
                5.0F,
                pitch,
                player.getWorld().random.nextLong()
        ));
    }

    private static int seconds(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0));
    }

    static void updatePlayerMirror(ServerPlayerEntity player, boolean marked, int remainingTicks) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (component.getSaintState().updateKarma(marked, remainingTicks)) {
            component.sync();
        }
    }
}
