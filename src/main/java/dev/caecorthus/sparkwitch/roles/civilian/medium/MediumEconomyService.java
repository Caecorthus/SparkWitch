package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Medium coins, the USEC/Accountant task-pay model (2026-11-10): the balance is overwritten with
 * {@link MediumRules#INITIAL_MONEY} for every living final Medium in its own {@code ON_FINISH_INITIALIZE} phase
 * (after the default phase, so roles are final), +{@link MediumRules#TASK_REWARD} per completed task (never for a
 * SparkTraits Impostor or an unknown answer, through {@link SparkTraitsTaskIncomeBridge}: SparkTraits pays an
 * Impostor's task income itself, the Accountant/BEAR precedent). No passive income. Income goes through
 * {@code addToBalance} so income observers (SparkFactionAPI's income record, SparkTraits Snowball) see ordinary
 * income; the round-start overwrite is the only {@code setBalance}. {@code CanSeeMoney} answers ALLOW for a living
 * Medium, so the money HUD shows. Server is the only writer; the client only reads {@code CanSeeMoney}.
 * 通灵师金币，沿用 USEC/会计的任务收入模式（2026-11-10）：在自有 {@code ON_FINISH_INITIALIZE} 阶段（排在默认阶段之后，
 * 此时职业已是最终职业）把每名存活的最终通灵师的余额覆盖为 {@link MediumRules#INITIAL_MONEY}；每完成一个任务
 * +{@link MediumRules#TASK_REWARD}（SparkTraits 内鬼或无法判定时不发，经 {@link SparkTraitsTaskIncomeBridge}：内鬼的
 * 任务收入由 SparkTraits 自行支付，沿用会计/BEAR 先例）。无被动收入。收入经 {@code addToBalance} 发放，收入观察者
 * （SparkFactionAPI 收入记录、SparkTraits 滚雪球）视其为普通收入；开局覆盖是唯一的 {@code setBalance}。
 * {@code CanSeeMoney} 对存活的通灵师返回 ALLOW，使金币 HUD 显示。只有服务端写入；客户端只读取 {@code CanSeeMoney}。
 */
final class MediumEconomyService {
	/** The Medium's own ON_FINISH_INITIALIZE phase, after the default one. / 通灵师自有的 ON_FINISH_INITIALIZE 阶段，排在默认阶段之后。 */
	static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("medium_economy_finish_initialize");

	private static boolean registered;

	private MediumEconomyService() {
	}

	static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
		CanSeeMoney.EVENT.register(MediumEconomyService::canSeeMoney);
		GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
		GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
			if (world instanceof ServerWorld serverWorld) {
				initializeBalances(serverWorld, game);
			}
		});
	}

	// ---- Start ----

	static void initializeBalances(ServerWorld world, GameWorldComponent game) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			// Still STARTING here, so the running-state helper would reject everyone. / 此时仍为 STARTING，运行态判定会拒绝所有人。
			if (receivesStartingBalance(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
					game.getRole(player))) {
				PlayerShopComponent.KEY.get(player).setBalance(MediumRules.INITIAL_MONEY);
			}
		}
	}

	/** Only a living final Medium with a role. / 只有拥有身份且存活的最终通灵师。 */
	static boolean receivesStartingBalance(boolean hasRole, boolean dead, @Nullable Role finalRole) {
		return hasRole && !dead && MediumRules.isMedium(finalRole);
	}

	// ---- Tasks ----

	static void onTaskComplete(ServerPlayerEntity player) {
		if (canEarn(player) && MediumRules.receivesTaskMoney(SparkTraitsTaskIncomeBridge.isImpostor(player))) {
			PlayerShopComponent.KEY.get(player).addToBalance(MediumRules.TASK_REWARD);
		}
	}

	// ---- Eligibility ----

	static boolean canEarn(@Nullable PlayerEntity player) {
		if (player == null) {
			return false;
		}
		GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
		Role role = game.getRole(player);
		// Cheap role test first: this runs on every task completion for every player. / 先做廉价的职业判断：每次任务完成都会执行。
		if (!MediumRules.isMedium(role)) {
			return false;
		}
		return MediumRules.canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
				GameFunctions.isPlayerPlayingAndAlive(player), role, player.isSpectator(), player.isCreative(),
				SparkWitchApi.isWraithRestricted(player));
	}

	// ---- Visibility ----

	static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
		return MediumRules.isMedium(role) ? CanSeeMoney.Result.ALLOW : null;
	}

	private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
		if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
			return null;
		}
		return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
	}
}
