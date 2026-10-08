package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: the rifle's chamber, inserted magazine ({@code null} = no magazine; an empty magazine is
 * {@link UsecMagazineContents#EMPTY}) and suppressor. Stored only in vanilla {@code CUSTOM_DATA} under
 * {@link #NBT_KEY} (no new DataComponentType, like {@code PotionLauncherLoad}); other CUSTOM_DATA keys are preserved.
 * Decoding is tolerant: unknown or malformed fields read as absent. Only the server mutates it.
 * 稳定契约：步枪的弹膛、已装弹匣（{@code null} = 未装弹匣；空弹匣为 {@link UsecMagazineContents#EMPTY}）与消音器。
 * 只存放在原版 {@code CUSTOM_DATA} 的 {@link #NBT_KEY} 下（不新增 DataComponentType，与 {@code PotionLauncherLoad} 相同），
 * 其他 CUSTOM_DATA 键保持不变。解码是宽容的：未知或残缺字段视为不存在。只由服务端修改。
 */
public record UsecRifleState(@Nullable UsecAmmoType chamber, @Nullable UsecMagazineContents magazine,
                             boolean suppressor) {
    /** Stable NBT keys; do not rename. / 稳定的 NBT 键名，不得改名。 */
    public static final String NBT_KEY = "UsecRifle";
    static final String CHAMBER_KEY = "Chamber";
    static final String MAGAZINE_KEY = "Magazine";
    static final String SUPPRESSOR_KEY = "Suppressor";

    public static final UsecRifleState EMPTY = new UsecRifleState(null, null, false);

    public boolean hasMagazine() {
        return magazine != null;
    }

    public boolean isEmpty() {
        return chamber == null && magazine == null && !suppressor;
    }

    public UsecRifleState withChamber(@Nullable UsecAmmoType round) {
        return new UsecRifleState(round, magazine, suppressor);
    }

    public UsecRifleState withMagazine(@Nullable UsecMagazineContents contents) {
        return new UsecRifleState(chamber, contents, suppressor);
    }

    public UsecRifleState withSuppressor(boolean attached) {
        return new UsecRifleState(chamber, magazine, attached);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        if (chamber != null) {
            nbt.putString(CHAMBER_KEY, chamber.id());
        }
        if (magazine != null) {
            nbt.put(MAGAZINE_KEY, magazine.toNbt());
        }
        if (suppressor) {
            nbt.putBoolean(SUPPRESSOR_KEY, true);
        }
        return nbt;
    }

    /** Tolerant decode; anything but a compound reads as {@link #EMPTY}. / 宽容解码；非复合标签一律视为 {@link #EMPTY}。 */
    public static UsecRifleState fromNbt(@Nullable NbtElement element) {
        if (!(element instanceof NbtCompound nbt)) {
            return EMPTY;
        }
        UsecAmmoType chamber = nbt.get(CHAMBER_KEY) instanceof NbtString string
                ? UsecAmmoType.fromId(string.asString()) : null;
        // A list (even an empty one) is an inserted magazine; anything else means no magazine.
        // 列表（即使为空）表示已装弹匣；其他任何形式都表示未装弹匣。
        UsecMagazineContents magazine = nbt.get(MAGAZINE_KEY) instanceof NbtList list
                ? UsecMagazineContents.fromNbt(list) : null;
        boolean suppressor = nbt.getBoolean(SUPPRESSOR_KEY);
        return new UsecRifleState(chamber, magazine, suppressor);
    }

    public static UsecRifleState read(ItemStack rifle) {
        NbtComponent customData = rifle.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return EMPTY;
        }
        return fromNbt(customData.copyNbt().get(NBT_KEY));
    }

    /**
     * Writes the state, dropping the key when the rifle is empty and the component when nothing else is stored.
     * 写入状态；步枪为空时移除该键，没有其他数据时移除整个组件。
     */
    public static void write(ItemStack rifle, @Nullable UsecRifleState state) {
        NbtComponent customData = rifle.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData == null ? new NbtCompound() : customData.copyNbt();
        if (state == null || state.isEmpty()) {
            nbt.remove(NBT_KEY);
        } else {
            nbt.put(NBT_KEY, state.toNbt());
        }
        if (nbt.isEmpty()) {
            rifle.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            rifle.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }
}
