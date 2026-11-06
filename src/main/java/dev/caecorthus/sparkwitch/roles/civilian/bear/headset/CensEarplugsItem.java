package dev.caecorthus.sparkwitch.roles.civilian.bear.headset;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * CENS ProFlex DX5 tactical earplugs ({@code sparkwitch:cens_proflex_dx5}): hearing +50%, additive with the Rys-T
 * (E2), only while worn in the headset slot ({@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}). A BEAR item, so
 * it is deleted on its holder's death (D10). Right-clicking them in hand puts them into an EMPTY headset slot (the
 * server moves them and plays the insert sound to the wearer only); with the slot taken the click passes. They can
 * also be placed there by hand or shift-click in any inventory screen.
 * CENS ProFlex DX5 战术耳塞（{@code sparkwitch:cens_proflex_dx5}）：听力 +50%，与 Rys-T 叠加（E2），仅在戴于耳机槽
 * （{@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}）时生效。属于 BEAR 物品，持有者死亡时删除（D10）。手持右键会把它
 * 放入空的耳机槽（由服务端移动，并只对佩戴者播放戴上音效）；耳机槽已占用时该点击放行。也可在任意背包界面中手动或 Shift 点击放入。
 */
public class CensEarplugsItem extends Item {
    private static final float INSERT_VOLUME = 1.0F;
    private static final float INSERT_PITCH = 1.0F;

    public CensEarplugsItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (!HeadsetSlots.insertFromHand(user, hand)) {
            return TypedActionResult.pass(held);
        }
        if (!world.isClient()) {
            user.playSoundToPlayer(SparkWitchSounds.BEAR_EARPLUGS_INSERT, SoundCategory.PLAYERS, INSERT_VOLUME,
                    INSERT_PITCH);
        }
        return TypedActionResult.success(held, world.isClient());
    }
}
