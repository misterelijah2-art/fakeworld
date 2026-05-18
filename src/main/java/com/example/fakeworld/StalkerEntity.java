package com.example.fakeworld;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class StalkerEntity extends PathfinderMob {
	public static final double DISAPPEAR_DISTANCE = 14.0D;
	public static final double TOO_FAR_DISTANCE = 72.0D;
	private static final EntityDataAccessor<Optional<UUID>> TARGET_PLAYER = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	public StalkerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
		this.setNoAi(true);
		this.setNoGravity(false);
		this.setSilent(true);
		this.setInvulnerable(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.0D)
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
		this.setDeltaMovement(Vec3.ZERO);

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Optional<UUID> targetUuid = this.entityData.get(TARGET_PLAYER);
		if (targetUuid.isEmpty()) {
			this.discard();
			return;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUuid.get());
		if (player == null || !player.level().dimension().equals(this.level().dimension())) {
			this.discard();
			return;
		}

		double distanceSqr = this.distanceToSqr(player);
		if (distanceSqr < DISAPPEAR_DISTANCE * DISAPPEAR_DISTANCE) {
			Fakeworld.recordStalkerDisappearedNearPlayer(player);
			this.discard();
			return;
		}

		if (distanceSqr > TOO_FAR_DISTANCE * TOO_FAR_DISTANCE) {
			this.discard();
			return;
		}

		this.faceTarget(player);
	}

	public void faceTargetNow() {
		if (this.level() instanceof ServerLevel serverLevel) {
			this.entityData.get(TARGET_PLAYER)
					.map(uuid -> serverLevel.getServer().getPlayerList().getPlayer(uuid))
					.ifPresent(this::faceTarget);
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
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}
}
