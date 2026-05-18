package com.example.fakeworld;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public class MimicVillagerEntity extends PathfinderMob {
	private static final EntityDataAccessor<Optional<UUID>> TARGET_PLAYER = SynchedEntityData.defineId(MimicVillagerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final int MAX_LIFETIME_TICKS = 35 * 20;
	private static final double TOO_FAR_DISTANCE = 80.0D;
	private static final double CONTACT_DISTANCE = 1.9D;
	private static final double STEP_SPEED = 0.38D;
	private static final int LOOK_LOCK_TICKS = 40;
	private static final int TERRROR_DURATION_TICKS = 6 * 20;
	private int lifetimeTicks;
	private int watchedTicks;
	private int pathRefreshTicks;
	private int villagerActTicks;

	public MimicVillagerEntity(EntityType<? extends PathfinderMob> entityType, net.minecraft.world.level.Level level) {
		super(entityType, level);
		this.setNoAi(false);
		this.setSilent(false);
		this.setPersistenceRequired();
		this.getNavigation().setCanFloat(true);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 24.0D)
				.add(Attributes.MOVEMENT_SPEED, STEP_SPEED)
				.add(Attributes.FOLLOW_RANGE, TOO_FAR_DISTANCE);
	}

	public void setTargetPlayer(ServerPlayer player) {
		this.entityData.set(TARGET_PLAYER, Optional.of(player.getUUID()));
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(TARGET_PLAYER, Optional.empty());
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Optional<UUID> targetUuid = this.entityData.get(TARGET_PLAYER);
		if (targetUuid.isEmpty()) {
			this.discard();
			return;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUuid.get());
		if (player == null || !player.level().dimension().equals(this.level().dimension()) || player.isSpectator() || player.isCreative()) {
			this.discard();
			return;
		}

		this.lifetimeTicks++;
		if (this.lifetimeTicks > MAX_LIFETIME_TICKS) {
			this.discard();
			return;
		}

		double distanceSqr = this.distanceToSqr(player);
		if (distanceSqr > TOO_FAR_DISTANCE * TOO_FAR_DISTANCE) {
			this.discard();
			return;
		}

		this.faceTarget(player);
		boolean watched = player.hasLineOfSight(this) && isPlayerLookingAt(player);
		if (watched) {
			this.watchedTicks++;
			this.pathRefreshTicks = 0;
			this.getNavigation().stop();
			this.setDeltaMovement(Vec3.ZERO);
		} else {
			this.watchedTicks = 0;
			if (this.pathRefreshTicks > 0 && !this.getNavigation().isDone()) {
				this.pathRefreshTicks--;
			} else {
				moveLikeVillagerToward(player);
				this.pathRefreshTicks = 10;
			}
			tickVillagerLikeBehavior(serverLevel);
		}

		if (distanceSqr <= CONTACT_DISTANCE * CONTACT_DISTANCE) {
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 6 * 20, 0, false, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6 * 20, 1, false, false, true));
			if (this.watchedTicks >= LOOK_LOCK_TICKS) {
				player.displayClientMessage(net.minecraft.network.chat.Component.literal("It waited for you to look away."), true);
			}
			Fakeworld.playTimedTerror(player, TERRROR_DURATION_TICKS);
			this.discard();
		}
	}

	private void moveLikeVillagerToward(ServerPlayer player) {
		Vec3 toPlayer = player.position().subtract(this.position());
		Vec3 horizontal = new Vec3(toPlayer.x, 0.0D, toPlayer.z);
		if (horizontal.lengthSqr() < 0.001D) {
			this.getNavigation().stop();
			return;
		}

		Vec3 dir = horizontal.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		double offset = (this.random.nextDouble() - 0.5D) * 2.4D;
		double approachDistance = Math.min(2.2D, Math.max(0.8D, horizontal.length() * 0.12D));
		Vec3 target = player.position().subtract(dir.scale(approachDistance)).add(side.scale(offset));
		int y = this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(target.x), Mth.floor(target.z));
		this.getNavigation().moveTo(target.x, y, target.z, STEP_SPEED);
	}

	private void tickVillagerLikeBehavior(ServerLevel world) {
		if (this.villagerActTicks > 0) {
			this.villagerActTicks--;
			return;
		}

		this.villagerActTicks = 30 + this.random.nextInt(40);
		if (this.random.nextFloat() < 0.35F) {
			world.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
		}

		if (this.random.nextFloat() < 0.25F) {
			AABB area = new AABB(this.blockPosition()).inflate(6.0D, 3.0D, 6.0D);
			List<net.minecraft.world.entity.npc.Villager> villagers = world.getEntities(net.minecraft.world.entity.EntityType.VILLAGER, area, v -> true);
			if (!villagers.isEmpty()) {
				net.minecraft.world.entity.npc.Villager villager = villagers.get(this.random.nextInt(villagers.size()));
				this.lookAt(EntityAnchorArgument.Anchor.EYES, villager.getEyePosition());
			}
		}
	}

	private boolean isPlayerLookingAt(ServerPlayer player) {
		Vec3 look = player.getLookAngle().normalize();
		Vec3 toEntity = this.position().subtract(player.getEyePosition()).normalize();
		return look.dot(toEntity) > 0.94D;
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
	public void addAdditionalSaveData(CompoundTag tag) {
		this.entityData.get(TARGET_PLAYER).ifPresent(uuid -> tag.putUUID("TargetPlayer", uuid));
		tag.putInt("LifetimeTicks", this.lifetimeTicks);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("TargetPlayer")) {
			this.entityData.set(TARGET_PLAYER, Optional.of(tag.getUUID("TargetPlayer")));
		}
		this.lifetimeTicks = tag.getInt("LifetimeTicks");
	}
}
