package com.example.fakeworld;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CreepyVillageManager {
	private static final int SEARCH_RADIUS = 96;
	private static final int VILLAGE_RADIUS = 48;
	private static final int AUTO_SCAN_INTERVAL_TICKS = 40;
	private static final int REPAIR_CHECK_INTERVAL_TICKS = 100;
	private static final int INDOOR_SEARCH_RADIUS = 14;
	private static final int TEST_HOUSE_DISTANCE = 9;
	private static final int TEST_HOUSE_RADIUS = 2;
	private static final int TEST_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final int NBT_COMPOUND_TYPE = 10;
	private static final List<VanillaVillagePiece> VANILLA_VILLAGE_PIECES = List.of(
			new VanillaVillagePiece(plainsVillageTemplate("town_centers/plains_meeting_point_1"), 0, 0, Rotation.NONE),
			new VanillaVillagePiece(plainsVillageTemplate("streets/crossroad_01"), 0, 8, Rotation.NONE),
			new VanillaVillagePiece(plainsVillageTemplate("streets/straight_01"), 0, 20, Rotation.NONE),
			new VanillaVillagePiece(plainsVillageTemplate("streets/straight_02"), 0, -18, Rotation.CLOCKWISE_180),
			new VanillaVillagePiece(plainsVillageTemplate("streets/corner_01"), 18, 8, Rotation.CLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("streets/corner_02"), -18, 8, Rotation.COUNTERCLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("plains_lamp_1"), 8, 6, Rotation.NONE),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_1"), -24, -18, Rotation.CLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_2"), 22, -18, Rotation.COUNTERCLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_3"), -26, 14, Rotation.CLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_4"), 20, 16, Rotation.COUNTERCLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_1"), -10, 28, Rotation.CLOCKWISE_180),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_house_2"), 12, 30, Rotation.CLOCKWISE_180),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_small_farm_1"), -34, 4, Rotation.CLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_large_farm_1"), 32, 2, Rotation.COUNTERCLOCKWISE_90),
			new VanillaVillagePiece(plainsVillageTemplate("houses/plains_animal_pen_1"), 0, -32, Rotation.NONE)
	);
	private static final SimpleCommandExceptionType NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakevillage must be run by a player."));
	private static final SimpleCommandExceptionType NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakevillage only works in the fake overworld."));
	private static final SimpleCommandExceptionType GENERATION_FAILED = new SimpleCommandExceptionType(Component.literal("Could not build a broken village here."));
	private static final String[] VILLAGER_MESSAGES = {
			"They look toward the missing door.",
			"They are waiting for night to be safe again.",
			"Something was taken from here.",
			"The doorway is empty."
	};

	private CreepyVillageManager() {
	}

	public static void registerEvents() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Villager)) {
				return InteractionResult.PASS;
			}

			Fakeworld.FakeworldSaveData data = Fakeworld.getSaveData(serverPlayer.server);
			if (!isUnrepairedVillageEntity(data, entity)) {
				return InteractionResult.PASS;
			}

			String message = VILLAGER_MESSAGES[serverPlayer.getRandom().nextInt(VILLAGER_MESSAGES.length)];
			serverPlayer.displayClientMessage(Component.literal(message), true);
			return InteractionResult.FAIL;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			if (!world.getBlockState(pos).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}

			Fakeworld.FakeworldSaveData data = Fakeworld.getSaveData(serverPlayer.server);
			if (!isInsideActiveVillage(data, world.dimension(), pos)) {
				return InteractionResult.PASS;
			}

			if (data.isCreepyVillageRepaired()) {
				serverPlayer.displayClientMessage(Component.literal("They go inside at night."), true);
				return InteractionResult.PASS;
			}

			serverPlayer.displayClientMessage(Component.literal("The empty doorways answer back."), true);
			return InteractionResult.PASS;
		});
	}

	public static int runCommand(net.minecraft.commands.CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw NEEDS_PLAYER.create();
		}

		ServerLevel world = source.getLevel();
		if (!world.dimension().equals(Fakeworld.FAKE_OVERWORLD)) {
			throw NEEDS_FAKE_OVERWORLD.create();
		}

		return generateBrokenVillage(source, player, world, "Generated a broken village.");
	}

	public static int runTestCommand(net.minecraft.commands.CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw NEEDS_PLAYER.create();
		}

		ServerLevel world = source.getLevel();
		if (!world.dimension().equals(Fakeworld.FAKE_OVERWORLD)) {
			throw NEEDS_FAKE_OVERWORLD.create();
		}

		BlockPos center = findVillageGenerationCenter(player, world);
		if (center == null || !spawnTestVillageAt(world, center, Fakeworld.getSaveData(source.getServer()))) {
			throw GENERATION_FAILED.create();
		}

		source.sendSuccess(() -> Component.literal("Built a test broken village."), false);
		return 1;
	}

	private static int generateBrokenVillage(net.minecraft.commands.CommandSourceStack source, ServerPlayer player, ServerLevel world, String successMessage) throws CommandSyntaxException {
		BlockPos center = findVillageGenerationCenter(player, world);
		if (center == null || !spawnBrokenVillageAt(world, center, Fakeworld.getSaveData(source.getServer()))) {
			throw GENERATION_FAILED.create();
		}

		source.sendSuccess(() -> Component.literal(successMessage), false);
		return 1;
	}

	public static boolean spawnCreepyVillageFor(ServerPlayer player, ServerLevel world, Fakeworld.FakeworldSaveData data) {
		if (data.hasActiveCreepyVillage()) {
			return false;
		}

		BlockPos center = findVillageGenerationCenter(player, world);
		return center != null && spawnBrokenVillageAt(world, center, data);
	}

	private static void removeExistingVillagers(ServerLevel world, BlockPos center, int radius) {
		AABB area = new AABB(center).inflate(radius, 32.0D, radius);
		for (Villager villager : world.getEntities(EntityType.VILLAGER, area, villager -> true)) {
			villager.discard();
		}
	}

	private static ResourceLocation plainsVillageTemplate(String path) {
		return new ResourceLocation("minecraft", "village/plains/" + path);
	}

	public static void tick(MinecraftServer server, Fakeworld.FakeworldSaveData data) {
		ServerLevel world = server.getLevel(Fakeworld.FAKE_OVERWORLD);
		if (world == null) {
			return;
		}

		if (!data.hasActiveCreepyVillage()) {
			return;
		}

		if (!data.hasActiveUnrepairedCreepyVillage()) {
			return;
		}

		ServerLevel activeWorld = server.getLevel(data.getCreepyVillageDimension());
		if (activeWorld == null) {
			return;
		}

		freezeAnchoredVillagers(activeWorld, data);
		data.decrementCreepyVillageRepairCheckTicks();
		if (data.getCreepyVillageRepairCheckTicks() > 0) {
			return;
		}

		data.setCreepyVillageRepairCheckTicks(REPAIR_CHECK_INTERVAL_TICKS);
		BlockPos center = data.getCreepyVillageCenter();
		int doorCount = countWoodenDoors(activeWorld, center, data.getCreepyVillageRadius());
		if (doorCount >= data.getCreepyVillageRequiredDoorCount()) {
			repairVillage(activeWorld, data);
		}
	}

	public static void save(Fakeworld.FakeworldSaveData data, CompoundTag tag) {
		tag.putBoolean("CreepyVillageActive", data.creepyVillageActive);
		tag.putBoolean("CreepyVillageRepaired", data.creepyVillageRepaired);
		tag.putString("CreepyVillageDimension", data.creepyVillageDimension.location().toString());
		tag.putInt("CreepyVillageX", data.creepyVillageCenter.getX());
		tag.putInt("CreepyVillageY", data.creepyVillageCenter.getY());
		tag.putInt("CreepyVillageZ", data.creepyVillageCenter.getZ());
		tag.putInt("CreepyVillageRadius", data.creepyVillageRadius);
		tag.putInt("CreepyVillageRequiredDoors", data.creepyVillageRequiredDoorCount);
		tag.putInt("CreepyVillageRepairCheckTicks", data.creepyVillageRepairCheckTicks);
		ListTag villagerAnchors = new ListTag();
		for (VillagerAnchor anchor : data.creepyVillageVillagerAnchors) {
			CompoundTag anchorTag = new CompoundTag();
			anchorTag.putUUID("Villager", anchor.villagerUuid());
			anchorTag.putInt("AnchorX", anchor.anchor().getX());
			anchorTag.putInt("AnchorY", anchor.anchor().getY());
			anchorTag.putInt("AnchorZ", anchor.anchor().getZ());
			anchorTag.putInt("DoorX", anchor.door().getX());
			anchorTag.putInt("DoorY", anchor.door().getY());
			anchorTag.putInt("DoorZ", anchor.door().getZ());
			anchorTag.putString("Facing", anchor.facing().getName());
			villagerAnchors.add(anchorTag);
		}
		tag.put("CreepyVillageVillagers", villagerAnchors);
	}

	public static void load(Fakeworld.FakeworldSaveData data, CompoundTag tag) {
		data.creepyVillageActive = tag.getBoolean("CreepyVillageActive");
		data.creepyVillageRepaired = tag.getBoolean("CreepyVillageRepaired");
		String dimension = tag.getString("CreepyVillageDimension");
		if (!dimension.isEmpty()) {
			data.creepyVillageDimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(dimension));
		}
		data.creepyVillageCenter = new BlockPos(tag.getInt("CreepyVillageX"), tag.getInt("CreepyVillageY"), tag.getInt("CreepyVillageZ"));
		data.creepyVillageRadius = tag.getInt("CreepyVillageRadius");
		data.creepyVillageRequiredDoorCount = tag.getInt("CreepyVillageRequiredDoors");
		data.creepyVillageRepairCheckTicks = tag.getInt("CreepyVillageRepairCheckTicks");
		data.creepyVillageVillagerAnchors.clear();
		ListTag villagerAnchors = tag.getList("CreepyVillageVillagers", NBT_COMPOUND_TYPE);
		for (int i = 0; i < villagerAnchors.size(); i++) {
			CompoundTag anchorTag = villagerAnchors.getCompound(i);
			if (!anchorTag.hasUUID("Villager")) {
				continue;
			}

			BlockPos anchor = new BlockPos(anchorTag.getInt("AnchorX"), anchorTag.getInt("AnchorY"), anchorTag.getInt("AnchorZ"));
			BlockPos door = new BlockPos(anchorTag.getInt("DoorX"), anchorTag.getInt("DoorY"), anchorTag.getInt("DoorZ"));
			Direction facing = Direction.byName(anchorTag.getString("Facing"));
			if (facing == null) {
				facing = directionFromAnchorToDoor(anchor, door);
			}
			data.creepyVillageVillagerAnchors.add(new VillagerAnchor(anchorTag.getUUID("Villager"), anchor, door, facing));
		}
		if (data.creepyVillageRadius <= 0) {
			data.creepyVillageRadius = VILLAGE_RADIUS;
		}
	}

	private static boolean tryStartNear(ServerPlayer player, ServerLevel world, Fakeworld.FakeworldSaveData data) {
		if (data.hasActiveUnrepairedCreepyVillage()) {
			return false;
		}

		BlockPos center = findCandidateCenter(player, world);
		if (center == null) {
			return false;
		}

		List<House> houses = findHouses(world, center);
		int doorsBefore = houses.size();
		int removedDoors = removeMissingDoors(world, houses);
		if (removedDoors <= 0) {
			return false;
		}

		data.setCreepyVillage(world.dimension(), center, VILLAGE_RADIUS, doorsBefore);
		activateVillagers(world, center, houses, data);
		return true;
	}

	private static BlockPos findVillageGenerationCenter(ServerPlayer player, ServerLevel world) {
		BlockPos origin = player.blockPosition();
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = 72.0D + world.getRandom().nextDouble() * 64.0D;
			int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos center = new BlockPos(x, y, z);
			if (!isVillageSiteDryLand(world, center)) {
				continue;
			}

			int score = scoreVillageSite(world, center);
			if (score > bestScore) {
				bestScore = score;
				best = center;
			}
		}

		return bestScore >= 40 ? best : null;
	}

	private static List<House> buildVanillaVillage(ServerLevel world, BlockPos center) {
		boolean placedAny = false;
		for (VanillaVillagePiece piece : VANILLA_VILLAGE_PIECES) {
			if (placeVanillaVillagePiece(world, center, piece)) {
				placedAny = true;
			}
		}

		if (!placedAny) {
			return Collections.emptyList();
		}

		return findHouses(world, center);
	}

	private static boolean placeVanillaVillagePiece(ServerLevel world, BlockPos center, VanillaVillagePiece piece) {
		Optional<StructureTemplate> template = world.getStructureManager().get(piece.id());
		if (template.isEmpty()) {
			return false;
		}

		BlockPos roughOrigin = center.offset(piece.offsetX(), 0, piece.offsetZ());
		BlockPos origin = terrainSurfacePos(world, roughOrigin);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setMirror(Mirror.NONE)
				.setRotation(piece.rotation())
				.setIgnoreEntities(true)
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
		boolean placed = template.get().placeInWorld(world, origin, origin, settings, world.getRandom(), Block.UPDATE_ALL);
		if (placed) {
			fillFoundationUnderPiece(world, origin, template.get().getSize(), piece.rotation());
		}
		return placed;
	}

	private static void fillFoundationUnderPiece(ServerLevel world, BlockPos origin, Vec3i size, Rotation rotation) {
		for (int x = 0; x < size.getX(); x++) {
			for (int z = 0; z < size.getZ(); z++) {
				Vec3i offset = rotateLocalOffset(x, z, rotation);
				BlockPos columnBase = origin.offset(offset.getX(), -1, offset.getZ());
				fillFoundationColumn(world, columnBase);
			}
		}
	}

	private static Vec3i rotateLocalOffset(int x, int z, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> new Vec3i(-z, 0, x);
			case CLOCKWISE_180 -> new Vec3i(-x, 0, -z);
			case COUNTERCLOCKWISE_90 -> new Vec3i(z, 0, -x);
			default -> new Vec3i(x, 0, z);
		};
	}

	private static void fillFoundationColumn(ServerLevel world, BlockPos start) {
		BlockState foundation = foundationBlockFor(world, start);
		for (int y = start.getY(); y >= world.getMinBuildHeight(); y--) {
			BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return;
			}

			world.setBlock(pos, foundation, Block.UPDATE_ALL);
		}
	}

	private static BlockState foundationBlockFor(ServerLevel world, BlockPos pos) {
		for (int radius = 0; radius <= 3; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					if (Math.abs(x) != radius && Math.abs(z) != radius) {
						continue;
					}

					BlockPos sampleColumn = pos.offset(x, 0, z);
					for (int y = pos.getY(); y >= Math.max(world.getMinBuildHeight(), pos.getY() - 8); y--) {
						BlockState sample = world.getBlockState(new BlockPos(sampleColumn.getX(), y, sampleColumn.getZ()));
						if (isNaturalFoundationBlock(sample)) {
							return sample.getBlock().defaultBlockState();
						}
					}
				}
			}
		}
		return Blocks.DIRT.defaultBlockState();
	}

	private static boolean isNaturalFoundationBlock(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK)
				|| state.is(Blocks.DIRT)
				|| state.is(Blocks.COARSE_DIRT)
				|| state.is(Blocks.PODZOL)
				|| state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.SAND)
				|| state.is(Blocks.RED_SAND)
				|| state.is(Blocks.SNOW_BLOCK)
				|| state.is(Blocks.STONE);
	}

	private static boolean spawnBrokenVillageAt(ServerLevel world, BlockPos center, Fakeworld.FakeworldSaveData data) {
		removeExistingVillagers(world, center, VILLAGE_RADIUS);
		List<House> houses = buildVanillaVillage(world, center);
		int doorsBefore = houses.size();
		int removedDoors = removeMissingDoors(world, houses);
		if (removedDoors <= 0) {
			return false;
		}

		data.setCreepyVillage(world.dimension(), center, VILLAGE_RADIUS, doorsBefore);
		spawnAnchoredVillagers(world, houses, data);
		return true;
	}

	private static boolean spawnTestVillageAt(ServerLevel world, BlockPos center, Fakeworld.FakeworldSaveData data) {
		removeExistingVillagers(world, center, VILLAGE_RADIUS);
		List<House> houses = buildTestVillage(world, center);
		int doorsBefore = houses.size();
		int removedDoors = removeMissingDoors(world, houses);
		if (removedDoors <= 0) {
			return false;
		}

		data.setCreepyVillage(world.dimension(), center, VILLAGE_RADIUS, doorsBefore);
		spawnAnchoredVillagers(world, houses, data);
		return true;
	}

	private static int scoreVillageSite(ServerLevel world, BlockPos center) {
		if (!isVillageSiteDryLand(world, center)) {
			return Integer.MIN_VALUE;
		}

		int score = 0;
		for (int x = -40; x <= 40; x += 8) {
			for (int z = -40; z <= 40; z += 8) {
				int sampleX = center.getX() + x;
				int sampleZ = center.getZ() + z;
				int sampleY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
				int delta = Math.abs(sampleY - center.getY());
				score -= Math.min(delta, 10) * 2;
				if (delta <= 4) {
					score += 12;
				} else if (delta <= 8) {
					score += 5;
				}

				BlockPos sample = new BlockPos(sampleX, sampleY, sampleZ);
				if (hasSolidGroundAtPlacement(world, sample)) {
					score += 8;
				}
				if (!world.getBlockState(sample).getFluidState().isEmpty()) {
					score -= 20;
				}
			}
		}
		return score;
	}

	private static boolean isVillageSiteDryLand(ServerLevel world, BlockPos center) {
		for (int x = -48; x <= 48; x += 8) {
			for (int z = -48; z <= 48; z += 8) {
				int sampleX = center.getX() + x;
				int sampleZ = center.getZ() + z;
				int sampleY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
				BlockPos sample = new BlockPos(sampleX, sampleY, sampleZ);
				if (world.getBiome(sample).is(BiomeTags.IS_OCEAN) || !hasSolidGroundAtPlacement(world, sample)) {
					return false;
				}
			}
		}
		return true;
	}

	private static BlockPos terrainSurfacePos(ServerLevel world, BlockPos roughPos) {
		int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roughPos.getX(), roughPos.getZ());
		return new BlockPos(roughPos.getX(), y, roughPos.getZ());
	}

	private static boolean hasSolidGroundAtPlacement(ServerLevel world, BlockPos placementPos) {
		BlockState ground = world.getBlockState(placementPos.below());
		return ground.blocksMotion()
				&& ground.getFluidState().isEmpty()
				&& world.getBlockState(placementPos).getFluidState().isEmpty();
	}

	private static List<House> buildTestVillage(ServerLevel world, BlockPos center) {
		world.setBlock(center, foundationBlockFor(world, center.below()), Block.UPDATE_ALL);
		world.setBlock(center.above(), Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);

		List<House> houses = new ArrayList<>();
		BlockPos northWest = center.offset(-TEST_HOUSE_DISTANCE, 0, -TEST_HOUSE_DISTANCE);
		BlockPos northEast = center.offset(TEST_HOUSE_DISTANCE, 0, -TEST_HOUSE_DISTANCE);
		BlockPos southWest = center.offset(-TEST_HOUSE_DISTANCE, 0, TEST_HOUSE_DISTANCE);
		BlockPos southEast = center.offset(TEST_HOUSE_DISTANCE, 0, TEST_HOUSE_DISTANCE);
		houses.add(buildTestHouse(world, northWest, Direction.SOUTH));
		houses.add(buildTestHouse(world, northEast, Direction.SOUTH));
		houses.add(buildTestHouse(world, southWest, Direction.NORTH));
		houses.add(buildTestHouse(world, southEast, Direction.NORTH));
		return houses;
	}

	private static House buildTestHouse(ServerLevel world, BlockPos roughCenter, Direction doorFacing) {
		BlockPos center = roughCenter.atY(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roughCenter.getX(), roughCenter.getZ()));
		clearTestHouseArea(world, center);

		for (int x = -TEST_HOUSE_RADIUS; x <= TEST_HOUSE_RADIUS; x++) {
			for (int z = -TEST_HOUSE_RADIUS; z <= TEST_HOUSE_RADIUS; z++) {
				world.setBlock(center.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
				world.setBlock(center.offset(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
				boolean wall = Math.abs(x) == TEST_HOUSE_RADIUS || Math.abs(z) == TEST_HOUSE_RADIUS;
				if (wall) {
					world.setBlock(center.offset(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
					world.setBlock(center.offset(x, 2, z), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}

		BlockPos doorPos = center.relative(doorFacing, TEST_HOUSE_RADIUS).above();
		BlockState lowerDoor = Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, doorFacing)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		BlockState upperDoor = lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		world.setBlock(doorPos, lowerDoor, TEST_BLOCK_FLAGS);
		world.setBlock(doorPos.above(), upperDoor, TEST_BLOCK_FLAGS);

		BlockPos outside = doorPos.relative(doorFacing);
		world.setBlock(outside.below(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		world.setBlock(outside, Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
		world.setBlock(outside.above(), Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
		return new House(doorPos.immutable(), center.above().immutable(), doorFacing);
	}

	private static void clearTestHouseArea(ServerLevel world, BlockPos center) {
		for (int x = -TEST_HOUSE_RADIUS - 1; x <= TEST_HOUSE_RADIUS + 1; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = -TEST_HOUSE_RADIUS - 1; z <= TEST_HOUSE_RADIUS + 1; z++) {
					world.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	private static void tickVillageDiscovery(MinecraftServer server, ServerLevel world, Fakeworld.FakeworldSaveData data) {
		data.decrementCreepyVillageScanTicks();
		if (data.getCreepyVillageScanTicks() > 0) {
			return;
		}

		data.setCreepyVillageScanTicks(AUTO_SCAN_INTERVAL_TICKS);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension().equals(Fakeworld.FAKE_OVERWORLD) && tryStartNear(player, world, data)) {
				return;
			}
		}
	}

	private static BlockPos findCandidateCenter(ServerPlayer player, ServerLevel world) {
		AABB search = new AABB(player.blockPosition()).inflate(SEARCH_RADIUS, 32.0D, SEARCH_RADIUS);
		List<Villager> villagers = world.getEntities(EntityType.VILLAGER, search, villager -> true);
		if (villagers.isEmpty()) {
			return null;
		}

		for (Villager villager : villagers) {
			BlockPos center = villager.blockPosition();
			if (findHouses(world, center).size() > 1) {
				return center;
			}
		}

		return null;
	}

	private static List<House> findHouses(ServerLevel world, BlockPos center) {
		List<House> houses = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-VILLAGE_RADIUS, -24, -VILLAGE_RADIUS), center.offset(VILLAGE_RADIUS, 32, VILLAGE_RADIUS))) {
			BlockState state = world.getBlockState(pos);
			if (!isLowerWoodenDoor(state)) {
				continue;
			}
			if (!isGroundLevelDoor(world, pos)) {
				continue;
			}

			Direction outsideDirection = findDoorOutsideDirection(world, pos, state);
			BlockPos interior = findDoorInterior(world, pos, outsideDirection);
			if (interior != null && !hasHouseNear(houses, interior)) {
				houses.add(new House(pos.immutable(), interior, outsideDirection));
			}
		}

		return houses;
	}

	private static boolean hasHouseNear(List<House> houses, BlockPos interior) {
		for (House house : houses) {
			if (house.interior().distSqr(interior) <= 9.0D) {
				return true;
			}
		}

		return false;
	}

	private static Direction findDoorOutsideDirection(ServerLevel world, BlockPos doorPos, BlockState doorState) {
		Direction facing = doorState.getValue(DoorBlock.FACING);
		Direction opposite = facing.getOpposite();
		int frontScore = outdoorSideScore(world, doorPos.relative(facing), doorPos);
		int backScore = outdoorSideScore(world, doorPos.relative(opposite), doorPos);
		if (frontScore != backScore) {
			return frontScore > backScore ? facing : opposite;
		}

		BlockPos frontShelter = findShelterPosition(world, doorPos, doorPos.relative(facing));
		BlockPos backShelter = findShelterPosition(world, doorPos, doorPos.relative(opposite));
		if (frontShelter != null && backShelter == null) {
			return opposite;
		}
		if (backShelter != null && frontShelter == null) {
			return facing;
		}
		return facing;
	}

	private static int outdoorSideScore(ServerLevel world, BlockPos stand, BlockPos doorPos) {
		int score = 0;
		if (!isClearStandSpace(world, stand) && !isClearStandSpace(world, stand.above())) {
			score -= 40;
		}
		if (world.canSeeSky(stand)) {
			score += 20;
		}
		if (world.canSeeSky(stand.above())) {
			score += 12;
		}
		if (!isShelteredStandPosition(world, stand)) {
			score += 8;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos neighbor = stand.relative(direction);
			if (isClearStandSpace(world, neighbor) || isClearStandSpace(world, neighbor.above())) {
				score += 2;
			}
		}
		if (stand.distSqr(doorPos) > 1.5D) {
			score += 1;
		}
		return score;
	}

	private static BlockPos findDoorInterior(ServerLevel world, BlockPos doorPos, Direction outsideDirection) {
		BlockPos preferredInteriorSide = doorPos.relative(outsideDirection.getOpposite());
		BlockPos interior = findShelterPosition(world, doorPos, preferredInteriorSide);
		if (interior != null) {
			return interior;
		}
		if (isStandPosition(world, preferredInteriorSide)) {
			return preferredInteriorSide.immutable();
		}
		if (isStandPosition(world, preferredInteriorSide.below())) {
			return preferredInteriorSide.below().immutable();
		}
		return preferredInteriorSide.immutable();
	}

	private static boolean isGroundLevelDoor(ServerLevel world, BlockPos doorPos) {
		int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, doorPos.getX(), doorPos.getZ());
		return doorPos.getY() <= surfaceY + 2;
	}

	private static BlockPos findDoorstepStandPosition(ServerLevel world, BlockPos doorPos, Direction outsideDirection) {
		BlockPos outside = doorPos.relative(outsideDirection);
		if (isStandPosition(world, outside)) {
			return outside.immutable();
		}
		if (isStandPosition(world, outside.below())) {
			return outside.below().immutable();
		}
		return null;
	}

	private static BlockPos findClosestStandPosition(ServerLevel world, BlockPos pos) {
		if (isStandPosition(world, pos)) {
			return pos.immutable();
		}
		if (isStandPosition(world, pos.above())) {
			return pos.above().immutable();
		}
		if (isStandPosition(world, pos.below())) {
			return pos.below().immutable();
		}
		return pos.immutable();
	}

	private static int removeMissingDoors(ServerLevel world, List<House> houses) {
		if (houses.isEmpty()) {
			return 0;
		}

		List<House> candidates = new ArrayList<>(houses);
		Collections.shuffle(candidates);
		int targetCount = houses.size();

		int removed = 0;
		for (House house : candidates) {
			removeDoorWithoutDrops(world, house.door());
			removed++;
			if (removed >= targetCount) {
				return removed;
			}
		}

		return removed;
	}

	private static void removeDoorWithoutDrops(ServerLevel world, BlockPos lowerDoorPos) {
		world.setBlock(lowerDoorPos.above(), Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
		world.setBlock(lowerDoorPos, Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
	}

	private static int countWoodenDoors(ServerLevel world, BlockPos center, int radius) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -24, -radius), center.offset(radius, 32, radius))) {
			if (isLowerWoodenDoor(world.getBlockState(pos))) {
				count++;
			}
		}

		return count;
	}

	private static boolean isLowerWoodenDoor(BlockState state) {
		return state.is(BlockTags.WOODEN_DOORS)
				&& state.hasProperty(DoorBlock.HALF)
				&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
	}

	private static boolean isUnrepairedVillageEntity(Fakeworld.FakeworldSaveData data, Entity entity) {
		return data.hasActiveUnrepairedCreepyVillage()
				&& isInsideActiveVillage(data, entity.level().dimension(), entity.blockPosition());
	}

	private static boolean isInsideActiveVillage(Fakeworld.FakeworldSaveData data, ResourceKey<Level> dimension, BlockPos pos) {
		if (!data.hasActiveCreepyVillage() || !data.getCreepyVillageDimension().equals(dimension)) {
			return false;
		}

		return data.getCreepyVillageCenter().distSqr(pos) <= data.getCreepyVillageRadius() * data.getCreepyVillageRadius();
	}

	private static void repairVillage(ServerLevel world, Fakeworld.FakeworldSaveData data) {
		data.setCreepyVillageRepaired();
		BlockPos center = data.getCreepyVillageCenter();
		releaseAnchoredVillagers(world, data);
		setVillagerState(world, center, false);
		for (ServerPlayer player : world.players()) {
			if (center.distSqr(player.blockPosition()) <= data.getCreepyVillageRadius() * data.getCreepyVillageRadius()) {
				player.displayClientMessage(Component.literal("The village remembers how to close its doors."), true);
			}
		}
	}

	private static void activateVillagers(ServerLevel world, BlockPos center, List<House> houses, Fakeworld.FakeworldSaveData data) {
		AABB area = new AABB(center).inflate(VILLAGE_RADIUS, 24.0D, VILLAGE_RADIUS);
		List<Villager> villagers = new ArrayList<>(world.getEntities(EntityType.VILLAGER, area, villager -> true));
		List<VillagerAnchor> anchors = assignVillagersToHouses(world, houses, villagers);
		data.setCreepyVillageVillagerAnchors(anchors);
	}

	private static void spawnAnchoredVillagers(ServerLevel world, List<House> houses, Fakeworld.FakeworldSaveData data) {
		List<VillagerAnchor> anchors = new ArrayList<>();
		for (House house : houses) {
			BlockPos anchor = forcedOutdoorAnchor(world, house);
			Direction facing = directionFromAnchorToDoor(anchor, house.door());
			Villager villager = EntityType.VILLAGER.create(world);
			if (villager == null) {
				continue;
			}

			villager.setBaby(false);
			villager.moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 0.0F, 0.0F);
			world.addFreshEntity(villager);
			freezeVillagerAtDoor(villager, anchor, house.door(), facing);
			anchors.add(new VillagerAnchor(villager.getUUID(), anchor, house.door(), facing));
		}
		data.setCreepyVillageVillagerAnchors(anchors);
	}

	private static void setVillagerState(ServerLevel world, BlockPos center, boolean unfinished) {
		AABB area = new AABB(center).inflate(VILLAGE_RADIUS, 24.0D, VILLAGE_RADIUS);
		for (Villager villager : world.getEntities(EntityType.VILLAGER, area, villager -> true)) {
			villager.setSilent(unfinished);
			villager.setNoAi(unfinished);
			villager.setNoGravity(false);
			villager.setInvulnerable(false);
			villager.noPhysics = false;
		}
	}

	private static List<VillagerAnchor> assignVillagersToHouses(ServerLevel world, List<House> houses, List<Villager> villagers) {
		List<VillagerAnchor> anchors = new ArrayList<>();
		if (houses.isEmpty()) {
			return anchors;
		}

		List<Villager> adults = new ArrayList<>();
		for (Villager villager : villagers) {
			if (villager.isBaby()) {
				villager.discard();
				continue;
			}
			adults.add(villager);
		}

		while (adults.size() < houses.size()) {
			BlockPos anchor = forcedOutdoorAnchor(world, houses.get(adults.size()));
			Villager villager = EntityType.VILLAGER.create(world);
			if (villager == null) {
				break;
			}
			villager.setBaby(false);
			villager.moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 0.0F, 0.0F);
			world.addFreshEntity(villager);
			adults.add(villager);
		}

		List<Villager> available = new ArrayList<>(adults);
		for (House house : houses) {
			BlockPos anchor = forcedOutdoorAnchor(world, house);
			Direction facing = directionFromAnchorToDoor(anchor, house.door());
			Villager villager = takeClosestVillager(available, anchor);
			if (villager == null) {
				continue;
			}
			freezeVillagerAtDoor(villager, anchor, house.door(), facing);
			anchors.add(new VillagerAnchor(villager.getUUID(), anchor, house.door(), facing));
		}

		for (Villager villager : available) {
			villager.discard();
		}

		return anchors;
	}

	private static BlockPos forcedOutdoorAnchor(ServerLevel world, House house) {
		Direction outsideDirection = verifiedOutdoorDirection(world, house.door(), house.outsideDirection());
		BlockPos anchor = house.door().relative(outsideDirection, 2).atY(house.door().getY());
		prepareForcedOutdoorAnchor(world, anchor);
		return anchor.immutable();
	}

	private static Direction verifiedOutdoorDirection(ServerLevel world, BlockPos door, Direction direction) {
		if (outdoorAnchorScore(world, door.relative(direction, 2)) >= outdoorAnchorScore(world, door.relative(direction.getOpposite(), 2))) {
			return direction;
		}
		return direction.getOpposite();
	}

	private static int outdoorAnchorScore(ServerLevel world, BlockPos pos) {
		int score = 0;
		if (!isClearStandSpace(world, pos) && !isClearStandSpace(world, pos.above())) {
			score -= 40;
		}
		if (world.canSeeSky(pos) || world.canSeeSky(pos.above())) {
			score += 20;
		}
		if (!isShelteredStandPosition(world, pos)) {
			score += 8;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (isClearStandSpace(world, pos.relative(direction))) {
				score += 2;
			}
		}
		return score;
	}

	private static void prepareForcedOutdoorAnchor(ServerLevel world, BlockPos anchor) {
		fillFoundationColumn(world, anchor.below());
		for (int y = 0; y <= 3; y++) {
			world.setBlock(anchor.above(y), Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
		}
	}

	private static Villager takeClosestVillager(List<Villager> villagers, BlockPos target) {
		Villager closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (Villager villager : villagers) {
			double distance = villager.blockPosition().distSqr(target);
			if (distance < closestDistance) {
				closest = villager;
				closestDistance = distance;
			}
		}

		if (closest != null) {
			villagers.remove(closest);
		}

		return closest;
	}

	private static BlockPos findShelterPosition(ServerLevel world, BlockPos villageCenter, BlockPos origin) {
		for (int y = -4; y <= 4; y++) {
			for (int radius = 0; radius <= INDOOR_SEARCH_RADIUS; radius++) {
				for (int x = -radius; x <= radius; x++) {
					for (int z = -radius; z <= radius; z++) {
						if (Math.abs(x) != radius && Math.abs(z) != radius) {
							continue;
						}

						BlockPos candidate = origin.offset(x, y, z);
						if (villageCenter.distSqr(candidate) <= VILLAGE_RADIUS * VILLAGE_RADIUS && isShelteredStandPosition(world, candidate)) {
							return candidate.immutable();
						}
					}
				}
			}
		}

		return null;
	}

	private static boolean isShelteredStandPosition(ServerLevel world, BlockPos pos) {
		return isStandPosition(world, pos) && !world.canSeeSky(pos);
	}

	private static boolean isStandPosition(ServerLevel world, BlockPos pos) {
		return world.getBlockState(pos.below()).blocksMotion()
				&& isClearStandSpace(world, pos)
				&& isClearStandSpace(world, pos.above());
	}

	private static boolean isClearStandSpace(ServerLevel world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getFluidState().isEmpty()
				&& (state.isAir() || state.canBeReplaced());
	}

	private static void freezeAnchoredVillagers(ServerLevel world, Fakeworld.FakeworldSaveData data) {
		List<VillagerAnchor> anchors = data.getCreepyVillageVillagerAnchors();
		for (VillagerAnchor anchor : data.getCreepyVillageVillagerAnchors()) {
			Entity entity = world.getEntity(anchor.villagerUuid());
			if (entity instanceof Villager villager) {
				freezeVillagerAtDoor(villager, anchor.anchor(), anchor.door(), anchor.facing());
			}
		}

		removeUnanchoredVillagers(world, data, anchors);
	}

	private static boolean isVillagerAnchored(Villager villager, List<VillagerAnchor> anchors) {
		UUID uuid = villager.getUUID();
		for (VillagerAnchor anchor : anchors) {
			if (anchor.villagerUuid().equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	private static void removeUnanchoredVillagers(ServerLevel world, Fakeworld.FakeworldSaveData data, List<VillagerAnchor> anchors) {
		BlockPos center = data.getCreepyVillageCenter();
		AABB area = new AABB(center).inflate(data.getCreepyVillageRadius(), 24.0D, data.getCreepyVillageRadius());
		for (Villager villager : world.getEntities(EntityType.VILLAGER, area, v -> true)) {
			if (!isVillagerAnchored(villager, anchors)) {
				villager.discard();
			}
		}
	}

	private static void releaseAnchoredVillagers(ServerLevel world, Fakeworld.FakeworldSaveData data) {
		for (VillagerAnchor anchor : data.getCreepyVillageVillagerAnchors()) {
			Entity entity = world.getEntity(anchor.villagerUuid());
			if (entity instanceof Villager villager) {
				releaseVillager(villager);
			}
		}
		data.clearCreepyVillageVillagerAnchors();
	}

	private static void freezeVillagerAtDoor(Villager villager, BlockPos anchor, BlockPos door, Direction facing) {
		if (villager.isPassenger()) {
			villager.stopRiding();
		}

		villager.setSilent(true);
		villager.setNoAi(true);
		villager.setNoGravity(true);
		villager.setInvulnerable(true);
		villager.noPhysics = true;
		villager.setDeltaMovement(Vec3.ZERO);
		villager.hurtMarked = false;
		clearReplaceableStandSpace(worldFromVillager(villager), anchor);
		villager.teleportTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
		faceDoor(villager, door, facing);
	}

	private static ServerLevel worldFromVillager(Villager villager) {
		return (ServerLevel) villager.level();
	}

	private static void clearReplaceableStandSpace(ServerLevel world, BlockPos pos) {
		clearIfReplaceable(world, pos);
		clearIfReplaceable(world, pos.above());
	}

	private static void clearIfReplaceable(ServerLevel world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isAir() && state.getFluidState().isEmpty() && state.canBeReplaced()) {
			world.setBlock(pos, Blocks.AIR.defaultBlockState(), TEST_BLOCK_FLAGS);
		}
	}

	private static void releaseVillager(Villager villager) {
		villager.noPhysics = false;
		villager.setNoGravity(false);
		villager.setInvulnerable(false);
		villager.setSilent(false);
		villager.setNoAi(false);
		villager.setDeltaMovement(Vec3.ZERO);
	}

	private static void faceDoor(Villager villager, BlockPos door, Direction facing) {
		Vec3 lookTarget = Vec3.atCenterOf(door);
		float yaw = directionFromAnchorToDoor(villager.blockPosition(), door).toYRot();
		villager.lookAt(EntityAnchorArgument.Anchor.EYES, lookTarget);
		villager.setYRot(yaw);
		villager.yRotO = yaw;
		villager.setYHeadRot(yaw);
		villager.yHeadRotO = yaw;
		villager.setYBodyRot(yaw);
		villager.yBodyRotO = yaw;
	}

	private static Direction directionFromAnchorToDoor(BlockPos anchor, BlockPos door) {
		return Direction.getNearest(door.getX() - anchor.getX(), 0.0D, door.getZ() - anchor.getZ());
	}

	private record House(BlockPos door, BlockPos interior, Direction outsideDirection) {
	}

	private record VanillaVillagePiece(ResourceLocation id, int offsetX, int offsetZ, Rotation rotation) {
	}

	record VillagerAnchor(UUID villagerUuid, BlockPos anchor, BlockPos door, Direction facing) {
	}
}
