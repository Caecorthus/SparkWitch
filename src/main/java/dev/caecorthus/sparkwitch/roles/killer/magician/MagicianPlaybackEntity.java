package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * 魔术师可见皮套；原版伤害全部免疫，武器包由管理器显式收束。
 * The owner (the Magician) is a server-only field and never tracked: a tracked owner UUID would let any client name the
 * Magician from its puppet. Clients see only the copied player's UUID and name.
 * 主人（魔术师）是仅服务端字段且从不追踪同步：同步主人 UUID 会让任何客户端从皮套认出魔术师。客户端只能看到被复制玩家的 UUID 与名字。
 */
public class MagicianPlaybackEntity extends LivingEntity {
    private static final TrackedData<Optional<UUID>> DISGUISE = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> NAME = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> USING = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ACTIVE_OFF_HAND = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> USE_LEFT = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SITTING = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> SWING_SEQUENCE = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SWING_OFF_HAND = DataTracker.registerData(MagicianPlaybackEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
    private @Nullable UUID owner;
    public MagicianPlaybackEntity(EntityType<? extends LivingEntity> type, World world) { super(type, world); noClip = true; setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder b) { super.initDataTracker(b); b.add(DISGUISE, Optional.empty()); b.add(NAME, ""); b.add(USING, false); b.add(ACTIVE_OFF_HAND, false); b.add(USE_LEFT, 0); b.add(SITTING, false); b.add(SWING_SEQUENCE, 0); b.add(SWING_OFF_HAND, false); }
    public void setIdentity(@Nullable UUID owner, @Nullable UUID disguise, String name) { this.owner = owner; dataTracker.set(DISGUISE, Optional.ofNullable(disguise)); dataTracker.set(NAME, name == null ? "" : name); }
    /** Server only; always null on the client. / 仅服务端；客户端上恒为 null。 */
    public @Nullable UUID owner() { return owner; }
    public UUID disguise() { return dataTracker.get(DISGUISE).orElse(null); }
    public String disguiseName() { return dataTracker.get(NAME); }
    public void clearEquipment() { main=ItemStack.EMPTY; off=ItemStack.EMPTY; for(int i=0;i<armor.length;i++) armor[i]=ItemStack.EMPTY; }
    public void setReplayUseState(boolean using, @Nullable Hand hand) { dataTracker.set(USING, using && hand != null); dataTracker.set(ACTIVE_OFF_HAND, hand == Hand.OFF_HAND); }
    public boolean isReplayUsingItem() { return dataTracker.get(USING); }
    public Hand getReplayActiveHand() { return dataTracker.get(ACTIVE_OFF_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND; }
    public void setReplayItemUseTimeLeft(int ticks) { dataTracker.set(USE_LEFT, Math.max(0, ticks)); }
    public int getReplayItemUseTimeLeft() { return Math.max(0, dataTracker.get(USE_LEFT)); }
    public void setReplaySitting(boolean sitting) { dataTracker.set(SITTING, sitting); }
    public boolean isReplaySitting() { return dataTracker.get(SITTING); }
    public void playReplaySwing(Hand hand) { dataTracker.set(SWING_OFF_HAND, hand == Hand.OFF_HAND); dataTracker.set(SWING_SEQUENCE, dataTracker.get(SWING_SEQUENCE) + 1); swingHand(hand); }
    public Hand getReplaySwingHand() { return dataTracker.get(SWING_OFF_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND; }
    // 自定义 LivingEntity 不会自动继承玩家的使用状态；客户端渲染时补齐，服务端仍保持真实状态。
    @Override public boolean isUsingItem() { return getWorld().isClient() && isReplayUsingItem() || super.isUsingItem(); }
    @Override public Hand getActiveHand() { return getWorld().isClient() && isReplayUsingItem() ? getReplayActiveHand() : super.getActiveHand(); }
    @Override public ItemStack getActiveItem() { return getWorld().isClient() && isReplayUsingItem() ? getStackInHand(getReplayActiveHand()) : super.getActiveItem(); }
    @Override public int getItemUseTimeLeft() { return getWorld().isClient() && isReplayUsingItem() ? getReplayItemUseTimeLeft() : super.getItemUseTimeLeft(); }
    @Override public int getItemUseTime() { return getWorld().isClient() && isReplayUsingItem() ? Math.max(0, getActiveItem().getMaxUseTime(this) - getReplayItemUseTimeLeft()) : super.getItemUseTime(); }
    @Override public boolean damage(DamageSource source, float amount) { if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) { discard(); return true; } return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return !source.isOf(DamageTypes.GENERIC_KILL) && !source.isOf(DamageTypes.OUT_OF_WORLD); }
    @Override public boolean canTakeDamage() { return false; }
    @Override public void kill() { discard(); }
    @Override public boolean isPushedByFluids() { return false; }
    @Override protected void pushAway(Entity entity) {}
    @Override public boolean isPushable() { return false; }
    @Override public Iterable<ItemStack> getArmorItems() { return java.util.List.of(armor); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return switch(slot) { case MAINHAND -> main; case OFFHAND -> off; case FEET -> armor[0]; case LEGS -> armor[1]; case CHEST -> armor[2]; case HEAD -> armor[3]; default -> ItemStack.EMPTY; }; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) { ItemStack c=stack.copy(); switch(slot) { case MAINHAND -> main=c; case OFFHAND -> off=c; case FEET -> armor[0]=c; case LEGS -> armor[1]=c; case CHEST -> armor[2]=c; case HEAD -> armor[3]=c; default -> {} } }
    @Override public Arm getMainArm() { return Arm.RIGHT; }
    @Override public boolean shouldSave() { return false; }
    public static DefaultAttributeContainer.Builder createAttributes() { return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 999999.0); }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) { if(owner()!=null) nbt.putUuid("Owner", owner()); if(disguise()!=null) nbt.putUuid("Disguise", disguise()); nbt.putString("Name", disguiseName()); }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) { setIdentity(nbt.containsUuid("Owner")?nbt.getUuid("Owner"):null, nbt.containsUuid("Disguise")?nbt.getUuid("Disguise"):null, nbt.getString("Name")); }
}
