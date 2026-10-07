package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The complete round ledger is private; sync exposes only public sentences.
 * 完整回合账本仅存服务端，同步只包含公开判决状态。 */
public final class JudgeWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<JudgeWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("judge_round"), JudgeWorldComponent.class);
    private static final int MAX_SAVED_PLAYERS = 16384;
    private final World world;
    private final JudgeRoundState state = new JudgeRoundState();
    private final JudgeSelectionSessions sessions = new JudgeSelectionSessions();
    private final Map<UUID, Long> visibleSentences = new HashMap<>();
    private final Set<UUID> initializedJudges = new HashSet<>();
    private Set<UUID> previousViewers = Set.of();

    public JudgeWorldComponent(World world) {
        this.world = world;
    }

    JudgeRoundState state() {
        return state;
    }

    JudgeSelectionSessions sessions() {
        return sessions;
    }

    boolean initializeJudge(UUID player) {
        return initializedJudges.add(player);
    }

    public boolean isSentenced(UUID player) {
        return remainingSentenceTicks(player) > 0;
    }

    public int remainingSentenceTicks(UUID player) {
        if (player == null || GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return 0;
        }
        if (!world.isClient) {
            return state.remainingTicks(player, world.getTime());
        }
        long expiry = visibleSentences.getOrDefault(player, 0L);
        return expiry <= world.getTime() ? 0 : (int) Math.min(JudgeRules.SENTENCE_TICKS, expiry - world.getTime());
    }

    public void sync() {
        if (!world.isClient) {
            KEY.sync(world);
        }
    }

    void clearRound() {
        state.clear();
        sessions.clear();
        visibleSentences.clear();
        initializedJudges.clear();
        previousViewers = Set.of();
        sync();
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        JudgeRuntime.tick(serverWorld, this);
        Set<UUID> viewers = serverWorld.getPlayers().stream()
                .filter(GameFunctions::isPlayerPlayingAndAlive)
                .filter(player -> !player.isSpectator() && !player.isCreative())
                .map(ServerPlayerEntity::getUuid)
                .collect(Collectors.toSet());
        if (!previousViewers.equals(viewers)) {
            previousViewers = Set.copyOf(viewers);
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient.getWorld() == world;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean allowed = recipient.getWorld() == world
                && GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(recipient)
                && !recipient.isSpectator() && !recipient.isCreative();
        Map<UUID, Long> publicSentences = allowed ? state.sentences() : Map.of();
        long now = world.getTime();
        Map<UUID, Long> current = new HashMap<>();
        publicSentences.forEach((player, expiry) -> {
            if (expiry > now) {
                current.put(player, expiry);
            }
        });
        buf.writeVarInt(current.size());
        current.forEach((player, expiry) -> {
            buf.writeUuid(player);
            buf.writeVarInt((int) Math.min(JudgeRules.SENTENCE_TICKS, expiry - now));
        });
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SAVED_PLAYERS || count > buf.readableBytes() / 17) {
            throw new IllegalArgumentException("Invalid Judge sentence view");
        }
        Map<UUID, Long> incoming = new HashMap<>();
        for (int i = 0; i < count; i++) {
            UUID player = buf.readUuid();
            int remaining = buf.readVarInt();
            if (remaining < 0 || remaining > JudgeRules.SENTENCE_TICKS || incoming.containsKey(player)) {
                throw new IllegalArgumentException("Invalid Judge sentence duration");
            }
            incoming.put(player, world.getTime() + remaining);
        }
        visibleSentences.clear();
        visibleSentences.putAll(incoming);
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (!state.active()) {
            tag.remove("Match");
            tag.remove("OpeningParticipants");
            tag.remove("Players");
            return;
        }
        tag.putUuid("Match", state.match());
        tag.putInt("OpeningParticipants", state.openingParticipants());
        NbtList players = new NbtList();
        Map<UUID, Integer> kills = state.kills();
        Map<UUID, Long> sentences = state.sentences();
        for (UUID uuid : state.participants()) {
            NbtCompound player = new NbtCompound();
            player.putUuid("Player", uuid);
            player.putInt("Kills", kills.getOrDefault(uuid, 0));
            player.putLong("SentenceExpiry", sentences.getOrDefault(uuid, 0L));
            players.add(player);
        }
        tag.put("Players", players);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        state.clear();
        sessions.clear();
        visibleSentences.clear();
        initializedJudges.clear();
        previousViewers = Set.of();
        if (world.isClient || !tag.containsUuid("Match")) {
            return;
        }
        NbtList entries = tag.getList("Players", NbtElement.COMPOUND_TYPE);
        if (entries.size() > MAX_SAVED_PLAYERS) {
            return;
        }
        Set<UUID> participants = new HashSet<>();
        Map<UUID, Integer> kills = new HashMap<>();
        Map<UUID, Long> sentences = new HashMap<>();
        for (NbtElement element : entries) {
            NbtCompound player = (NbtCompound) element;
            if (!player.containsUuid("Player")) {
                continue;
            }
            UUID uuid = player.getUuid("Player");
            participants.add(uuid);
            kills.put(uuid, Math.max(0, player.getInt("Kills")));
            sentences.put(uuid, Math.max(0L, player.getLong("SentenceExpiry")));
        }
        int opening = tag.getInt("OpeningParticipants");
        if (opening < 0 || opening > participants.size()) {
            return;
        }
        state.restore(tag.getUuid("Match"), opening, participants, kills, sentences);
    }
}
