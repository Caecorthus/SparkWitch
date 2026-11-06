package dev.caecorthus.sparkwitch.roles.civilian.bear.headset;

import net.minecraft.item.Item;

/**
 * CENS ProFlex DX5 tactical earplugs ({@code sparkwitch:cens_proflex_dx5}): hearing +50%, additive with the Rys-T
 * (E2), only while worn in the headset slot ({@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}). A BEAR item, so
 * it is deleted on its holder's death (D10). WP0 stub: the slot, the insert sound and the volume hooks are WP4.
 * CENS ProFlex DX5 战术耳塞（{@code sparkwitch:cens_proflex_dx5}）：听力 +50%，与 Rys-T 叠加（E2），仅在戴于耳机槽
 * （{@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}）时生效。属于 BEAR 物品，持有者死亡时删除（D10）。WP0 桩：
 * 槽位、佩戴音效与音量钩子归 WP4。
 */
public class CensEarplugsItem extends Item {
    public CensEarplugsItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }
}
