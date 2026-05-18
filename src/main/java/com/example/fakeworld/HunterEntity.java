package com.example.fakeworld;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Optional;
import java.util.UUID;

public class HunterEntity extends Monster {
	private static final int MAX_LIFETIME_TICKS = 35 * 20;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final int BLOCK_BREAK_COOLDOWN_TICKS = 12;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final int PRESSURE_COOLDOWN_TICKS = 8;
	private static final double TOO_FAR_DISTANCE = 96.0D;
	private static final double ATTACK_DISTANCE = 1.8D;
	private static final EntityDataAccessor<Optional<UUID>> TARGET_PLAYER = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private int lifetimeTicks;
	private int pathRefreshTicks;
	private int blockBreakCooldownTicks;
	private int attackCooldownTicks;
	private int pressureCooldownTicks;
	private boolean journalRecorded;

	public HunterEntity(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.hideNameplate();
		this.setSilent(true);
		this.setPersistenceRequired();
		this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
		this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
		this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
		this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
		this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, 0.0F);
		this.setPathfindingMalus(BlockPathTypes.DOOR_OPEN, 0.0F);
		this.getNavigation().setCanFloat(true);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.42D)
				.add(Attributes.ATTACK_DAMAGE, 6.0D)
				.add(Attributes.FOLLOW_RANGE, TOO_FAR_DISTANCE);
	}

	private void hideNameplate() {
		this.setCustomName(null);
		this.setCustomNameVisible(false);
	}

	public void setTargetPlayer(ServerPlayer player) {
		this.entityData.set(TARGET_PLAYER, Optional.of(player.getUUID()));
		this.setTarget(player);
	}

	public Optional<UUID> getTargetPlayerUuid() {
		return this.entityData.get(TARGET_PLAYER);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(TARGET_PLAYER, Optional.empty());
	}

	@Override
	public void tick() {
		super.tick();
		this.hideNameplate();

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Optional<UUID> targetUuid = this.entityData.get(TARGET_PLAYER);
		if (targetUuid.isEmpty()) {
			this.discard();
			return;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUuid.get());
		if (player == null || !player.level().dimension().equals(this.level().dimension()) || player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
			this.discardAndStopTerror(player);
			return;
		}

		this.lifetimeTicks++;
		if (this.attackCooldownTicks > 0) {
			this.attackCooldownTicks--;
		}
		if (this.pressureCooldownTicks > 0) {
			this.pressureCooldownTicks--;
		}
		int aggression = Fakeworld.hunterAggressionFor(player);
		if (this.lifetimeTicks > MAX_LIFETIME_TICKS + aggression * 30) {
			this.recordJournal(player);
			this.discardAndStopTerror(player);
			return;
		}

		double distanceSqr = this.distanceToSqr(player);
		double tooFarDistance = TOO_FAR_DISTANCE + aggression * 2.0D;
		if (distanceSqr > tooFarDistance * tooFarDistance) {
			this.recordJournal(player);
			this.discardAndStopTerror(player);
			return;
		}

		this.setTarget(player);
		this.refreshPathTo(player, aggression);
		this.faceTarget(player);
		this.pressureTowardTarget(player, aggression);
		this.breakBlockingBlockToward(player, aggression);
		double attackDistance = ATTACK_DISTANCE + aggression * 0.025D;
		if (this.attackCooldownTicks <= 0 && distanceSqr <= attackDistance * attackDistance) {
			this.recordJournal(player);
			player.hurt(this.damageSources().mobAttack(this), 6.0F);
			this.attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
		}
	}

	private void refreshPathTo(ServerPlayer player, int aggression) {
		if (this.pathRefreshTicks > 0 && !this.getNavigation().isDone()) {
			this.pathRefreshTicks--;
			return;
		}

		this.getNavigation().moveTo(player, 1.05D + aggression * 0.04D);
		this.pathRefreshTicks = Math.max(4, PATH_REFRESH_TICKS - aggression / 3);
	}

	private void breakBlockingBlockToward(ServerPlayer player, int aggression) {
		if (this.blockBreakCooldownTicks > 0) {
			this.blockBreakCooldownTicks--;
			return;
		}

		if (!this.getNavigation().isDone() && this.horizontalCollision) {
			BlockPos target = this.firstBreakableBlockToward(player);
			if (target != null) {
				this.breakPursuitBlock(target, aggression);
			}
		}

		if (player.getY() + 1.0D < this.getY()) {
			BlockPos target = this.firstBreakableBlockDown();
			if (target != null) {
				this.breakPursuitBlock(target, aggression);
			}
		}

		if (player.getY() > this.getY() + 1.25D && this.horizontalDistanceTo(player) <= 2.5D) {
			BlockPos target = this.firstBreakableBlockAbove();
			if (target != null) {
				this.breakPursuitBlock(target, aggression);
			}
		}
	}

	private BlockPos firstBreakableBlockToward(ServerPlayer player) {
		Direction direction = Direction.getNearest(player.getX() - this.getX(), 0.0D, player.getZ() - this.getZ());
		BlockPos base = this.blockPosition();
		BlockPos[] candidates = {
				base.relative(direction),
				base.relative(direction).above(),
				base.above().relative(direction)
		};

		for (BlockPos candidate : candidates) {
			if (this.canBreakThrough(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private BlockPos firstBreakableBlockDown() {
		BlockPos base = this.blockPosition();
		BlockPos[] candidates = {
				base.below(),
				base.below(2)
		};

		for (BlockPos candidate : candidates) {
			if (this.canBreakThrough(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private BlockPos firstBreakableBlockAbove() {
		BlockPos base = this.blockPosition();
		BlockPos[] candidates = {
				base.above(),
				base.above(2)
		};

		for (BlockPos candidate : candidates) {
			if (this.canBreakThrough(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean canBreakThrough(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(this.level(), pos);
		return hardness >= 0.0F && hardness <= 12.0F && state.getBlock() != Blocks.BEDROCK;
	}

	private void breakPursuitBlock(BlockPos pos, int aggression) {
		if (this.level().destroyBlock(pos, false, this)) {
			this.blockBreakCooldownTicks = Math.max(3, BLOCK_BREAK_COOLDOWN_TICKS - aggression / 2);
			this.level().playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.35F, 0.75F);
		}
	}

	private void pressureTowardTarget(ServerPlayer player, int aggression) {
		if (this.pressureCooldownTicks > 0) {
			return;
		}

		boolean changedWorld = this.placeBridgeBlockIfNeeded(player) || this.placeStepBlockIfNeeded(player);
		this.directPressureMove(player, aggression);
		if (changedWorld) {
			this.pressureCooldownTicks = Math.max(2, PRESSURE_COOLDOWN_TICKS - aggression / 4);
		}
	}

	private boolean placeBridgeBlockIfNeeded(ServerPlayer player) {
		Direction direction = Direction.getNearest(player.getX() - this.getX(), 0.0D, player.getZ() - this.getZ());
		BlockPos base = this.blockPosition();
		BlockPos[] candidates = {
				base,
				base.relative(direction),
				base.relative(direction, 2)
		};

		for (BlockPos candidate : candidates) {
			if (this.needsBridgeSupport(candidate) && this.placePursuitBlock(candidate.below())) {
				return true;
			}

			if (!this.level().getBlockState(candidate).getFluidState().isEmpty() && this.placePursuitBlock(candidate)) {
				return true;
			}
		}
		return false;
	}

	private boolean needsBridgeSupport(BlockPos feetPos) {
		BlockState feetState = this.level().getBlockState(feetPos);
		BlockState headState = this.level().getBlockState(feetPos.above());
		if ((!feetState.isAir() && feetState.getFluidState().isEmpty()) || (!headState.isAir() && headState.getFluidState().isEmpty())) {
			return false;
		}

		BlockState supportState = this.level().getBlockState(feetPos.below());
		return supportState.isAir() || !supportState.getFluidState().isEmpty();
	}

	private boolean placeStepBlockIfNeeded(ServerPlayer player) {
		if (!this.onGround() || player.getY() <= this.getY() + 1.25D || this.horizontalDistanceTo(player) > 2.75D) {
			return false;
		}

		BlockPos stepPos = this.blockPosition();
		if (this.level().getBlockState(stepPos).isAir() && this.level().getBlockState(stepPos.above()).isAir()) {
			this.setDeltaMovement(this.getDeltaMovement().x, 0.42D, this.getDeltaMovement().z);
			return this.placePursuitBlock(stepPos.below());
		}
		return false;
	}

	private boolean placePursuitBlock(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (!state.isAir() && state.getFluidState().isEmpty()) {
			return false;
		}

		this.level().setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		return true;
	}

	private void directPressureMove(ServerPlayer player, int aggression) {
		if (!this.getNavigation().isDone() || this.distanceToSqr(player) <= 9.0D) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		if (horizontalDistance <= 0.001D) {
			return;
		}

		double speed = 0.075D + aggression * 0.004D;
		this.setDeltaMovement(
				this.getDeltaMovement().x + dx / horizontalDistance * speed,
				this.getDeltaMovement().y,
				this.getDeltaMovement().z + dz / horizontalDistance * speed);
	}

	private double horizontalDistanceTo(ServerPlayer player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void recordJournal(ServerPlayer player) {
		if (!this.journalRecorded) {
			Fakeworld.recordHunterEncounter(player);
			this.journalRecorded = true;
		}
	}

	private void discardAndStopTerror(ServerPlayer player) {
		if (player != null) {
			Fakeworld.stopTerrorSound(player);
		}
		this.discard();
	}

	@Override
	public void die(DamageSource damageSource) {
		this.stopTerrorForStoredTarget();
		super.die(damageSource);
	}

	private void stopTerrorForStoredTarget() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Optional<UUID> targetUuid = this.entityData.get(TARGET_PLAYER);
		if (targetUuid.isEmpty()) {
			return;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUuid.get());
		if (player != null) {
			Fakeworld.stopTerrorSound(player);
		}
	}

	private void faceTarget(ServerPlayer player) {
		this.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
		float yaw = (float) (Mth.atan2(player.getZ() - this.getZ(), player.getX() - this.getX()) * (180.0D / Math.PI)) - 90.0F;
		this.setYRot(yaw);
		this.yRotO = yaw;
		this.setYHeadRot(yaw);
		this.yHeadRotO = yaw;
		this.setYBodyRot(yaw);
		this.yBodyRotO = yaw;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		this.entityData.get(TARGET_PLAYER).ifPresent(uuid -> tag.putUUID("TargetPlayer", uuid));
		tag.putInt("LifetimeTicks", this.lifetimeTicks);
		tag.putInt("AttackCooldownTicks", this.attackCooldownTicks);
		tag.putInt("PressureCooldownTicks", this.pressureCooldownTicks);
		tag.putBoolean("JournalRecorded", this.journalRecorded);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("TargetPlayer")) {
			this.entityData.set(TARGET_PLAYER, Optional.of(tag.getUUID("TargetPlayer")));
		}
		this.lifetimeTicks = tag.getInt("LifetimeTicks");
		this.attackCooldownTicks = tag.getInt("AttackCooldownTicks");
		this.pressureCooldownTicks = tag.getInt("PressureCooldownTicks");
		this.journalRecorded = tag.getBoolean("JournalRecorded");
		this.hideNameplate();
	}
}
