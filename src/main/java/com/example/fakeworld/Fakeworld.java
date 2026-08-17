package com.example.fakeworld;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.example.fakeworld.mixin.ServerLevelAccessor;
import com.example.fakeworld.mixin.StructureTemplateAccessor;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.api.ModInitializer;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Fakeworld implements ModInitializer {
	public static final String MOD_ID = "fakeworld";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final FakeworldConfig CONFIG = FakeworldConfig.load();
	public static final ResourceKey<Level> FAKE_OVERWORLD = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(MOD_ID, "fake_overworld"));
	public static final ResourceLocation DESKTOP_NOTE_PACKET = new ResourceLocation(MOD_ID, "desktop_note");
	private static final ResourceLocation JOURNAL_HOUSE_STRUCTURE = new ResourceLocation(MOD_ID, "house");
	private static final ResourceLocation CASTLE_STRUCTURE = new ResourceLocation(MOD_ID, "castle");
	private static final ResourceLocation CAMPSITE_STRUCTURE = new ResourceLocation(MOD_ID, "campsite");
	private static final ResourceLocation EMPTY_STRUCTURE = new ResourceLocation(MOD_ID, "empty");
	private static final ResourceLocation TOWER_STRUCTURE = new ResourceLocation(MOD_ID, "tower");
	private static final ResourceLocation YOU_CAME_BACK_ADVANCEMENT = new ResourceLocation(MOD_ID, "you_came_back");
	public static final EntityType<StalkerEntity> STALKER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			new ResourceLocation(MOD_ID, "stalker"),
			FabricEntityTypeBuilder.createMob()
					.entityFactory(StalkerEntity::new)
					.spawnGroup(MobCategory.MISC)
					.dimensions(EntityDimensions.scalable(0.6F, 1.95F))
					.trackRangeBlocks(96)
					.trackedUpdateRate(2)
					.disableSaving()
					.build()
	);
	public static final EntityType<HunterEntity> HUNTER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			new ResourceLocation(MOD_ID, "hunter"),
			FabricEntityTypeBuilder.createMob()
					.entityFactory(HunterEntity::new)
					.spawnGroup(MobCategory.MONSTER)
					.dimensions(EntityDimensions.scalable(0.6F, 1.95F))
					.trackRangeBlocks(96)
					.trackedUpdateRate(2)
					.disableSaving()
					.build()
	);
	public static final EntityType<MimicVillagerEntity> MIMIC_VILLAGER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			new ResourceLocation(MOD_ID, "mimic_villager"),
			FabricEntityTypeBuilder.createMob()
					.entityFactory(MimicVillagerEntity::new)
					.spawnGroup(MobCategory.MONSTER)
					.dimensions(EntityDimensions.scalable(0.6F, 1.95F))
					.trackRangeBlocks(96)
					.trackedUpdateRate(2)
					.disableSaving()
					.build()
	);
	public static final Block BLOOD_SPLATTER = Registry.register(
			BuiltInRegistries.BLOCK,
			new ResourceLocation(MOD_ID, "blood_splatter"),
			new BloodSplatterBlock(BlockBehaviour.Properties.of()
					.noCollission()
					.noOcclusion()
					.strength(-1.0F, 3600000.0F)
					.noLootTable()
					.replaceable()
					.sound(SoundType.HONEY_BLOCK))
	);
	public static final SoundEvent BLOOD_SPLATTER_SOUND = Registry.register(
			BuiltInRegistries.SOUND_EVENT,
			new ResourceLocation(MOD_ID, "blood_splatter"),
			SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "blood_splatter"))
	);
	public static final SoundEvent DARKNESS_SOUND = Registry.register(
			BuiltInRegistries.SOUND_EVENT,
			new ResourceLocation(MOD_ID, "darkness"),
			SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "darkness"))
	);
	public static final SoundEvent TERROR_SOUND = Registry.register(
			BuiltInRegistries.SOUND_EVENT,
			new ResourceLocation(MOD_ID, "terror"),
			SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "terror"))
	);

	public static final SoundEvent SOUNDEVENT_SOUND = Registry.register(
			BuiltInRegistries.SOUND_EVENT,
			new ResourceLocation(MOD_ID, "soundevent"),
			SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "soundevent"))
	);

	private static final int AMBIENT_EVENT_FAILED_PICK_RETRIES = 3;
	private static final int ABANDONED_HOME_SIGN_MAX_EVENTS = 3;
	private static final int DARKNESS_MAX_AMBIENT_EVENTS = 5;
	private static final int FAKE_JOIN_MAX_AMBIENT_EVENTS = 2;
	private static final int PHANTOM_MINING_MAX_AMBIENT_EVENTS = 3;
	private static final int SKY_OBJECT_MAX_AMBIENT_EVENTS = 2;
	private static final int SOUNDEVENT_MAX_AMBIENT_EVENTS = 2;
	private static final int STRUCTURE_REPAIR_START_DELAY_TICKS = 45 * 20;
	private static final int STRUCTURE_REPAIR_INTERVAL_TICKS = 20;
	private static final int STRUCTURE_REPAIR_PLAYER_RADIUS = 96;
	private static final int AMBIENT_STRUCTURE_MIN_DISTANCE = 96;
	private static final int AMBIENT_STRUCTURE_MAX_DISTANCE = 180;
	private static final StructureSpawnDefinition[] AMBIENT_STRUCTURES = {
			new StructureSpawnDefinition("castle", CASTLE_STRUCTURE),
			new StructureSpawnDefinition("campsite", CAMPSITE_STRUCTURE),
			new StructureSpawnDefinition("empty", EMPTY_STRUCTURE),
			new StructureSpawnDefinition("tower", TOWER_STRUCTURE)
	};
	private static final int DIRECTOR_PHASE_1_SCORE = 4;
	private static final int DIRECTOR_PHASE_2_SCORE = 10;
	private static final int DIRECTOR_PHASE_3_SCORE = 18;
	private static final int DIRECTOR_MOOD_DEFAULT_TICKS = 3;
	private static final int MIN_DIRECTOR_PRESSURE = 0;
	private static final int MAX_DIRECTOR_PRESSURE = 100;
	private static final int QUIET_DIRECTOR_PRESSURE_GAIN = 12;
	private static final int FAILED_EVENT_DIRECTOR_PRESSURE_GAIN = 5;
	private static final int ANIMAL_KILL_SCORE_INTERVAL = 10;
	private static final int ANIMAL_KILL_DIRECTOR_PRESSURE_GAIN = 1;
	private static final int STALKER_SPAWN_MIN_DISTANCE = 30;
	private static final int STALKER_SPAWN_MAX_DISTANCE = 44;
	private static final int JOURNAL_HOUSE_MIN_DISTANCE = 72;
	private static final int JOURNAL_HOUSE_MAX_DISTANCE = 128;
	private static final int JOURNAL_HOUSE_MESSAGE_DISTANCE = 14;
	private static final int JOURNAL_UPDATE_MIN_DELAY_TICKS = 10 * 20;
	private static final int JOURNAL_UPDATE_MAX_DELAY_TICKS = 30 * 20;
	private static final int BLOOD_DURATION_TICKS = 100;
	private static final int DARKNESS_DURATION_TICKS = 200;
	private static final int FOOTSTEP_COUNT = 6;
	private static final int FOOTSTEP_INTERVAL_TICKS = 8;
	private static final int FOOTSTEP_START_DISTANCE = 7;
	private static final int FAKE_JOIN_PRESENCE_DURATION_TICKS = 35 * 20;
	private static final int FAKE_JOIN_NAMETAG_MIN_DISTANCE = 18;
	private static final int FAKE_JOIN_NAMETAG_MAX_DISTANCE = 42;
	private static final int PHANTOM_MINING_MIN_DEPTH = 8;
	private static final int PHANTOM_MINING_MIN_LENGTH = 7;
	private static final int PHANTOM_MINING_MAX_LENGTH = 13;
	private static final int PHANTOM_MINING_INTERVAL_TICKS = 18;
	private static final int SKY_OBJECT_DURATION_TICKS = 9 * 20;
	private static final int SKY_OBJECT_DIRECTOR_PRESSURE_GAIN = 8;
	private static final float SKY_OBJECT_DAMAGE = 4.0F;
	private static final int SKY_OBJECT_ROOM_HALF_WIDTH = 13;
	private static final int SKY_OBJECT_ROOM_HALF_DEPTH = 13;
	private static final int SKY_OBJECT_ROOM_HEIGHT = 10;
	private static final double SKY_OBJECT_LOOK_DOT = 0.94D;
	private static final int ANIMAL_ATTENTION_RADIUS = 28;
	private static final int ANIMAL_ATTENTION_MAX_ANIMALS = 10;
	private static final double ANIMAL_ATTENTION_MIN_DISTANCE = 16.0D;
	private static final double ANIMAL_ATTENTION_SPEED = 1.05D;
	private static final int ALWAYS_STARE_ANIMAL_RADIUS = 36;
	private static final int ANIMAL_STARE_PATH_COOLDOWN_TICKS = 60;
	private static final int ANIMAL_STARE_EVENT_COOLDOWN_TICKS = 20 * 20;
	private static final double ANIMAL_STARE_APPROACH_MIN_SPEED_SQR = 0.0125D * 0.0125D;
	private static final double ANIMAL_STARE_APPROACH_DOT = 0.65D;
	private static final int DOPPELGANGER_LOADED_CHUNK_RADIUS = 8;
	private static final int DOPPELGANGER_DURATION_TICKS = 45 * 20;
	private static final int TAME_DOG_KILL_DELAY_TICKS = 3 * 20;
	private static final int TAME_DOG_SCAN_RADIUS = 128;
	private static final int HUNTER_WARNING_TICKS = 3 * 20;
	private static final int FAKE_SLEEP_GRACE_TICKS = 20;
	private static final String TAME_DOG_TAG = "FakeworldSittingDog";
	private static final String[] STALKER_SIGN_MESSAGES = {
			"YOU LEFT THEM",
			"WE STAYED FOREVER",
			"WHY ARE YOU BACK",
			"WHY DID YOU LEAVE US?"
	};
	private static final String JOURNAL_TITLE = "Journal";
	private static final String JOURNAL_AUTHOR = "unknown";
	private static final String JOURNAL_TAG = "FakeworldJournal";
	private static final String JOURNAL_STALKER_OBSERVATION_KEY = "FakeworldStalkerObservation";
	private static final String JOURNAL_HUNTER_OBSERVATION_KEY = "FakeworldHunterObservation";
	private static final String JOURNAL_STALKER_OBSERVATION = """
			Something watched from far away today.

			It did not come closer.
			It did not need to.

			Some things remember you best from a distance.""";
	private static final String JOURNAL_HUNTER_OBSERVATION = """
			The animal did not run from you.

			Afterward, something else began to.

			It has your face, but not your hesitation.""";
	private static final SimpleCommandExceptionType FAKEBLOOD_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeblood must be run by a player."));
	private static final SimpleCommandExceptionType FAKEBLOOD_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeblood only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEBELONG_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakebelong must be run by a player."));
	private static final SimpleCommandExceptionType FAKEBELONG_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakebelong only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEADVANCEMENT_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeadvancement must be run by a player."));
	private static final SimpleCommandExceptionType FAKEADVANCEMENT_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeadvancement only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEADVANCEMENT_ALREADY_SHOWN = new SimpleCommandExceptionType(Component.literal("The fake advancement has already happened in this world."));
	private static final SimpleCommandExceptionType FAKEADVANCEMENT_MISSING = new SimpleCommandExceptionType(Component.literal("The You Came Back advancement is missing."));
	private static final SimpleCommandExceptionType FAKESTALKER_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakestalker must be run by a player."));
	private static final SimpleCommandExceptionType FAKESTALKER_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakestalker only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKESTALKER_NO_POSITION = new SimpleCommandExceptionType(Component.literal("Could not find a valid stalker position."));
	private static final SimpleCommandExceptionType FAKEDARKNESS_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakedarkness must be run by a player."));
	private static final SimpleCommandExceptionType FAKEDARKNESS_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakedarkness only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKESHUFFLE_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeshuffle must be run by a player."));
	private static final SimpleCommandExceptionType FAKESHUFFLE_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeshuffle only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEFOOTSTEPS_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakefootsteps must be run by a player."));
	private static final SimpleCommandExceptionType FAKEFOOTSTEPS_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakefootsteps only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEJOIN_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakejoin must be run by a player."));
	private static final SimpleCommandExceptionType FAKEJOIN_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakejoin only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEMINING_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakemining must be run by a player."));
	private static final SimpleCommandExceptionType FAKEMINING_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakemining only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEMINING_NO_POSITION = new SimpleCommandExceptionType(Component.literal("Could not find a valid underground mining path."));
	private static final SimpleCommandExceptionType FAKESKYOBJECT_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeskyobject must be run by a player."));
	private static final SimpleCommandExceptionType FAKESKYOBJECT_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeskyobject only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEANIMALS_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeanimals must be run by a player."));
	private static final SimpleCommandExceptionType FAKEANIMALS_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeanimals only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEANIMALS_NO_ANIMALS = new SimpleCommandExceptionType(Component.literal("No passive animals were found nearby."));
	private static final SimpleCommandExceptionType FAKEDOPPELGANGER_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakeanimaldouble must be run by a player."));
	private static final SimpleCommandExceptionType FAKEDOPPELGANGER_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakeanimaldouble only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEDOPPELGANGER_NO_ANIMALS = new SimpleCommandExceptionType(Component.literal("No passive animal was found nearby."));
	private static final SimpleCommandExceptionType FAKEDOG_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakedog must be run by a player."));
	private static final SimpleCommandExceptionType FAKEDOG_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakedog only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEDOG_ALREADY_EXISTS = new SimpleCommandExceptionType(Component.literal("A dog is already waiting nearby."));
	private static final SimpleCommandExceptionType FAKEHOUSE_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakehouse must be run by a player."));
	private static final SimpleCommandExceptionType FAKEHOUSE_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakehouse only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEHOUSE_NO_POSITION = new SimpleCommandExceptionType(Component.literal("Could not find a valid journal house position."));
	private static final SimpleCommandExceptionType FAKEMIMIC_NEEDS_PLAYER = new SimpleCommandExceptionType(Component.literal("/fakemimic must be run by a player."));
	private static final SimpleCommandExceptionType FAKEMIMIC_NEEDS_FAKE_OVERWORLD = new SimpleCommandExceptionType(Component.literal("/fakemimic only works in the fake overworld."));
	private static final SimpleCommandExceptionType FAKEMIMIC_NEEDS_REPAIRED_VILLAGE = new SimpleCommandExceptionType(Component.literal("The mimic only appears after the village is restored."));
	private static final SimpleCommandExceptionType FAKEMIMIC_NO_POSITION = new SimpleCommandExceptionType(Component.literal("Could not find a valid mimic position."));
	private static final SimpleCommandExceptionType FAKESOUNDEVENT_NEEDS_PLAYER =
			new SimpleCommandExceptionType(Component.literal("fakesoundevent must be run by a player."));
	private static final SimpleCommandExceptionType FAKESOUNDEVENT_NEEDS_FAKE_OVERWORLD =
			new SimpleCommandExceptionType(Component.literal("fakesoundevent only works in the fake overworld."));
	private static final Set<UUID> FORCED_BLEEDING_TREE_BREAKS = new HashSet<>();
	private static final Map<ScheduledBloodSplatter, Integer> BLOOD_SPLATTER_EXPIRATIONS = new HashMap<>();
	private static final List<ScheduledFootsteps> SCHEDULED_FOOTSTEPS = new ArrayList<>();
	private static final List<FakeJoinPresence> FAKE_JOIN_PRESENCES = new ArrayList<>();
	private static final List<PhantomMiningEvent> PHANTOM_MINING_EVENTS = new ArrayList<>();
	private static final List<SkyObjectEvent> SKY_OBJECT_EVENTS = new ArrayList<>();
	private static final List<AnimalAttentionEvent> ANIMAL_ATTENTION_EVENTS = new ArrayList<>();
	private static final List<DoppelgangerAnimal> DOPPELGANGER_ANIMALS = new ArrayList<>();
	private static final List<PendingHunterSpawn> PENDING_HUNTER_SPAWNS = new ArrayList<>();
	private static final List<PendingStructureRepair> PENDING_STRUCTURE_REPAIRS = new ArrayList<>();
	private static final Map<UUID, Integer> RECENT_FAKE_OVERWORLD_SLEEPERS = new HashMap<>();
	private static final Map<UUID, Integer> TIMED_TERROR_PLAYERS = new HashMap<>();
	private static final Map<UUID, Integer> ANIMAL_STARE_PATH_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Integer> ANIMAL_STARE_EVENT_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Vec3> ANIMAL_STARE_PLAYER_POSITIONS = new HashMap<>();
	private static final Map<UUID, Integer> TAME_DOG_KILL_TIMERS = new HashMap<>();


	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(HUNTER, HunterEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MIMIC_VILLAGER, MimicVillagerEntity.createAttributes());
		CreepyVillageManager.registerEvents();
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof ServerPlayer player) {
				removeJournalBeforeDeath(player, getSaveData(player.server));
				if (damageSource.getEntity() instanceof HunterEntity) {
					stopTerrorSound(player);
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Fakeworld::afterLivingEntityDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				restoreJournalAfterDeath(newPlayer, getSaveData(newPlayer.server));
			}
		});
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player && player.level().dimension().equals(FAKE_OVERWORLD)) {
				RECENT_FAKE_OVERWORLD_SLEEPERS.remove(player.getUUID());
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}

			if (player.level() instanceof ServerLevel level
					&& level.dimension().equals(FAKE_OVERWORLD)
					&& isFakeOverworldSleepTime(level)
					&& player.isSleepingLongEnough()) {
				RECENT_FAKE_OVERWORLD_SLEEPERS.put(player.getUUID(), FAKE_SLEEP_GRACE_TICKS);
			} else {
				RECENT_FAKE_OVERWORLD_SLEEPERS.remove(player.getUUID());
			}
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel serverLevel)) {
				return;
			}

			FakeworldSaveData saveData = getSaveData(serverLevel.getServer());
			if (player instanceof ServerPlayer serverPlayer) {
				handleSpawnedStructureBreak(serverPlayer, serverLevel, pos, saveData);
			}

			if (canBleedFromChoppedLog(serverLevel, pos, state)) {
				boolean forced = FORCED_BLEEDING_TREE_BREAKS.remove(player.getUUID());
				if (forced || serverLevel.getRandom().nextInt(100) < CONFIG.bleedingTreeChancePercent) {
					placeTreeBlood(serverLevel, pos);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			FakeworldSaveData saveData = getSaveData(server);
			tickBloodSplatterExpirations(server.getAllLevels());
			tickScheduledFootsteps(server);
			tickFakeJoinPresences(server);
			tickPhantomMiningEvents(server);
			tickSkyObjectEvents(server);
			tickAnimalStarePathCooldowns();
			tickAlwaysStaringAnimals(server);
			tickAnimalAttention(server);
			tickDoppelgangerAnimals(server);
			tickTameDogTraps(server);
			tickPendingHunterSpawns(server);
			tickPendingStructureRepairs(server);
			tickTimedTerror(server);
			tickPendingJournalUpdates(server, saveData);
			tickJournalHouse(server, saveData);
			CreepyVillageManager.tick(server, saveData);
			tickFakeOverworldSleep(server);
			tickAmbientEventScheduler(server, saveData);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("fakeblood")
					.executes(context -> runFakeBloodCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakebelong")
					.executes(context -> runFakeBelongCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakeadvancement")
					.executes(context -> runFakeAdvancementCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakestalker")
					.executes(context -> runFakeStalkerCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakedarkness")
					.executes(context -> runFakeDarknessCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakeshuffle")
					.executes(context -> runFakeShuffleCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakefootsteps")
					.executes(context -> runFakeFootstepsCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakejoin")
					.executes(context -> runFakeJoinCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakemining")
					.executes(context -> runFakeMiningCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakeskyobject")
					.executes(context -> runFakeSkyObjectCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakeanimals")
					.executes(context -> runFakeAnimalsCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakeanimaldouble")
					.executes(context -> runFakeAnimalDoubleCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakedog")
					.executes(context -> runFakeDogCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakehouse")
					.executes(context -> runFakeHouseCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakemimic")
					.executes(context -> runFakeMimicCommand(context.getSource(), false))
					.then(Commands.literal("force")
							.executes(context -> runFakeMimicCommand(context.getSource(), true))));
			dispatcher.register(Commands.literal("fakestructure")
					.then(Commands.literal("castle")
							.executes(context -> runFakeStructureCommand(context.getSource(), AMBIENT_STRUCTURES[0])))
					.then(Commands.literal("campsite")
							.executes(context -> runFakeStructureCommand(context.getSource(), AMBIENT_STRUCTURES[1])))
					.then(Commands.literal("empty")
							.executes(context -> runFakeStructureCommand(context.getSource(), AMBIENT_STRUCTURES[2])))
					.then(Commands.literal("tower")
							.executes(context -> runFakeStructureCommand(context.getSource(), AMBIENT_STRUCTURES[3]))));
			dispatcher.register(Commands.literal("fakevillage")
					.executes(context -> CreepyVillageManager.runCommand(context.getSource()))
					.then(Commands.literal("test")
							.executes(context -> CreepyVillageManager.runTestCommand(context.getSource()))));
			dispatcher.register(Commands.literal("fakephase")
					.executes(context -> runFakePhaseCommand(context.getSource())));
			dispatcher.register(Commands.literal("fakesoundevent")
					.executes(context -> runFakeSoundEventCommand(context.getSource())));
		});

		LOGGER.info("Fake overworld is enabled.");
	}

	private static int runFakeBloodCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEBLOOD_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEBLOOD_NEEDS_FAKE_OVERWORLD.create();
		}

		FORCED_BLEEDING_TREE_BREAKS.add(player.getUUID());
		source.sendSuccess(() -> Component.literal("Your next valid chopped log will bleed."), false);
		return 1;
	}

	private static int runFakeBelongCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEBELONG_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEBELONG_NEEDS_FAKE_OVERWORLD.create();
		}

		FakeworldSaveData saveData = getSaveData(source.getServer());
		if (saveData.hasShownBelongingMessage()) {
			source.sendSuccess(() -> Component.literal("The feeling has already happened in this world."), false);
			return 0;
		}

		showBelongingMessage(player, saveData);
		return 1;
	}

	private static int runFakeAdvancementCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEADVANCEMENT_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEADVANCEMENT_NEEDS_FAKE_OVERWORLD.create();
		}

		FakeworldSaveData saveData = getSaveData(source.getServer());
		if (saveData.getDirectorEventCount("fake_advancement") > 0) {
			throw FAKEADVANCEMENT_ALREADY_SHOWN.create();
		}

		if (!showYouCameBackAdvancement(player)) {
			throw FAKEADVANCEMENT_MISSING.create();
		}

		saveData.recordDirectorEvent("fake_advancement", 0);
		return 1;
	}

	private static int runFakeStalkerCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKESTALKER_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKESTALKER_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!spawnStalkerFor(player, world)) {
			throw FAKESTALKER_NO_POSITION.create();
		}

		source.sendSuccess(() -> Component.literal("Spawned a stalker."), false);
		return 1;
	}

	private static int runFakeDarknessCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEDARKNESS_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEDARKNESS_NEEDS_FAKE_OVERWORLD.create();
		}

		triggerDarknessEvent(player);
		source.sendSuccess(() -> Component.literal("Triggered darkness."), false);
		return 1;
	}

	private static int runFakeShuffleCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKESHUFFLE_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKESHUFFLE_NEEDS_FAKE_OVERWORLD.create();
		}

		shuffleInventory(player, world);
		source.sendSuccess(() -> Component.literal("Shuffled your inventory."), false);
		return 1;
	}

	private static int runFakeFootstepsCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEFOOTSTEPS_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEFOOTSTEPS_NEEDS_FAKE_OVERWORLD.create();
		}

		scheduleFootstepsBehind(player, world);
		source.sendSuccess(() -> Component.literal("Started silent footsteps."), false);
		return 1;
	}

	private static int runFakeJoinCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEJOIN_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEJOIN_NEEDS_FAKE_OVERWORLD.create();
		}

		triggerFakeJoinEvent(player, world);
		source.sendSuccess(() -> Component.literal("Sent fake join."), false);
		return 1;
	}

	private static int runFakeMiningCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEMINING_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEMINING_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!startPhantomMining(player, world)) {
			throw FAKEMINING_NO_POSITION.create();
		}

		source.sendSuccess(() -> Component.literal("Something started mining."), false);
		return 1;
	}

	private static int runFakeSkyObjectCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKESKYOBJECT_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKESKYOBJECT_NEEDS_FAKE_OVERWORLD.create();
		}

		startSkyObjectEvent(player, world);
		source.sendSuccess(() -> Component.literal("Loaded sky object."), false);
		return 1;
	}

	private static int runFakeAnimalsCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEANIMALS_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEANIMALS_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!startAnimalAttention(player, world)) {
			throw FAKEANIMALS_NO_ANIMALS.create();
		}

		source.sendSuccess(() -> Component.literal("Nearby animals are watching."), false);
		return 1;
	}

	private static int runFakeAnimalDoubleCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEDOPPELGANGER_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEDOPPELGANGER_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!spawnAnimalDoppelganger(player, world)) {
			throw FAKEDOPPELGANGER_NO_ANIMALS.create();
		}

		source.sendSuccess(() -> Component.literal("Something copied an animal nearby."), false);
		return 1;
	}

	private static int runFakeDogCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEDOG_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEDOG_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!spawnTameDogFor(player, world)) {
			throw FAKEDOG_ALREADY_EXISTS.create();
		}

		source.sendSuccess(() -> Component.literal("A dog is waiting."), false);
		return 1;
	}

	private static int runFakeHouseCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEHOUSE_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEHOUSE_NEEDS_FAKE_OVERWORLD.create();
		}

		FakeworldSaveData saveData = getSaveData(source.getServer());
		if (!spawnJournalHouseNear(player, world, saveData)) {
			throw FAKEHOUSE_NO_POSITION.create();
		}

		source.sendSuccess(() -> Component.literal("Placed the journal house."), false);
		return 1;
	}

	private static int runFakeStructureCommand(CommandSourceStack source, StructureSpawnDefinition structure) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEHOUSE_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEHOUSE_NEEDS_FAKE_OVERWORLD.create();
		}

		if (!spawnAmbientStructureNear(player, world, getSaveData(source.getServer()), structure)) {
			throw FAKEHOUSE_NO_POSITION.create();
		}

		source.sendSuccess(() -> Component.literal("Placed " + structure.name() + "."), false);
		return 1;
	}

	private static int runFakeMimicCommand(CommandSourceStack source, boolean force) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw FAKEMIMIC_NEEDS_PLAYER.create();
		}

		if (!world.dimension().equals(FAKE_OVERWORLD)) {
			throw FAKEMIMIC_NEEDS_FAKE_OVERWORLD.create();
		}

		FakeworldSaveData saveData = getSaveData(source.getServer());
		if (!force && !saveData.isCreepyVillageRepaired()) {
			throw FAKEMIMIC_NEEDS_REPAIRED_VILLAGE.create();
		}

		if (!force && !canSpawnMimicFor(player, saveData, world)) {
			throw FAKEMIMIC_NO_POSITION.create();
		}

		if (!spawnMimicVillagerFor(player, world)) {
			throw FAKEMIMIC_NO_POSITION.create();
		}

		source.sendSuccess(() -> Component.literal(force ? "Forced mimic spawn." : "A mimic has appeared nearby."), false);
		return 1;
	}
	private static int runFakeSoundEventCommand(CommandSourceStack source) throws CommandSyntaxException {
		ServerLevel world = source.getLevel();
		ServerPlayer player = source.getPlayerOrException();
		if (!world.dimension().equals(FAKE_OVERWORLD)) throw FAKESOUNDEVENT_NEEDS_FAKE_OVERWORLD.create();
		triggerSoundEvent(player);
		source.sendSuccess(() -> Component.literal("Triggered sound event."), false);
		return 1;
	}
	private static int runFakePhaseCommand(CommandSourceStack source) {
		FakeworldSaveData saveData = getSaveData(source.getServer());
		source.sendSuccess(() -> Component.literal("Fakeworld phase " + saveData.getDirectorPhase()
				+ " score " + saveData.getDirectorScore()
				+ " mood " + saveData.getDirectorMood().name().toLowerCase()
				+ " pressure " + saveData.getDirectorPressure()), false);
		return 1;
	}

	private static boolean canBleedFromChoppedLog(ServerLevel world, BlockPos pos, BlockState state) {
		return world.dimension().equals(FAKE_OVERWORLD)
				&& state.is(BlockTags.LOGS)
				&& world.getBlockState(pos.below()).is(BlockTags.LOGS)
				&& hasNearbyLeaves(world, pos);
	}

	public static void placeTreeBlood(ServerLevel world, BlockPos choppedLogPos) {
		BlockState currentState = world.getBlockState(choppedLogPos);
		if (!currentState.isAir() && !currentState.canBeReplaced()) {
			return;
		}

		Direction facing = Direction.from2DDataValue(world.getRandom().nextInt(4));
		BlockState bloodState = BLOOD_SPLATTER.defaultBlockState().setValue(BloodSplatterBlock.FACING, facing);
		world.setBlock(choppedLogPos, bloodState, Block.UPDATE_ALL);
		world.playSound(null, choppedLogPos, BLOOD_SPLATTER_SOUND, SoundSource.BLOCKS, 0.75F, 0.82F + world.getRandom().nextFloat() * 0.12F);
		BLOOD_SPLATTER_EXPIRATIONS.put(new ScheduledBloodSplatter(world.dimension(), choppedLogPos.immutable()), BLOOD_DURATION_TICKS);
	}

	private static boolean hasNearbyLeaves(ServerLevel world, BlockPos choppedLogPos) {
		BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();
		for (int y = 0; y <= 7; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					searchPos.set(choppedLogPos.getX() + x, choppedLogPos.getY() + y, choppedLogPos.getZ() + z);
					if (world.getBlockState(searchPos).is(BlockTags.LEAVES)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static void tickBloodSplatterExpirations(Iterable<ServerLevel> levels) {
		Iterator<Map.Entry<ScheduledBloodSplatter, Integer>> iterator = BLOOD_SPLATTER_EXPIRATIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<ScheduledBloodSplatter, Integer> entry = iterator.next();
			int ticksRemaining = entry.getValue() - 1;
			if (ticksRemaining > 0) {
				entry.setValue(ticksRemaining);
				continue;
			}

			removeBloodSplatter(levels, entry.getKey());
			iterator.remove();
		}
	}

	private static void tickScheduledFootsteps(MinecraftServer server) {
		Iterator<ScheduledFootsteps> iterator = SCHEDULED_FOOTSTEPS.iterator();
		while (iterator.hasNext()) {
			ScheduledFootsteps footsteps = iterator.next();
			if (footsteps.delayTicks() > 0) {
				footsteps.setDelayTicks(footsteps.delayTicks() - 1);
				continue;
			}

			ServerLevel world = server.getLevel(footsteps.dimension());
			if (world == null) {
				iterator.remove();
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(footsteps.playerUuid());
			if (player == null || !player.level().dimension().equals(footsteps.dimension())) {
				iterator.remove();
				continue;
			}

			playFootstep(world, currentFootstepPos(player, world, footsteps.stepIndex(), footsteps.sideSign()), footsteps.stepIndex());
			if (footsteps.stepIndex() >= FOOTSTEP_COUNT - 1) {
				iterator.remove();
				continue;
			}

			footsteps.advance();
		}
	}

	private static void tickFakeJoinPresences(MinecraftServer server) {
		Iterator<FakeJoinPresence> iterator = FAKE_JOIN_PRESENCES.iterator();
		while (iterator.hasNext()) {
			FakeJoinPresence presence = iterator.next();
			ServerLevel world = server.getLevel(presence.dimension());
			Entity entity = world == null ? null : world.getEntity(presence.entityUuid());
			if (world == null || entity == null || !entity.isAlive()) {
				iterator.remove();
				continue;
			}

			presence.tick();
			if (presence.ticksRemaining() <= 0) {
				entity.discard();
				iterator.remove();
			}
		}
	}

	private static void tickPhantomMiningEvents(MinecraftServer server) {
		Iterator<PhantomMiningEvent> iterator = PHANTOM_MINING_EVENTS.iterator();
		while (iterator.hasNext()) {
			PhantomMiningEvent event = iterator.next();
			if (event.delayTicks() > 0) {
				event.setDelayTicks(event.delayTicks() - 1);
				continue;
			}

			ServerLevel world = server.getLevel(event.dimension());
			ServerPlayer player = server.getPlayerList().getPlayer(event.playerUuid());
			if (world == null || player == null || !player.level().dimension().equals(event.dimension())) {
				iterator.remove();
				continue;
			}

			if (event.nextIndex() >= event.path().size()) {
				iterator.remove();
				continue;
			}

			BlockPos pos = event.path().get(event.nextIndex());
			if (player.blockPosition().distSqr(pos) <= 2.25D) {
				iterator.remove();
				continue;
			}

			if (!breakPhantomMiningBlock(world, pos)) {
				iterator.remove();
				continue;
			}

			event.advance();
		}
	}

	private static void tickSkyObjectEvents(MinecraftServer server) {
		Iterator<SkyObjectEvent> iterator = SKY_OBJECT_EVENTS.iterator();
		while (iterator.hasNext()) {
			SkyObjectEvent event = iterator.next();
			ServerLevel world = server.getLevel(event.dimension());
			ServerPlayer player = server.getPlayerList().getPlayer(event.playerUuid());
			if (world == null) {
				iterator.remove();
				continue;
			}
			if (player == null || !player.level().dimension().equals(event.dimension())) {
				if (player != null) {
					stopSkyObjectSound(player);
					stopTerrorSound(player);
				}
				removeSkyObjectBlocks(world, event);
				iterator.remove();
				continue;
			}

			if (isLookingAtSkyObject(player, event.center()) && !event.hasPunishedLook()) {
				punishSkyObjectLook(player);
				event.setPunishedLook();
			}

			event.tick();
			if (event.ticksRemaining() <= 0) {
				stopSkyObjectSound(player);
				stopTerrorSound(player);
				removeSkyObjectBlocks(world, event);
				iterator.remove();
			}
		}
	}

	private static void tickAnimalAttention(MinecraftServer server) {
		Iterator<AnimalAttentionEvent> iterator = ANIMAL_ATTENTION_EVENTS.iterator();
		while (iterator.hasNext()) {
			AnimalAttentionEvent event = iterator.next();
			ServerLevel world = server.getLevel(event.dimension());
			ServerPlayer player = server.getPlayerList().getPlayer(event.playerUuid());
			if (world == null || player == null || !player.level().dimension().equals(event.dimension())) {
				releaseAnimalAttention(world, event);
				iterator.remove();
				continue;
			}

			List<Animal> animals = attentionAnimals(world, event);
			if (isPlayerApproachingAnimals(player, animals)) {
				releaseAnimalAttention(world, event);
				startAnimalStareEventCooldown(player);
				iterator.remove();
				continue;
			}

			for (Animal animal : animals) {
				focusAnimalOnPlayer(animal, player);
			}
		}
	}

	private static List<Animal> attentionAnimals(ServerLevel world, AnimalAttentionEvent event) {
		List<Animal> animals = new ArrayList<>();
		for (AnimalAttentionState state : event.animals()) {
			Entity entity = world.getEntity(state.animalUuid());
			if (entity instanceof Animal animal && !animal.isRemoved() && animal.isAlive()) {
				animals.add(animal);
			}
		}
		return animals;
	}

	private static void tickAnimalStarePathCooldowns() {
		Iterator<Map.Entry<UUID, Integer>> iterator = ANIMAL_STARE_PATH_COOLDOWNS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticks);
			}
		}

		iterator = ANIMAL_STARE_EVENT_COOLDOWNS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticks);
			}
		}
	}

	private static void tickAlwaysStaringAnimals(MinecraftServer server) {
		ServerLevel world = server.getLevel(FAKE_OVERWORLD);
		if (world == null || world.players().isEmpty()) {
			return;
		}

		for (ServerPlayer player : world.players()) {
			if (player.isCreative() || player.isSpectator()) {
				ANIMAL_STARE_PLAYER_POSITIONS.remove(player.getUUID());
				continue;
			}
			if (isAnimalStareEventCoolingDown(player)) {
				ANIMAL_STARE_PLAYER_POSITIONS.put(player.getUUID(), player.position());
				continue;
			}

			AABB area = new AABB(player.blockPosition()).inflate(ALWAYS_STARE_ANIMAL_RADIUS, 10.0D, ALWAYS_STARE_ANIMAL_RADIUS);
			List<Animal> animals = world.getEntities(EntityTypeTest.forClass(Animal.class), area, animal -> !animal.isRemoved() && animal.isAlive());
			if (isPlayerApproachingAnimals(player, animals)) {
				stopAnimalStareForAnimals(animals);
				startAnimalStareEventCooldown(player);
				continue;
			}

			for (Animal animal : animals) {
				ServerPlayer nearest = nearestFakeOverworldPlayer(world, animal);
				if (nearest == player) {
					focusAnimalOnPlayer(animal, player);
				}
			}
		}
	}

	private static ServerPlayer nearestFakeOverworldPlayer(ServerLevel world, Animal animal) {
		ServerPlayer nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : world.players()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}

			double distance = animal.distanceToSqr(player);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = player;
			}
		}
		return nearest;
	}

	private static boolean isAnimalStareEventCoolingDown(ServerPlayer player) {
		return ANIMAL_STARE_EVENT_COOLDOWNS.containsKey(player.getUUID());
	}

	private static void startAnimalStareEventCooldown(ServerPlayer player) {
		ANIMAL_STARE_EVENT_COOLDOWNS.put(player.getUUID(), ANIMAL_STARE_EVENT_COOLDOWN_TICKS);
		ANIMAL_STARE_PLAYER_POSITIONS.put(player.getUUID(), player.position());
	}

	private static boolean isPlayerApproachingAnimals(ServerPlayer player, List<Animal> animals) {
		Vec3 current = player.position();
		Vec3 previous = ANIMAL_STARE_PLAYER_POSITIONS.put(player.getUUID(), current);
		if (previous == null || animals.isEmpty()) {
			return false;
		}

		Vec3 movement = current.subtract(previous);
		Vec3 horizontalMovement = new Vec3(movement.x, 0.0D, movement.z);
		if (horizontalMovement.lengthSqr() < ANIMAL_STARE_APPROACH_MIN_SPEED_SQR) {
			return false;
		}

		Vec3 movementDirection = horizontalMovement.normalize();
		for (Animal animal : animals) {
			Vec3 toAnimal = animal.position().subtract(current);
			Vec3 horizontalToAnimal = new Vec3(toAnimal.x, 0.0D, toAnimal.z);
			if (horizontalToAnimal.lengthSqr() < 0.001D) {
				continue;
			}
			if (movementDirection.dot(horizontalToAnimal.normalize()) >= ANIMAL_STARE_APPROACH_DOT) {
				return true;
			}
		}
		return false;
	}

	private static void stopAnimalStareForAnimals(List<Animal> animals) {
		for (Animal animal : animals) {
			animal.getNavigation().stop();
			ANIMAL_STARE_PATH_COOLDOWNS.remove(animal.getUUID());
		}
	}

	private static void tickDoppelgangerAnimals(MinecraftServer server) {
		Iterator<DoppelgangerAnimal> iterator = DOPPELGANGER_ANIMALS.iterator();
		while (iterator.hasNext()) {
			DoppelgangerAnimal doppelganger = iterator.next();
			ServerLevel world = server.getLevel(doppelganger.dimension());
			if (world == null) {
				iterator.remove();
				continue;
			}

			Entity entity = world.getEntity(doppelganger.animalUuid());
			if (!(entity instanceof Animal animal) || animal.isRemoved()) {
				iterator.remove();
				continue;
			}

			doppelganger.decrementDuration();
			if (doppelganger.remainingTicks() <= 0) {
				animal.discard();
				iterator.remove();
				continue;
			}

		}
	}

	private static void tickTameDogTraps(MinecraftServer server) {
		ServerLevel world = server.getLevel(FAKE_OVERWORLD);
		if (world == null || world.players().isEmpty()) {
			TAME_DOG_KILL_TIMERS.clear();
			return;
		}

		Set<UUID> seenDogs = new HashSet<>();
		for (ServerPlayer player : world.players()) {
			AABB area = new AABB(player.blockPosition()).inflate(TAME_DOG_SCAN_RADIUS, 32.0D, TAME_DOG_SCAN_RADIUS);
			List<Wolf> dogs = world.getEntities(EntityTypeTest.forClass(Wolf.class), area, Fakeworld::isFakeworldTameDog);
			for (Wolf dog : dogs) {
				seenDogs.add(dog.getUUID());
				keepFakeworldDogSitting(dog, world);
				tickTameDogKillTimer(dog, world);
			}
		}

		TAME_DOG_KILL_TIMERS.keySet().removeIf(dogUuid -> !seenDogs.contains(dogUuid));
	}

	private static boolean isFakeworldTameDog(Wolf dog) {
		return !dog.isRemoved() && dog.isAlive() && dog.getTags().contains(TAME_DOG_TAG);
	}

	private static void keepFakeworldDogSitting(Wolf dog, ServerLevel world) {
		if (dog.isOrderedToSit()) {
			dog.getNavigation().stop();
			return;
		}

		dog.setOrderedToSit(true);
		dog.getNavigation().stop();
		if (TAME_DOG_KILL_TIMERS.containsKey(dog.getUUID())) {
			return;
		}

		TAME_DOG_KILL_TIMERS.put(dog.getUUID(), TAME_DOG_KILL_DELAY_TICKS);
		UUID ownerUuid = dog.getOwnerUUID();
		if (ownerUuid != null) {
			ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerUuid);
			if (owner != null && owner.level().dimension().equals(FAKE_OVERWORLD)) {
				owner.sendSystemMessage(Component.literal("You don't deserve a pet"));
			}
		}
	}

	private static void tickTameDogKillTimer(Wolf dog, ServerLevel world) {
		Integer timer = TAME_DOG_KILL_TIMERS.get(dog.getUUID());
		if (timer == null) {
			return;
		}

		int nextTimer = timer - 1;
		if (nextTimer > 0) {
			TAME_DOG_KILL_TIMERS.put(dog.getUUID(), nextTimer);
			return;
		}

		TAME_DOG_KILL_TIMERS.remove(dog.getUUID());
		dog.hurt(world.damageSources().genericKill(), Float.MAX_VALUE);
	}

	private static void tickPendingHunterSpawns(MinecraftServer server) {
		Iterator<PendingHunterSpawn> iterator = PENDING_HUNTER_SPAWNS.iterator();
		while (iterator.hasNext()) {
			PendingHunterSpawn pendingSpawn = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(pendingSpawn.playerUuid());
			if (player == null) {
				iterator.remove();
				continue;
			}
			if (!player.level().dimension().equals(pendingSpawn.dimension())) {
				stopTerrorSound(player);
				iterator.remove();
				continue;
			}

			pendingSpawn.decrementDelay();
			if (pendingSpawn.delayTicks() > 0) {
				continue;
			}

			ServerLevel world = server.getLevel(pendingSpawn.dimension());
			if (world != null) {
				spawnHunterAt(world, pendingSpawn.pos(), player);
			} else {
				stopTerrorSound(player);
			}
			iterator.remove();
		}
	}

	private static void tickPendingJournalUpdates(MinecraftServer server, FakeworldSaveData saveData) {
		if (!saveData.hasPendingStalkerJournalUpdates()) {
			if (!saveData.hasPendingHunterJournalUpdates()) {
				return;
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean changed = false;
			if (saveData.hasPendingStalkerJournalUpdate(player.getUUID())) {
				if (saveData.decrementStalkerJournalDelay(player.getUUID()) <= 0) {
					saveData.addStalkerJournalEntry(player.getUUID());
					if (updatePlayerJournal(player, saveData)) {
						saveData.clearPendingStalkerJournalUpdate(player.getUUID());
						changed = true;
					} else if (hasJournal(player)) {
						saveData.clearPendingStalkerJournalUpdate(player.getUUID());
					}
				}
			}
			if (saveData.hasPendingHunterJournalUpdate(player.getUUID())) {
				if (saveData.decrementHunterJournalDelay(player.getUUID()) <= 0) {
					saveData.addHunterJournalEntry(player.getUUID());
					if (updatePlayerJournal(player, saveData)) {
						saveData.clearPendingHunterJournalUpdate(player.getUUID());
						changed = true;
					} else if (hasJournal(player)) {
						saveData.clearPendingHunterJournalUpdate(player.getUUID());
					}
				}
			}
			if (changed) {
				notifyJournalChanged(player);
			}
		}
	}

	private static void tickPendingStructureRepairs(MinecraftServer server) {
		Iterator<PendingStructureRepair> iterator = PENDING_STRUCTURE_REPAIRS.iterator();
		while (iterator.hasNext()) {
			PendingStructureRepair repair = iterator.next();
			ServerLevel world = server.getLevel(repair.dimension());
			if (world == null) {
				iterator.remove();
				continue;
			}

			if (repair.startDelayTicks() > 0) {
				repair.decrementStartDelay();
				continue;
			}

			if (!hasPlayerNearStructureRepair(world, repair)) {
				continue;
			}

			if (repair.delayTicks() > 0) {
				repair.decrementDelay();
				continue;
			}

			repair.setDelayTicks(STRUCTURE_REPAIR_INTERVAL_TICKS);
			if (repairNextChangedStructureBlock(world, repair)) {
				continue;
			}

			iterator.remove();
		}
	}

	private static void tickJournalHouse(MinecraftServer server, FakeworldSaveData saveData) {
		if (!saveData.hasJournalHouse()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.level().dimension().equals(saveData.getJournalHouseDimension())) {
				continue;
			}
			if (saveData.hasSeenJournalHouseMessage(player.getUUID())) {
				continue;
			}
			if (player.blockPosition().distSqr(saveData.getJournalHousePos()) > JOURNAL_HOUSE_MESSAGE_DISTANCE * JOURNAL_HOUSE_MESSAGE_DISTANCE) {
				continue;
			}

			player.displayClientMessage(Component.literal("I feel like i know this house..."), true);
			saveData.setSeenJournalHouseMessage(player.getUUID());
		}
	}

	private static void tickAmbientEventScheduler(MinecraftServer server, FakeworldSaveData saveData) {
		if (!CONFIG.ambientDirectorEnabled) {
			return;
		}

		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null) {
			return;
		}

		List<ServerPlayer> fakeWorldPlayers = getFakeWorldPlayers(server);
		if (fakeWorldPlayers.isEmpty()) {
			return;
		}

		if (!saveData.hasInitializedAmbientEventDelay()) {
			saveData.setAmbientEventDelayTicks(randomInitialAmbientEventDelay(fakeOverworld));
			saveData.setInitializedAmbientEventDelay();
			return;
		}

		saveData.tickAmbientEventCooldowns(1);
		saveData.decrementAmbientEventDelayTicks();
		if (saveData.getAmbientEventDelayTicks() > 0) {
			return;
		}

		saveData.setAmbientEventDelayTicks(randomAmbientEventDelay(fakeOverworld, saveData));
		runAmbientEventDirector(fakeOverworld, fakeWorldPlayers, saveData);
	}

	private static void tickFakeOverworldSleep(MinecraftServer server) {
		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null || !isFakeOverworldSleepTime(fakeOverworld)) {
			tickRecentFakeOverworldSleepers(server, false);
			return;
		}

		FakeworldSaveData saveData = getSaveData(server);

		int eligiblePlayers = 0;
		int sleepingPlayers = 0;
		for (ServerPlayer player : fakeOverworld.players()) {
			if (player.isSpectator()) {
				continue;
			}

			eligiblePlayers++;
			if (player.isSleepingLongEnough() || RECENT_FAKE_OVERWORLD_SLEEPERS.containsKey(player.getUUID())) {
				sleepingPlayers++;
			}
		}

		if (eligiblePlayers <= 0) {
			tickRecentFakeOverworldSleepers(server, false);
			return;
		}

		int sleepingPercentage = fakeOverworld.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
		int requiredSleepers = Math.max(1, Mth.ceil(eligiblePlayers * sleepingPercentage / 100.0F));
		if (sleepingPlayers < requiredSleepers) {
			tickRecentFakeOverworldSleepers(server, false);
			return;
		}

		saveData.recordDirectorEvent("sleep", 1);
		advanceFakeOverworldToMorning(server, fakeOverworld);
		wakeSleepingFakeOverworldPlayers(fakeOverworld);
		tickRecentFakeOverworldSleepers(server, true);
	}

	private static boolean isFakeOverworldSleepTime(ServerLevel fakeOverworld) {
		long dayTime = fakeOverworld.getDayTime() % 24000L;
		return fakeOverworld.isThundering() || dayTime >= 12542L && dayTime <= 23460L;
	}

	private static void tickRecentFakeOverworldSleepers(MinecraftServer server, boolean clear) {
		if (clear) {
			RECENT_FAKE_OVERWORLD_SLEEPERS.clear();
			return;
		}

		Iterator<Map.Entry<UUID, Integer>> iterator = RECENT_FAKE_OVERWORLD_SLEEPERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.level().dimension().equals(FAKE_OVERWORLD)) {
				iterator.remove();
				continue;
			}

			int ticks = entry.getValue() - 1;
			if (ticks <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticks);
			}
		}
	}

	private static void advanceFakeOverworldToMorning(MinecraftServer server, ServerLevel fakeOverworld) {
		long dayTime = fakeOverworld.getDayTime() + 24000L;
		long morning = dayTime - dayTime % 24000L;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().equals(FAKE_OVERWORLD) || level.dimension().equals(Level.OVERWORLD)) {
				level.setDayTime(morning);
				level.setWeatherParameters(0, 0, false, false);
			}
		}
	}

	private static void wakeSleepingFakeOverworldPlayers(ServerLevel fakeOverworld) {
		((ServerLevelAccessor) fakeOverworld).fakeworld$wakeUpAllPlayers();
		fakeOverworld.updateSleepingPlayerList();
	}

	private static int randomJournalUpdateDelay(ServerPlayer player) {
		return JOURNAL_UPDATE_MIN_DELAY_TICKS + player.getRandom().nextInt(JOURNAL_UPDATE_MAX_DELAY_TICKS - JOURNAL_UPDATE_MIN_DELAY_TICKS + 1);
	}

	private static List<ServerPlayer> getFakeWorldPlayers(MinecraftServer server) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension().equals(FAKE_OVERWORLD)) {
				players.add(player);
			}
		}

		return players;
	}

	private static void runAmbientEventDirector(ServerLevel fakeOverworld, List<ServerPlayer> fakeWorldPlayers, FakeworldSaveData saveData) {
		saveData.updateDirectorPhase();
		DirectorMood mood = saveData.getDirectorMood();
		List<AmbientEventCandidate> candidates = new ArrayList<>();

		if (!saveData.hasShownBelongingMessage()
				&& canQueueAmbientEvent(saveData, "belonging", 0)) {
			candidates.add(new AmbientEventCandidate("belonging", eventWeight(saveData, mood, "belonging", CONFIG.belongingMessageWeight, 0), cooldownTicks(CONFIG.belongingMessageCooldownMinutes), 1, () -> {
					ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
					showBelongingMessage(target, saveData);
					return true;
				}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("fake_advancement") <= 0
				&& canQueueAmbientEvent(saveData, "fake_advancement", 1)) {
			candidates.add(new AmbientEventCandidate("fake_advancement", eventWeight(saveData, mood, "fake_advancement", CONFIG.fakeAdvancementWeight, 1), cooldownTicks(CONFIG.fakeAdvancementCooldownMinutes), 1, () -> {
				return showYouCameBackAdvancement(randomPlayer(fakeWorldPlayers, fakeOverworld));
			}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& !saveData.hasJournalHouse()
				&& canQueueAmbientEvent(saveData, "journal_house", 1)) {
			candidates.add(new AmbientEventCandidate("journal_house", eventWeight(saveData, mood, "journal_house", CONFIG.journalHouseWeight, 1), cooldownTicks(CONFIG.journalHouseCooldownMinutes), 2,
					() -> spawnJournalHouseNear(randomPlayer(fakeWorldPlayers, fakeOverworld), fakeOverworld, saveData)));
		}

		for (StructureSpawnDefinition structure : AMBIENT_STRUCTURES) {
			String eventKey = "structure_" + structure.name();
			if (saveData.getDirectorPhase() >= 1 && canQueueAmbientEvent(saveData, eventKey, 1)) {
				candidates.add(new AmbientEventCandidate(eventKey, eventWeight(saveData, mood, eventKey, structure.chance(), 1), structure.cooldownTicks(), 2,
						() -> spawnAmbientStructureNear(randomPlayer(fakeWorldPlayers, fakeOverworld), fakeOverworld, saveData, structure)));
			}
		}

		if (saveData.getDirectorPhase() >= 1
				&& !hasActiveStalker(fakeOverworld)
				&& canQueueAmbientEvent(saveData, "stalker", 1)) {
			candidates.add(new AmbientEventCandidate("stalker", eventWeight(saveData, mood, "stalker", CONFIG.stalkerWeight, 1), cooldownTicks(CONFIG.stalkerCooldownMinutes), 2, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (!spawnStalkerFor(target, fakeOverworld)) {
					return false;
				}
				return true;
			}));
		}

		ServerPlayer darknessTarget = randomDarknessCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 1
				&& darknessTarget != null
				&& saveData.getDirectorEventCount("darkness") < DARKNESS_MAX_AMBIENT_EVENTS
				&& canQueueAmbientEvent(saveData, "darkness", 1)) {
			candidates.add(new AmbientEventCandidate(
					"darkness",
					eventWeight(saveData, mood, "darkness", CONFIG.darknessWeight, 1),
					cooldownTicks(CONFIG.darknessCooldownMinutes),
					1,
					() -> {
						triggerDarknessEvent(darknessTarget);
						return true;
					}
			));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("soundevent") < SOUNDEVENT_MAX_AMBIENT_EVENTS
				&& canQueueAmbientEvent(saveData, "soundevent", 1)) {
			candidates.add(new AmbientEventCandidate(
					"soundevent",
					eventWeight(saveData, mood, "soundevent", CONFIG.soundeventWeight, 1),
					cooldownTicks(CONFIG.soundeventCooldownMinutes),
					1,
					() -> {
						triggerSoundEvent(randomPlayer(fakeWorldPlayers, fakeOverworld));
						return true;
					}
			));
		}

		if (saveData.getDirectorPhase() >= 2
				&& canQueueAmbientEvent(saveData, "inventory_shuffle", 2)) {
			candidates.add(new AmbientEventCandidate("inventory_shuffle", eventWeight(saveData, mood, "inventory_shuffle", CONFIG.inventoryShuffleWeight, 2), cooldownTicks(CONFIG.inventoryShuffleCooldownMinutes), 2, () -> {
					ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
					shuffleInventory(target, fakeOverworld);
					return true;
				}));
		}

		if (canQueueAmbientEvent(saveData, "footsteps", 0)) {
			candidates.add(new AmbientEventCandidate("footsteps", eventWeight(saveData, mood, "footsteps", CONFIG.footstepsWeight, 0), cooldownTicks(CONFIG.footstepsCooldownMinutes), 1, () -> {
					ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
					scheduleFootstepsBehind(target, fakeOverworld);
					return true;
				}));
		}

		if (saveData.getDirectorPhase() >= 2
				&& saveData.getDirectorEventCount("fake_join") < FAKE_JOIN_MAX_AMBIENT_EVENTS
				&& canQueueAmbientEvent(saveData, "fake_join", 2)) {
			candidates.add(new AmbientEventCandidate("fake_join", eventWeight(saveData, mood, "fake_join", CONFIG.fakeJoinWeight, 2), cooldownTicks(CONFIG.fakeJoinCooldownMinutes), 3, () -> {
					ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
					return triggerFakeJoinEvent(target, fakeOverworld);
				}));
		}

		ServerPlayer miningTarget = randomPhantomMiningCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 2
				&& miningTarget != null
				&& saveData.getDirectorEventCount("phantom_mining") < PHANTOM_MINING_MAX_AMBIENT_EVENTS
				&& canQueueAmbientEvent(saveData, "phantom_mining", 2)) {
			candidates.add(new AmbientEventCandidate("phantom_mining", eventWeight(saveData, mood, "phantom_mining", CONFIG.phantomMiningWeight, 2), cooldownTicks(CONFIG.phantomMiningCooldownMinutes), 3,
					() -> startPhantomMining(miningTarget, fakeOverworld)));
		}

		ServerPlayer skyObjectTarget = randomSkyObjectCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 2
				&& skyObjectTarget != null
				&& saveData.getDirectorEventCount("sky_object") < SKY_OBJECT_MAX_AMBIENT_EVENTS
				&& canQueueAmbientEvent(saveData, "sky_object", 2)) {
			candidates.add(new AmbientEventCandidate("sky_object", eventWeight(saveData, mood, "sky_object", CONFIG.skyObjectWeight, 2), cooldownTicks(CONFIG.skyObjectCooldownMinutes), 3,
					() -> startSkyObjectEvent(skyObjectTarget, fakeOverworld)));
		}

		ServerPlayer animalAttentionTarget = randomAnimalAttentionCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 2
				&& animalAttentionTarget != null
				&& canQueueAmbientEvent(saveData, "animal_attention", 2)) {
			candidates.add(new AmbientEventCandidate("animal_attention", eventWeight(saveData, mood, "animal_attention", CONFIG.animalAttentionWeight, 2), cooldownTicks(CONFIG.animalAttentionCooldownMinutes), 2, () -> {
				if (!startAnimalAttention(animalAttentionTarget, fakeOverworld)) {
					return false;
				}
				return true;
			}));
		}

		ServerPlayer doppelgangerTarget = randomDoppelgangerCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 2
				&& doppelgangerTarget != null
				&& canQueueAmbientEvent(saveData, "animal_doppelganger", 2)) {
			candidates.add(new AmbientEventCandidate("animal_doppelganger", eventWeight(saveData, mood, "animal_doppelganger", CONFIG.animalDoppelgangerWeight, 2), cooldownTicks(CONFIG.animalDoppelgangerCooldownMinutes), 3, () -> {
				if (!spawnAnimalDoppelganger(doppelgangerTarget, fakeOverworld)) {
					return false;
				}
				return true;
			}));
		}

		ServerPlayer tameDogTarget = randomTameDogCandidate(fakeWorldPlayers, fakeOverworld);
		if (saveData.getDirectorPhase() >= 2
				&& tameDogTarget != null
				&& canQueueAmbientEvent(saveData, "tame_dog", 2)) {
			candidates.add(new AmbientEventCandidate("tame_dog", eventWeight(saveData, mood, "tame_dog", CONFIG.tameDogWeight, 2), cooldownTicks(CONFIG.tameDogCooldownMinutes), 2, () -> spawnTameDogFor(tameDogTarget, fakeOverworld)));
		}

		ServerPlayer mimicTarget = randomMimicCandidate(fakeWorldPlayers, saveData, fakeOverworld);
		if (saveData.getDirectorPhase() >= 3
				&& mimicTarget != null
				&& !hasActiveMimicVillager(fakeOverworld)
				&& canQueueAmbientEvent(saveData, "mimic_villager", 3)) {
			candidates.add(new AmbientEventCandidate("mimic_villager", eventWeight(saveData, mood, "mimic_villager", CONFIG.mimicVillagerWeight, 3), cooldownTicks(CONFIG.mimicVillagerCooldownMinutes), 4, () -> {
				if (!spawnMimicVillagerFor(mimicTarget, fakeOverworld)) {
					return false;
				}
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 3
				&& !saveData.hasActiveCreepyVillage()
				&& canQueueAmbientEvent(saveData, "creepy_village", 3)) {
			candidates.add(new AmbientEventCandidate("creepy_village", eventWeight(saveData, mood, "creepy_village", CONFIG.creepyVillageWeight, 3), cooldownTicks(CONFIG.creepyVillageCooldownMinutes), 5, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				return CreepyVillageManager.spawnCreepyVillageFor(target, fakeOverworld, saveData);
			}));
		}

		if (saveData.getDirectorPhase() >= 3
				&& saveData.isCreepyVillageRepaired()
				&& saveData.getDirectorEventCount("abandoned_home_sign") < ABANDONED_HOME_SIGN_MAX_EVENTS
				&& canQueueAmbientEvent(saveData, "abandoned_home_sign", 3)) {
			candidates.add(new AmbientEventCandidate("abandoned_home_sign", eventWeight(saveData, mood, "abandoned_home_sign", CONFIG.abandonedHomeSignWeight, 3), cooldownTicks(CONFIG.abandonedHomeSignCooldownMinutes), 4, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				return placeAbandonedHomeSignOnPlayer(target, fakeOverworld);
			}));
		}

		pickAndRunAmbientEvent(fakeOverworld, saveData, mood, candidates);
	}

	private static boolean canQueueAmbientEvent(FakeworldSaveData saveData, String eventKey, int minimumPhase) {
		return saveData.getDirectorPhase() >= minimumPhase && saveData.getAmbientEventCooldown(eventKey) <= 0;
	}

	private static int eventWeight(FakeworldSaveData saveData, DirectorMood mood, String eventKey, int baseWeight, int minimumPhase) {
		int phaseBonus = Math.max(0, saveData.getDirectorPhase() - minimumPhase) * 8;
		int repetitionPenalty = saveData.getDirectorEventCount(eventKey) * 12;
		int weight = (baseWeight * mood.weightPercent(eventKey)) / 100 + saveData.getDirectorPressure() + phaseBonus - repetitionPenalty;
		if (eventKey.equals(saveData.getLastAmbientEventKey())) {
			weight /= 4;
		}
		return Mth.clamp(weight, 1, 300);
	}

	private static int cooldownTicks(int minutes) {
		return CONFIG.cooldownTicks(minutes);
	}

	private static void broadcastAmbientEventDebug(ServerLevel fakeOverworld, String eventKey) {
		if (!CONFIG.ambientDebugMessages) {
			return;
		}

		for (ServerPlayer player : fakeOverworld.players()) {
			player.sendSystemMessage(Component.literal("[Fakeworld test] Ambient event: " + eventKey));
		}
	}

	private static void pickAndRunAmbientEvent(ServerLevel fakeOverworld, FakeworldSaveData saveData, DirectorMood mood, List<AmbientEventCandidate> candidates) {
		for (int retry = 0; retry < AMBIENT_EVENT_FAILED_PICK_RETRIES && !candidates.isEmpty(); retry++) {
			int quietWeight = (CONFIG.ambientQuietWeight * mood.quietWeightPercent()) / 100;
			quietWeight = Math.max(10, quietWeight - saveData.getDirectorPressure());
			int totalWeight = quietWeight;
			for (AmbientEventCandidate candidate : candidates) {
				totalWeight += candidate.weight();
			}

			int selected = fakeOverworld.getRandom().nextInt(totalWeight);
			if (selected < quietWeight) {
				saveData.setLastAmbientEventKey("");
				saveData.addDirectorPressure(QUIET_DIRECTOR_PRESSURE_GAIN);
				saveData.advanceDirectorMood(nextMoodAfterQuiet(fakeOverworld, saveData, mood));
				return;
			}

			selected -= quietWeight;
			for (int i = 0; i < candidates.size(); i++) {
				AmbientEventCandidate candidate = candidates.get(i);
				selected -= candidate.weight();
				if (selected >= 0) {
					continue;
				}

				if (candidate.action().tryRun()) {
					saveData.setAmbientEventCooldown(candidate.eventKey(), candidate.cooldownTicks());
					saveData.recordDirectorEvent(candidate.eventKey(), candidate.directorScore());
					saveData.setLastAmbientEventKey(candidate.eventKey());
					saveData.addDirectorPressure(-directorPressureRelief(candidate.directorScore()));
					saveData.advanceDirectorMood(nextMoodAfterEvent(fakeOverworld, saveData, mood, candidate.eventKey()));
					broadcastAmbientEventDebug(fakeOverworld, candidate.eventKey());
					return;
				}

				candidates.remove(i);
				saveData.addDirectorPressure(FAILED_EVENT_DIRECTOR_PRESSURE_GAIN);
				break;
			}
		}
	}

	private static int directorPressureRelief(int directorScore) {
		return 14 + Math.max(0, directorScore) * 8;
	}

	private static int randomAmbientEventDelay(ServerLevel fakeOverworld, FakeworldSaveData saveData) {
		int minDelayTicks = CONFIG.ambientEventMinDelaySeconds * 20;
		int maxDelayTicks = CONFIG.ambientEventMaxDelaySeconds * 20;
		int span = maxDelayTicks - minDelayTicks + 1;
		int delay = minDelayTicks + fakeOverworld.getRandom().nextInt(span);
		int pressureReduction = Mth.clamp(saveData.getDirectorPressure() * 20 + saveData.getDirectorPhase() * 10 * 20, 0, 65 * 20);
		delay = (delay * saveData.getDirectorMood().delayPercent()) / 100;
		return Math.max(30 * 20, delay - pressureReduction);
	}

	private static int randomInitialAmbientEventDelay(ServerLevel fakeOverworld) {
		int minDelay = 90 * 20;
		int maxDelay = 180 * 20;
		return minDelay + fakeOverworld.getRandom().nextInt(maxDelay - minDelay + 1);
	}

	private static DirectorMood nextMoodAfterQuiet(ServerLevel fakeOverworld, FakeworldSaveData saveData, DirectorMood currentMood) {
		return switch (currentMood) {
			case QUIET -> saveData.getDirectorPhase() >= 1 && fakeOverworld.getRandom().nextInt(100) < 65 ? DirectorMood.WATCHING : DirectorMood.QUIET;
			case WATCHING -> fakeOverworld.getRandom().nextInt(100) < 45 ? DirectorMood.STALKING : DirectorMood.QUIET;
			case STALKING -> fakeOverworld.getRandom().nextInt(100) < 35 ? DirectorMood.PUNISHING : DirectorMood.WATCHING;
			case PUNISHING, AFTERMATH -> DirectorMood.QUIET;
		};
	}

	private static DirectorMood nextMoodAfterEvent(ServerLevel fakeOverworld, FakeworldSaveData saveData, DirectorMood currentMood, String eventKey) {
		if (eventKey.equals("stalker") || eventKey.equals("darkness")) {
			return fakeOverworld.getRandom().nextInt(100) < 60 ? DirectorMood.AFTERMATH : DirectorMood.STALKING;
		}
		if (eventKey.equals("inventory_shuffle") || eventKey.equals("animal_doppelganger") || eventKey.equals("mimic_villager") || eventKey.equals("creepy_village")) {
			return DirectorMood.AFTERMATH;
		}
		if (eventKey.equals("footsteps") || eventKey.equals("animal_attention")) {
			return saveData.getDirectorPhase() >= 2 && fakeOverworld.getRandom().nextInt(100) < 45 ? DirectorMood.STALKING : DirectorMood.WATCHING;
		}
		if (eventKey.startsWith("structure_") || eventKey.equals("journal_house")) {
			return fakeOverworld.getRandom().nextInt(100) < 55 ? DirectorMood.WATCHING : DirectorMood.QUIET;
		}

		return currentMood == DirectorMood.QUIET ? DirectorMood.WATCHING : currentMood;
	}

	private static ServerPlayer randomPlayer(List<ServerPlayer> players, ServerLevel fakeOverworld) {
		return players.get(fakeOverworld.getRandom().nextInt(players.size()));
	}

	@FunctionalInterface
	private interface AmbientEventAction {
		boolean tryRun();
	}

	private static ServerPlayer randomDarknessCandidate(List<ServerPlayer> players, ServerLevel fakeOverworld) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!player.hasEffect(MobEffects.DARKNESS)) {
				candidates.add(player);
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return randomPlayer(candidates, fakeOverworld);
	}

	private static boolean showYouCameBackAdvancement(ServerPlayer player) {
		Advancement advancement = player.server.getAdvancements().getAdvancement(YOU_CAME_BACK_ADVANCEMENT);
		if (advancement == null) {
			return false;
		}

		return player.getAdvancements().award(advancement, "came_back");
	}

	private static ServerPlayer randomMimicCandidate(List<ServerPlayer> players, FakeworldSaveData saveData, ServerLevel world) {
		if (!saveData.isCreepyVillageRepaired()) {
			return null;
		}

		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (canSpawnMimicFor(player, saveData, world)) {
				candidates.add(player);
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return randomPlayer(candidates, world);
	}

	private static boolean canSpawnMimicFor(ServerPlayer player, FakeworldSaveData saveData, ServerLevel world) {
		if (!saveData.isCreepyVillageRepaired()) {
			return false;
		}

		if (!player.level().dimension().equals(world.dimension()) || player.isCreative() || player.isSpectator()) {
			return false;
		}

		if (!saveData.getCreepyVillageDimension().equals(world.dimension())) {
			return false;
		}

		BlockPos center = saveData.getCreepyVillageCenter();
		int radius = Math.max(1, saveData.getCreepyVillageRadius());
		double maxDistance = radius + 36.0D;
		return player.blockPosition().distSqr(center) <= maxDistance * maxDistance;
	}
	private static void triggerSoundEvent(ServerPlayer player) {
		playAttachedSound(player, SOUNDEVENT_SOUND, SoundSource.AMBIENT, 1.0F, 1.0F);
	}
	private static void triggerDarknessEvent(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DARKNESS_DURATION_TICKS, 0, false, false, true));
		playAttachedSound(player, DARKNESS_SOUND, SoundSource.AMBIENT, 1.0F, 1.0F);
	}

	private static void shuffleInventory(ServerPlayer player, ServerLevel world) {
		List<ItemStack> stacks = new ArrayList<>();
		stacks.addAll(player.getInventory().items);
		stacks.addAll(player.getInventory().armor);
		stacks.addAll(player.getInventory().offhand);
		for (int i = stacks.size() - 1; i > 0; i--) {
			Collections.swap(stacks, i, world.getRandom().nextInt(i + 1));
		}

		int index = 0;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			player.getInventory().items.set(i, stacks.get(index++));
		}
		for (int i = 0; i < player.getInventory().armor.size(); i++) {
			player.getInventory().armor.set(i, stacks.get(index++));
		}
		for (int i = 0; i < player.getInventory().offhand.size(); i++) {
			player.getInventory().offhand.set(i, stacks.get(index++));
		}

		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.literal("Everything is somewhere else."), true);
		playAttachedSound(player, SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.75F, 1.35F);
	}

	private static void scheduleFootstepsBehind(ServerPlayer player, ServerLevel world) {
		SCHEDULED_FOOTSTEPS.add(new ScheduledFootsteps(world.dimension(), player.getUUID(), world.getRandom().nextBoolean() ? 1 : -1));
	}

	private static boolean triggerFakeJoinEvent(ServerPlayer target, ServerLevel world) {
		Component joinMessage = Component.literal("Observer joined the game").withStyle(ChatFormatting.YELLOW);
		for (ServerPlayer player : world.players()) {
			player.sendSystemMessage(joinMessage);
		}
		sendDesktopNote(target);

		Optional<BlockPos> nametagPos = findFakeJoinNametagPos(target, world);
		if (nametagPos.isEmpty()) {
			playAttachedSound(target, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F, 0.65F);
			return true;
		}

		ArmorStand marker = new ArmorStand(world, nametagPos.get().getX() + 0.5D, nametagPos.get().getY(), nametagPos.get().getZ() + 0.5D);
		marker.setInvisible(true);
		marker.setNoGravity(true);
		marker.setSilent(true);
		marker.setInvulnerable(true);
		marker.setCustomName(Component.literal("Observer"));
		marker.setCustomNameVisible(true);
		marker.addTag("FakeworldFakeJoin");
		if (!world.addFreshEntity(marker)) {
			return true;
		}

		FAKE_JOIN_PRESENCES.add(new FakeJoinPresence(world.dimension(), marker.getUUID(), FAKE_JOIN_PRESENCE_DURATION_TICKS));
		playAttachedSound(target, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F, 0.65F);
		return true;
	}

	private static Optional<BlockPos> findFakeJoinNametagPos(ServerPlayer player, ServerLevel world) {
		BlockPos playerPos = player.blockPosition();
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = FAKE_JOIN_NAMETAG_MIN_DISTANCE + world.getRandom().nextDouble() * (FAKE_JOIN_NAMETAG_MAX_DISTANCE - FAKE_JOIN_NAMETAG_MIN_DISTANCE);
			int x = playerPos.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = playerPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
			int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			int startY = Math.min(playerPos.getY() - 4, surfaceY - 3);
			int minY = Math.max(world.getMinBuildHeight() + 1, startY - 26);
			for (int y = startY; y >= minY; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				if (isEmptyNametagSpace(world, pos)) {
					return Optional.of(pos);
				}
			}
		}

		Vec3 look = player.getLookAngle();
		Vec3 behind = new Vec3(-look.x, 0.0D, -look.z);
		if (behind.lengthSqr() < 0.01D) {
			behind = Vec3.directionFromRotation(0.0F, player.getYRot() + 180.0F);
		}
		behind = behind.normalize().scale(FAKE_JOIN_NAMETAG_MIN_DISTANCE);
		BlockPos fallback = BlockPos.containing(player.getX() + behind.x, player.getY(), player.getZ() + behind.z);
		return isEmptyNametagSpace(world, fallback) ? Optional.of(fallback) : Optional.empty();
	}

	private static boolean isEmptyNametagSpace(ServerLevel world, BlockPos pos) {
		return world.getBlockState(pos).isAir()
				&& world.getBlockState(pos.above()).isAir()
				&& world.getBlockState(pos).getFluidState().isEmpty()
				&& world.getBlockState(pos.above()).getFluidState().isEmpty();
	}

	private static ServerPlayer randomPhantomMiningCandidate(List<ServerPlayer> players, ServerLevel world) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (isUndergroundMiningTarget(player, world)) {
				candidates.add(player);
			}
		}

		return candidates.isEmpty() ? null : randomPlayer(candidates, world);
	}

	private static boolean isUndergroundMiningTarget(ServerPlayer player, ServerLevel world) {
		BlockPos pos = player.blockPosition();
		int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return surfaceY - pos.getY() >= PHANTOM_MINING_MIN_DEPTH
				&& world.getBlockState(pos).isAir()
				&& world.getBlockState(pos.above()).isAir();
	}

	private static boolean startPhantomMining(ServerPlayer player, ServerLevel world) {
		if (!isUndergroundMiningTarget(player, world)) {
			return false;
		}

		Optional<List<BlockPos>> path = findPhantomMiningPath(player, world);
		if (path.isEmpty()) {
			return false;
		}

		PHANTOM_MINING_EVENTS.add(new PhantomMiningEvent(world.dimension(), player.getUUID(), path.get()));
		playAttachedSound(player, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.45F, 0.55F);
		return true;
	}

	private static Optional<List<BlockPos>> findPhantomMiningPath(ServerPlayer player, ServerLevel world) {
		BlockPos playerPos = player.blockPosition();
		List<Direction> directions = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
		for (int i = directions.size() - 1; i > 0; i--) {
			Collections.swap(directions, i, world.getRandom().nextInt(i + 1));
		}
		for (Direction direction : directions) {
			for (int yOffset = -2; yOffset <= 2; yOffset++) {
				for (int wallDistance = 3; wallDistance <= 6; wallDistance++) {
					BlockPos end = playerPos.relative(direction, wallDistance).offset(0, yOffset, 0);
					BlockPos airBeforeEnd = end.relative(direction.getOpposite());
					if (!isEmptyNametagSpace(world, airBeforeEnd) || !isPhantomMineable(world, end)) {
						continue;
					}

					int length = PHANTOM_MINING_MIN_LENGTH + world.getRandom().nextInt(PHANTOM_MINING_MAX_LENGTH - PHANTOM_MINING_MIN_LENGTH + 1);
					List<BlockPos> path = phantomMiningPath(end, direction, length);
					if (isValidPhantomMiningPath(world, path)) {
						return Optional.of(path);
					}
				}
			}
		}

		return Optional.empty();
	}

	private static List<BlockPos> phantomMiningPath(BlockPos end, Direction direction, int length) {
		List<BlockPos> path = new ArrayList<>();
		for (int i = length - 1; i >= 0; i--) {
			path.add(end.relative(direction, i).immutable());
		}
		return path;
	}

	private static boolean isValidPhantomMiningPath(ServerLevel world, List<BlockPos> path) {
		for (BlockPos pos : path) {
			if (!isPhantomMineable(world, pos)) {
				return false;
			}
		}
		return true;
	}

	private static boolean breakPhantomMiningBlock(ServerLevel world, BlockPos pos) {
		if (!isPhantomMineable(world, pos)) {
			return false;
		}

		BlockState state = world.getBlockState(pos);
		world.levelEvent(null, 2001, pos, Block.getId(state));
		world.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.75F + world.getRandom().nextFloat() * 0.25F);
		world.destroyBlock(pos, true);

		// Break adjacent block for strip mine pattern (1x2 tunnel)
		BlockPos adjacentPos = pos.above();
		if (isPhantomMineable(world, adjacentPos)) {
			BlockState adjacentState = world.getBlockState(adjacentPos);
			world.levelEvent(null, 2001, adjacentPos, Block.getId(adjacentState));
			world.playSound(null, adjacentPos, adjacentState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.75F + world.getRandom().nextFloat() * 0.25F);
			world.destroyBlock(adjacentPos, true);
		}

		return true;
	}

	private static boolean isPhantomMineable(ServerLevel world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir()
				|| !state.getFluidState().isEmpty()
				|| state.getDestroySpeed(world, pos) < 0.0F
				|| world.getBlockEntity(pos) != null) {
			return false;
		}

		return state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(Blocks.DIRT)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.TUFF)
				|| state.is(Blocks.CALCITE)
				|| state.is(Blocks.GRANITE)
				|| state.is(Blocks.DIORITE)
				|| state.is(Blocks.ANDESITE)
				|| state.is(Blocks.SANDSTONE)
				|| state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.NETHERRACK)
				|| state.is(Blocks.BLACKSTONE)
				|| state.is(Blocks.BASALT);
	}

	private static ServerPlayer randomSkyObjectCandidate(List<ServerPlayer> players, ServerLevel world) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (canSeeSkyObject(player, world)) {
				candidates.add(player);
			}
		}

		return candidates.isEmpty() ? null : randomPlayer(candidates, world);
	}

	private static boolean canSeeSkyObject(ServerPlayer player, ServerLevel world) {
		BlockPos pos = player.blockPosition();
		int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return pos.getY() >= surfaceY - 5;
	}

	private static boolean startSkyObjectEvent(ServerPlayer player, ServerLevel world) {
		SkyObjectEvent event = new SkyObjectEvent(world.dimension(), player.getUUID(), skyObjectCenter(player, world), SKY_OBJECT_DURATION_TICKS);
		placeSkyObjectBlocks(world, event);
		SKY_OBJECT_EVENTS.add(event);
		playAttachedSound(player, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.6F, 0.35F);
		return true;
	}

	private static Vec3 skyObjectCenter(ServerPlayer player, ServerLevel world) {
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
		if (horizontal.lengthSqr() < 0.01D) {
			horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		horizontal = horizontal.normalize().scale(18.0D);

		BlockPos playerPos = player.blockPosition();
		int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ());
		double y = Mth.clamp(Math.max(player.getY() + 38.0D, surfaceY + 30.0D), world.getMinBuildHeight() + 32.0D, world.getMaxBuildHeight() - 18.0D);
		return new Vec3(player.getX() + horizontal.x, y, player.getZ() + horizontal.z);
	}

	private static void placeSkyObjectBlocks(ServerLevel world, SkyObjectEvent event) {
		BlockPos center = BlockPos.containing(event.center());
		int bottomY = center.getY() - SKY_OBJECT_ROOM_HEIGHT / 2;
		int topY = bottomY + SKY_OBJECT_ROOM_HEIGHT;
		for (int x = -SKY_OBJECT_ROOM_HALF_WIDTH; x <= SKY_OBJECT_ROOM_HALF_WIDTH; x++) {
			for (int z = -SKY_OBJECT_ROOM_HALF_DEPTH; z <= SKY_OBJECT_ROOM_HALF_DEPTH; z++) {
				boolean edge = Math.abs(x) == SKY_OBJECT_ROOM_HALF_WIDTH || Math.abs(z) == SKY_OBJECT_ROOM_HALF_DEPTH;
				boolean floorGrid = x % 4 == 0 || z % 4 == 0;
				if (edge || floorGrid) {
					placeSkyObjectBlock(world, event, center.offset(x, bottomY - center.getY(), z), Blocks.BLACK_CONCRETE.defaultBlockState());
					placeSkyObjectBlock(world, event, center.offset(x, topY - center.getY(), z), Blocks.GRAY_CONCRETE.defaultBlockState());
				}
				if (Math.abs(x) <= 4 && Math.abs(z) <= 4) {
					placeSkyObjectBlock(world, event, center.offset(x, topY - center.getY(), z), Blocks.SEA_LANTERN.defaultBlockState());
				}
			}
		}

		for (int y = bottomY; y <= topY; y++) {
			int yOffset = y - center.getY();
			for (int x = -SKY_OBJECT_ROOM_HALF_WIDTH; x <= SKY_OBJECT_ROOM_HALF_WIDTH; x++) {
				boolean frame = y == bottomY || y == topY || x % 4 == 0 || Math.abs(x) == SKY_OBJECT_ROOM_HALF_WIDTH;
				if (frame) {
					placeSkyObjectBlock(world, event, center.offset(x, yOffset, -SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.BLACK_CONCRETE.defaultBlockState());
					placeSkyObjectBlock(world, event, center.offset(x, yOffset, SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.BLACK_CONCRETE.defaultBlockState());
				}
			}
			for (int z = -SKY_OBJECT_ROOM_HALF_DEPTH; z <= SKY_OBJECT_ROOM_HALF_DEPTH; z++) {
				boolean frame = y == bottomY || y == topY || z % 4 == 0 || Math.abs(z) == SKY_OBJECT_ROOM_HALF_DEPTH;
				if (frame) {
					placeSkyObjectBlock(world, event, center.offset(-SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, z), Blocks.BLACK_CONCRETE.defaultBlockState());
					placeSkyObjectBlock(world, event, center.offset(SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, z), Blocks.BLACK_CONCRETE.defaultBlockState());
				}
			}
		}

		for (int y = bottomY + 2; y <= topY - 2; y++) {
			int yOffset = y - center.getY();
			for (int x = -4; x <= 4; x++) {
				placeSkyObjectBlock(world, event, center.offset(x, yOffset, -SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.TINTED_GLASS.defaultBlockState());
			}
			for (int z = -4; z <= 4; z++) {
				placeSkyObjectBlock(world, event, center.offset(-SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, z), Blocks.TINTED_GLASS.defaultBlockState());
				placeSkyObjectBlock(world, event, center.offset(SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, z), Blocks.TINTED_GLASS.defaultBlockState());
			}
		}

		for (int y = bottomY; y <= topY; y++) {
			int yOffset = y - center.getY();
			placeSkyObjectBlock(world, event, center.offset(-SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, -SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.OBSIDIAN.defaultBlockState());
			placeSkyObjectBlock(world, event, center.offset(SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, -SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.OBSIDIAN.defaultBlockState());
			placeSkyObjectBlock(world, event, center.offset(-SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.OBSIDIAN.defaultBlockState());
			placeSkyObjectBlock(world, event, center.offset(SKY_OBJECT_ROOM_HALF_WIDTH, yOffset, SKY_OBJECT_ROOM_HALF_DEPTH), Blocks.OBSIDIAN.defaultBlockState());
		}
	}

	private static void placeSkyObjectBlock(ServerLevel world, SkyObjectEvent event, BlockPos pos, BlockState state) {
		if (world.getBlockState(pos).isAir() && world.getBlockState(pos).getFluidState().isEmpty()) {
			world.setBlock(pos, state, Block.UPDATE_ALL);
			event.addBlock(pos.immutable());
		}
	}

	private static void removeSkyObjectBlocks(ServerLevel world, SkyObjectEvent event) {
		for (BlockPos pos : event.blocks()) {
			BlockState state = world.getBlockState(pos);
			if (state.is(Blocks.BLACK_CONCRETE)
					|| state.is(Blocks.GRAY_CONCRETE)
					|| state.is(Blocks.SEA_LANTERN)
					|| state.is(Blocks.TINTED_GLASS)
					|| state.is(Blocks.OBSIDIAN)) {
				world.removeBlock(pos, false);
			}
		}
		event.clearBlocks();
	}

	private static boolean isLookingAtSkyObject(ServerPlayer player, Vec3 center) {
		Vec3 toObject = center.subtract(player.getEyePosition());
		if (toObject.lengthSqr() < 1.0D) {
			return false;
		}

		return player.getLookAngle().normalize().dot(toObject.normalize()) >= SKY_OBJECT_LOOK_DOT;
	}

	private static void punishSkyObjectLook(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 8 * 20, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 10 * 20, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 20, 1, false, false, true));
		player.hurt(player.damageSources().magic(), SKY_OBJECT_DAMAGE);
		player.displayClientMessage(Component.literal("you were not meant to see the room"), true);
		playAttachedSound(player, TERROR_SOUND, SoundSource.HOSTILE, 0.9F, 0.7F);

		FakeworldSaveData saveData = getSaveData(player.server);
		saveData.recordDirectorEvent("sky_object_seen", 1);
		saveData.addDirectorPressure(SKY_OBJECT_DIRECTOR_PRESSURE_GAIN);
	}

	private static void stopSkyObjectSound(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEACON_AMBIENT), SoundSource.AMBIENT));
	}

	private static void sendDesktopNote(ServerPlayer player) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf("OBSERVER.txt");
		buf.writeUtf("""
				OBSERVATION CONTINUES

				Observer joined the game.

				do not answer the join message
				do not look for the name underground
				you were counted
				""");
		ServerPlayNetworking.send(player, DESKTOP_NOTE_PACKET, buf);
	}

	private static BlockPos currentFootstepPos(ServerPlayer player, ServerLevel world, int stepIndex, int sideSign) {
		Vec3 look = player.getLookAngle();
		Vec3 backward = new Vec3(-look.x, 0.0D, -look.z);
		if (backward.lengthSqr() < 0.01D) {
			backward = Vec3.directionFromRotation(0.0F, player.getYRot() + 180.0F);
		}
		backward = backward.normalize();
		Vec3 sideways = new Vec3(-backward.z, 0.0D, backward.x);
		double sideOffset = 0.55D * sideSign * (stepIndex % 2 == 0 ? 1.0D : -1.0D);
		double distance = FOOTSTEP_START_DISTANCE - stepIndex * 0.75D;
		double x = player.getX() + backward.x * distance + sideways.x * sideOffset;
		double z = player.getZ() + backward.z * distance + sideways.z * sideOffset;
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

		return new BlockPos(blockX, y, blockZ);
	}

	private static void playFootstep(ServerLevel world, BlockPos pos, int stepIndex) {
		BlockState floorState = world.getBlockState(pos.below());
		SoundType soundType = floorState.getSoundType();
		if (soundType == SoundType.EMPTY) {
			soundType = SoundType.GRASS;
		}

		float volume = Math.min(0.9F, soundType.getVolume() * 0.65F + 0.2F);
		float pitch = soundType.getPitch() * (0.82F + world.getRandom().nextFloat() * 0.12F);
		if (stepIndex == FOOTSTEP_COUNT - 1) {
			volume *= 0.7F;
		}

		world.playSound(null, pos, soundType.getStepSound(), SoundSource.PLAYERS, volume, pitch);
	}

	private static boolean hasAnimalAttentionCandidate(List<ServerPlayer> players, ServerLevel world) {
		for (ServerPlayer player : players) {
			if (!getNearbyAttentionAnimals(player, world).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static ServerPlayer randomAnimalAttentionCandidate(List<ServerPlayer> players, ServerLevel world) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!getNearbyAttentionAnimals(player, world).isEmpty()) {
				candidates.add(player);
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return randomPlayer(candidates, world);
	}

	private static ServerPlayer randomDoppelgangerCandidate(List<ServerPlayer> players, ServerLevel world) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!getLoadedChunkDoppelgangerAnimals(player, world).isEmpty()) {
				candidates.add(player);
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return randomPlayer(candidates, world);
	}

	private static ServerPlayer randomTameDogCandidate(List<ServerPlayer> players, ServerLevel world) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!hasActiveTameDogFor(player, world)) {
				candidates.add(player);
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return randomPlayer(candidates, world);
	}

	private static boolean startAnimalAttention(ServerPlayer player, ServerLevel world) {
		if (isAnimalStareEventCoolingDown(player)) {
			return false;
		}

		List<Animal> animals = getNearbyAttentionAnimals(player, world);
		if (animals.isEmpty()) {
			return false;
		}

		Collections.shuffle(animals);
		if (animals.size() > ANIMAL_ATTENTION_MAX_ANIMALS) {
			animals = new ArrayList<>(animals.subList(0, ANIMAL_ATTENTION_MAX_ANIMALS));
		}

		List<AnimalAttentionState> states = new ArrayList<>();
		for (Animal animal : animals) {
			states.add(new AnimalAttentionState(animal.getUUID(), animal.isSilent()));
			focusAnimalOnPlayer(animal, player);
		}

		ANIMAL_ATTENTION_EVENTS.add(new AnimalAttentionEvent(world.dimension(), player.getUUID(), states));
		return true;
	}

	private static boolean spawnAnimalDoppelganger(ServerPlayer player, ServerLevel world) {
		List<Animal> animals = getLoadedChunkDoppelgangerAnimals(player, world);
		if (animals.isEmpty()) {
			return false;
		}

		Animal original = animals.get(world.getRandom().nextInt(animals.size()));
		Entity copyEntity = original.getType().create(world);
		if (!(copyEntity instanceof Animal copy)) {
			return false;
		}

		BlockPos spawnPos = findDoppelgangerSpawnPos(player, world, original.blockPosition());
		copy.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, original.getYRot(), original.getXRot());
		copy.setPersistenceRequired();
		copy.setAge(original.getAge());
		if (original.hasCustomName()) {
			copy.setCustomName(original.getCustomName());
			copy.setCustomNameVisible(original.isCustomNameVisible());
		}

		if (!world.addFreshEntity(copy)) {
			return false;
		}

		DOPPELGANGER_ANIMALS.add(new DoppelgangerAnimal(world.dimension(), copy.getUUID(), player.getUUID()));
		return true;
	}

	private static boolean spawnTameDogFor(ServerPlayer player, ServerLevel world) {
		if (hasActiveTameDogFor(player, world)) {
			return false;
		}

		Wolf dog = EntityType.WOLF.create(world);
		if (dog == null) {
			return false;
		}

		dog.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		dog.setTame(true);
		dog.setOwnerUUID(player.getUUID());
		dog.setOrderedToSit(true);
		dog.setPersistenceRequired();
		dog.setCollarColor(DyeColor.RED);
		dog.addTag(TAME_DOG_TAG);
		dog.getNavigation().stop();

		return world.addFreshEntity(dog);
	}

	private static boolean hasActiveTameDogFor(ServerPlayer player, ServerLevel world) {
		AABB area = new AABB(player.blockPosition()).inflate(TAME_DOG_SCAN_RADIUS, 32.0D, TAME_DOG_SCAN_RADIUS);
		List<Wolf> dogs = world.getEntities(EntityTypeTest.forClass(Wolf.class), area, dog -> isFakeworldTameDog(dog) && player.getUUID().equals(dog.getOwnerUUID()));
		return !dogs.isEmpty();
	}

	private static BlockPos findDoppelgangerSpawnPos(ServerPlayer player, ServerLevel world, BlockPos originalPos) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = 5.0D + world.getRandom().nextDouble() * 6.0D;
			int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos candidate = new BlockPos(x, y, z);
			if (isValidAnimalStandPos(world, candidate)) {
				return candidate;
			}
		}

		return originalPos;
	}

	private static boolean isValidAnimalStandPos(ServerLevel world, BlockPos pos) {
		return world.getBlockState(pos.below()).blocksMotion()
				&& world.getBlockState(pos).isAir()
				&& world.getBlockState(pos.above()).isAir();
	}

	private static List<Animal> getNearbyAttentionAnimals(ServerPlayer player, ServerLevel world) {
		AABB area = new AABB(player.blockPosition()).inflate(ANIMAL_ATTENTION_RADIUS, 10.0D, ANIMAL_ATTENTION_RADIUS);
		return world.getEntities(EntityTypeTest.forClass(Animal.class), area,
				animal -> !animal.isRemoved() && animal.isAlive() && !isTrackedAttentionAnimal(animal.getUUID()));
	}

	private static boolean isTrackedAttentionAnimal(UUID animalUuid) {
		for (AnimalAttentionEvent event : ANIMAL_ATTENTION_EVENTS) {
			for (AnimalAttentionState state : event.animals()) {
				if (state.animalUuid().equals(animalUuid)) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<Animal> getLoadedChunkDoppelgangerAnimals(ServerPlayer player, ServerLevel world) {
		List<Animal> animals = new ArrayList<>();
		int playerChunkX = Mth.floor(player.getX()) >> 4;
		int playerChunkZ = Mth.floor(player.getZ()) >> 4;
		for (int chunkX = playerChunkX - DOPPELGANGER_LOADED_CHUNK_RADIUS; chunkX <= playerChunkX + DOPPELGANGER_LOADED_CHUNK_RADIUS; chunkX++) {
			for (int chunkZ = playerChunkZ - DOPPELGANGER_LOADED_CHUNK_RADIUS; chunkZ <= playerChunkZ + DOPPELGANGER_LOADED_CHUNK_RADIUS; chunkZ++) {
				if (!world.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				AABB chunkArea = new AABB(
						chunkX << 4, world.getMinBuildHeight(), chunkZ << 4,
						(chunkX << 4) + 16, world.getMaxBuildHeight(), (chunkZ << 4) + 16
				);
				animals.addAll(world.getEntities(EntityTypeTest.forClass(Animal.class), chunkArea,
						animal -> !animal.isRemoved() && animal.isAlive() && !isTrackedDoppelgangerAnimal(animal.getUUID())));
			}
		}

		return animals;
	}

	private static boolean isTrackedDoppelgangerAnimal(UUID animalUuid) {
		for (DoppelgangerAnimal doppelganger : DOPPELGANGER_ANIMALS) {
			if (doppelganger.animalUuid().equals(animalUuid)) {
				return true;
			}
		}
		return false;
	}

	private static void focusAnimalOnPlayer(Animal animal, ServerPlayer player) {
		if (animal.isPassenger()) {
			animal.stopRiding();
		}
		animal.setTarget(null);
		if (animal.distanceToSqr(player) > ANIMAL_ATTENTION_MIN_DISTANCE * ANIMAL_ATTENTION_MIN_DISTANCE) {
			if (!ANIMAL_STARE_PATH_COOLDOWNS.containsKey(animal.getUUID())) {
				animal.getNavigation().moveTo(player, ANIMAL_ATTENTION_SPEED);
				ANIMAL_STARE_PATH_COOLDOWNS.put(animal.getUUID(), ANIMAL_STARE_PATH_COOLDOWN_TICKS);
			}
		} else {
			animal.getNavigation().stop();
			ANIMAL_STARE_PATH_COOLDOWNS.remove(animal.getUUID());
		}
		animal.getLookControl().setLookAt(player, 30.0F, 30.0F);
		faceAnimalAtPlayer(animal, player);
	}

	private static void releaseAnimalAttention(ServerLevel world, AnimalAttentionEvent event) {
		if (world == null) {
			return;
		}

		for (AnimalAttentionState state : event.animals()) {
			Entity entity = world.getEntity(state.animalUuid());
			if (entity instanceof Animal animal) {
				animal.getNavigation().stop();
				animal.setSilent(state.wasSilent());
				animal.setTarget(null);
			}
		}
	}

	private static void faceAnimalAtPlayer(Animal animal, ServerPlayer player) {
		animal.lookAt(player, 30.0F, 30.0F);
		float yaw = (float) (Mth.atan2(player.getZ() - animal.getZ(), player.getX() - animal.getX()) * (180.0D / Math.PI)) - 90.0F;
		animal.setYRot(yaw);
		animal.yRotO = yaw;
		animal.setYHeadRot(yaw);
		animal.yHeadRotO = yaw;
		animal.setYBodyRot(yaw);
		animal.yBodyRotO = yaw;
	}

	private static void afterLivingEntityDeath(LivingEntity entity, DamageSource damageSource) {
		if (!(entity.level() instanceof ServerLevel world) || !world.dimension().equals(FAKE_OVERWORLD)) {
			return;
		}

		handleAnimalKillDirectorProgress(entity, damageSource, world);

		DoppelgangerAnimal doppelganger = removeDoppelganger(entity.getUUID());
		if (doppelganger == null) {
			return;
		}

		Entity attacker = damageSource.getEntity();
		ServerPlayer target = attacker instanceof ServerPlayer player ? player : world.getServer().getPlayerList().getPlayer(doppelganger.playerUuid());
		if (target == null || !target.level().dimension().equals(world.dimension())) {
			return;
		}

		BlockPos deathPos = entity.blockPosition();
		placeTreeBlood(world, deathPos);
		startPendingHunterSpawn(world, deathPos, target);
	}

	private static void handleAnimalKillDirectorProgress(LivingEntity entity, DamageSource damageSource, ServerLevel world) {
		if (!(entity instanceof Animal) || !(damageSource.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!player.level().dimension().equals(world.dimension()) || player.isCreative() || player.isSpectator()) {
			return;
		}

		FakeworldSaveData saveData = getSaveData(world.getServer());
		int killCount = saveData.getDirectorEventCount("animal_kill") + 1;
		int score = killCount % ANIMAL_KILL_SCORE_INTERVAL == 0 ? 1 : 0;
		saveData.recordDirectorEvent("animal_kill", score);
		saveData.addDirectorPressure(ANIMAL_KILL_DIRECTOR_PRESSURE_GAIN);
	}

	private static DoppelgangerAnimal removeDoppelganger(UUID uuid) {
		Iterator<DoppelgangerAnimal> iterator = DOPPELGANGER_ANIMALS.iterator();
		while (iterator.hasNext()) {
			DoppelgangerAnimal doppelganger = iterator.next();
			if (doppelganger.animalUuid().equals(uuid)) {
				iterator.remove();
				return doppelganger;
			}
		}

		return null;
	}

	private static void startPendingHunterSpawn(ServerLevel world, BlockPos pos, ServerPlayer target) {
		target.displayClientMessage(Component.literal("Your greed will cost you your life."), true);
		playAttachedSound(target, TERROR_SOUND, SoundSource.HOSTILE, 1.0F, 1.0F);
		PENDING_HUNTER_SPAWNS.add(new PendingHunterSpawn(world.dimension(), pos.immutable(), target.getUUID()));
	}

	private static void spawnHunterAt(ServerLevel world, BlockPos pos, ServerPlayer target) {
		HunterEntity hunter = new HunterEntity(HUNTER, world);
		hunter.setTargetPlayer(target);
		hunter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		world.addFreshEntity(hunter);
	}

	static void stopTerrorSound(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(new ResourceLocation(MOD_ID, "terror"), SoundSource.HOSTILE));
	}

	static void playTimedTerror(ServerPlayer player, int durationTicks) {
		if (durationTicks <= 0) {
			return;
		}
		playAttachedSound(player, TERROR_SOUND, SoundSource.HOSTILE, 0.9F, 1.0F);
		TIMED_TERROR_PLAYERS.put(player.getUUID(), durationTicks);
	}

	private static void tickTimedTerror(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> iterator = TIMED_TERROR_PLAYERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			int remaining = entry.getValue() - 1;
			if (remaining <= 0) {
				stopTerrorSound(player);
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	static void recordStalkerDisappearedNearPlayer(ServerPlayer player) {
		FakeworldSaveData saveData = getSaveData(player.server);
		if (saveData.hasStalkerJournalEntry(player.getUUID()) || saveData.hasPendingStalkerJournalUpdate(player.getUUID())) {
			return;
		}
		saveData.addPendingStalkerJournalUpdate(player.getUUID(), randomJournalUpdateDelay(player));
	}

	static void recordHunterEncounter(ServerPlayer player) {
		FakeworldSaveData saveData = getSaveData(player.server);
		if (!saveData.hasHunterJournalEntry(player.getUUID()) && !saveData.hasPendingHunterJournalUpdate(player.getUUID())) {
			saveData.addPendingHunterJournalUpdate(player.getUUID(), randomJournalUpdateDelay(player));
		}
	}

	private static void playAttachedSound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
		player.connection.send(new ClientboundSoundEntityPacket(Holder.direct(sound), source, player, volume, pitch, player.getRandom().nextLong()));
	}

	private static void notifyJournalChanged(ServerPlayer player) {
		player.displayClientMessage(Component.literal("The journal has changed."), true);
		playAttachedSound(player, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.75F, 0.8F);
	}

	private static boolean updatePlayerJournal(ServerPlayer player, FakeworldSaveData saveData) {
		boolean changed = false;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			ItemStack updated = updateJournal(player.getInventory().items.get(i), player.getUUID(), saveData);
			if (!updated.isEmpty()) {
				player.getInventory().items.set(i, updated);
				changed = true;
			}
		}
		for (int i = 0; i < player.getInventory().offhand.size(); i++) {
			ItemStack updated = updateJournal(player.getInventory().offhand.get(i), player.getUUID(), saveData);
			if (!updated.isEmpty()) {
				player.getInventory().offhand.set(i, updated);
				changed = true;
			}
		}

		if (changed) {
			player.getInventory().setChanged();
			player.inventoryMenu.broadcastChanges();
			player.containerMenu.broadcastChanges();
		}
		return changed;
	}

	private static ItemStack updateJournal(ItemStack stack, UUID playerUuid, FakeworldSaveData saveData) {
		if (!isJournal(stack)) {
			return ItemStack.EMPTY;
		}

		CompoundTag tag = stack.getOrCreateTag();
		boolean includeStalkerEntry = saveData.hasStalkerJournalEntry(playerUuid);
		boolean includeHunterEntry = saveData.hasHunterJournalEntry(playerUuid);
		if (tag.getBoolean(JOURNAL_STALKER_OBSERVATION_KEY) == includeStalkerEntry
				&& tag.getBoolean(JOURNAL_HUNTER_OBSERVATION_KEY) == includeHunterEntry) {
			return ItemStack.EMPTY;
		}

		return createJournal(includeStalkerEntry, includeHunterEntry);
	}

	private static ItemStack createJournal(boolean includeStalkerEntry, boolean includeHunterEntry) {
		ItemStack journal = new ItemStack(Items.WRITTEN_BOOK);
		CompoundTag journalTag = journal.getOrCreateTag();
		journalTag.putString("title", JOURNAL_TITLE);
		journalTag.putString("author", JOURNAL_AUTHOR);
		journalTag.putBoolean(JOURNAL_TAG, true);
		journalTag.putBoolean(JOURNAL_STALKER_OBSERVATION_KEY, includeStalkerEntry);
		journalTag.putBoolean(JOURNAL_HUNTER_OBSERVATION_KEY, includeHunterEntry);
		ListTag pages = new ListTag();
		if (includeStalkerEntry) {
			pages.add(journalPage(JOURNAL_STALKER_OBSERVATION));
		} else {
			pages.add(journalPage("Most pages are blank.\n\nThe rest look like they are waiting."));
		}
		if (includeHunterEntry) {
			pages.add(journalPage(JOURNAL_HUNTER_OBSERVATION));
		}
		journalTag.put("pages", pages);
		journal.setHoverName(Component.literal(JOURNAL_TITLE));
		return journal;
	}

	private static boolean isJournal(ItemStack stack) {
		if (!stack.is(Items.WRITTEN_BOOK) && !stack.is(Items.WRITABLE_BOOK) && !stack.is(Items.BOOK)) {
			return false;
		}

		CompoundTag tag = stack.getTag();
		if (tag != null && tag.getBoolean(JOURNAL_TAG)) {
			return true;
		}

		return stack.hasCustomHoverName() && JOURNAL_TITLE.equalsIgnoreCase(stack.getHoverName().getString());
	}

	private static ItemStack createJournal() {
		return createJournal(false, false);
	}

	private static void removeJournalBeforeDeath(ServerPlayer player, FakeworldSaveData saveData) {
		boolean removed = false;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			ItemStack stack = player.getInventory().items.get(i);
			if (isJournal(stack)) {
				recordJournalStateFromStack(player.getUUID(), stack, saveData);
				player.getInventory().items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < player.getInventory().offhand.size(); i++) {
			ItemStack stack = player.getInventory().offhand.get(i);
			if (isJournal(stack)) {
				recordJournalStateFromStack(player.getUUID(), stack, saveData);
				player.getInventory().offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}

		if (removed) {
			saveData.setRestoreJournalAfterDeath(player.getUUID());
			player.getInventory().setChanged();
			player.inventoryMenu.broadcastChanges();
			player.containerMenu.broadcastChanges();
		}
	}

	private static void recordJournalStateFromStack(UUID playerUuid, ItemStack stack, FakeworldSaveData saveData) {
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return;
		}
		if (tag.getBoolean(JOURNAL_STALKER_OBSERVATION_KEY)) {
			saveData.addStalkerJournalEntry(playerUuid);
		}
		if (tag.getBoolean(JOURNAL_HUNTER_OBSERVATION_KEY)) {
			saveData.addHunterJournalEntry(playerUuid);
		}
	}

	private static void restoreJournalAfterDeath(ServerPlayer player, FakeworldSaveData saveData) {
		if (!saveData.shouldRestoreJournalAfterDeath(player.getUUID())) {
			return;
		}

		saveData.clearRestoreJournalAfterDeath(player.getUUID());
		if (hasJournal(player)) {
			updatePlayerJournal(player, saveData);
			player.displayClientMessage(Component.literal("You coudn't let go of the book"), true);
			return;
		}

		ItemStack journal = createJournal(saveData.hasStalkerJournalEntry(player.getUUID()), saveData.hasHunterJournalEntry(player.getUUID()));
		if (!player.getInventory().add(journal)) {
			player.drop(journal, false);
		}
		player.displayClientMessage(Component.literal("You coudn't let go of the book"), true);
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges();
		player.containerMenu.broadcastChanges();
	}

	private static boolean hasJournal(ServerPlayer player) {
		for (ItemStack stack : player.getInventory().items) {
			if (isJournal(stack)) {
				return true;
			}
		}
		for (ItemStack stack : player.getInventory().offhand) {
			if (isJournal(stack)) {
				return true;
			}
		}
		return false;
	}

	private static StringTag journalPage(String text) {
		return StringTag.valueOf(Component.Serializer.toJson(Component.literal(text)));
	}

	private static boolean spawnJournalHouseNear(ServerPlayer player, ServerLevel world, FakeworldSaveData saveData) {
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = JOURNAL_HOUSE_MIN_DISTANCE + world.getRandom().nextDouble() * (JOURNAL_HOUSE_MAX_DISTANCE - JOURNAL_HOUSE_MIN_DISTANCE);
			int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (!isValidJournalHousePos(world, pos)) {
				continue;
			}

			if (placeJournalHouse(world, pos)) {
				saveData.setJournalHouse(world.dimension(), pos);
				return true;
			}
		}

		return false;
	}

	private static boolean spawnAmbientStructureNear(ServerPlayer player, ServerLevel world, FakeworldSaveData saveData, StructureSpawnDefinition structure) {
		Optional<StructureTemplate> template = world.getStructureManager().get(structure.id());
		if (template.isEmpty()) {
			return false;
		}

		Vec3i size = template.get().getSize();
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = AMBIENT_STRUCTURE_MIN_DISTANCE + world.getRandom().nextDouble() * (AMBIENT_STRUCTURE_MAX_DISTANCE - AMBIENT_STRUCTURE_MIN_DISTANCE);
			int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			SpawnedStructureBounds bounds = SpawnedStructureBounds.from(world.dimension(), structure.name(), pos, size);
			if (!isValidStructureSpawnPos(world, pos, size) || saveData.intersectsSpawnedStructure(bounds)) {
				continue;
			}

			if (placeStructureTemplate(world, pos, template.get())) {
				saveData.addSpawnedStructure(bounds);
				return true;
			}
		}

		return false;
	}

	private static boolean isValidJournalHousePos(ServerLevel world, BlockPos pos) {
		return isValidStructureSpawnPos(world, pos, new Vec3i(8, 6, 8));
	}

	private static boolean isValidStructureSpawnPos(ServerLevel world, BlockPos pos, Vec3i size) {
		int xStep = Math.max(1, size.getX() / 4);
		int zStep = Math.max(1, size.getZ() / 4);
		for (int x = 0; x < size.getX(); x += xStep) {
			for (int z = 0; z < size.getZ(); z += zStep) {
				BlockPos surface = pos.offset(x, 0, z);
				int height = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, surface.getX(), surface.getZ());
				if (height != pos.getY()) {
					return false;
				}
				if (!hasSolidGroundAtPlacement(world, surface)) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean hasSolidGroundAtPlacement(ServerLevel world, BlockPos placementPos) {
		BlockState ground = world.getBlockState(placementPos.below());
		return ground.blocksMotion()
				&& ground.getFluidState().isEmpty()
				&& world.getBlockState(placementPos).getFluidState().isEmpty();
	}

	private static boolean placeJournalHouse(ServerLevel world, BlockPos pos) {
		Optional<StructureTemplate> template = world.getStructureManager().get(JOURNAL_HOUSE_STRUCTURE);
		if (template.isPresent()) {
			return placeStructureTemplate(world, pos, template.get());
		}

		placeFallbackJournalHouse(world, pos);
		return true;
	}

	private static boolean placeStructureTemplate(ServerLevel world, BlockPos pos, StructureTemplate template) {
		StructurePlaceSettings settings = defaultStructurePlaceSettings();
		return template.placeInWorld(world, pos, pos, settings, world.getRandom(), Block.UPDATE_ALL);
	}

	private static void handleSpawnedStructureBreak(ServerPlayer player, ServerLevel world, BlockPos pos, FakeworldSaveData saveData) {
		Optional<SpawnedStructureBounds> damagedStructure = saveData.spawnedStructureAt(world.dimension(), pos);
		if (damagedStructure.isEmpty()) {
			return;
		}

		queueStructureRepair(world, damagedStructure.get());
		saveData.addDirectorPressure(FAILED_EVENT_DIRECTOR_PRESSURE_GAIN);
		player.displayClientMessage(Component.literal("This place remembers."), true);
	}

	private static void queueStructureRepair(ServerLevel world, SpawnedStructureBounds structure) {
		for (PendingStructureRepair repair : PENDING_STRUCTURE_REPAIRS) {
			if (repair.structure().equals(structure)) {
				repair.resetStartDelay();
				return;
			}
		}

		ResourceLocation id = ambientStructureId(structure.name());
		if (id == null) {
			return;
		}

		Optional<StructureTemplate> template = world.getStructureManager().get(id);
		if (template.isEmpty()) {
			return;
		}

		List<StructureBlockInfo> blocks = repairBlocksFromTemplate(structure.min(), template.get());
		if (!blocks.isEmpty()) {
			Collections.shuffle(blocks);
			PENDING_STRUCTURE_REPAIRS.add(new PendingStructureRepair(structure, blocks));
		}
	}

	private static List<StructureBlockInfo> repairBlocksFromTemplate(BlockPos origin, StructureTemplate template) {
		List<Palette> palettes = ((StructureTemplateAccessor) template).fakeworld$getPalettes();
		if (palettes.isEmpty()) {
			return Collections.emptyList();
		}

		List<StructureBlockInfo> blocks = new ArrayList<>();
		for (StructureBlockInfo blockInfo : palettes.get(0).blocks()) {
			BlockPos placedPos = origin.offset(blockInfo.pos());
			blocks.add(new StructureBlockInfo(placedPos, blockInfo.state(), blockInfo.nbt()));
		}
		return blocks;
	}

	private static StructurePlaceSettings defaultStructurePlaceSettings() {
		return new StructurePlaceSettings()
				.setMirror(Mirror.NONE)
				.setRotation(Rotation.NONE)
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
	}

	private static ResourceLocation ambientStructureId(String name) {
		for (StructureSpawnDefinition structure : AMBIENT_STRUCTURES) {
			if (structure.name().equals(name)) {
				return structure.id();
			}
		}
		return null;
	}

	private static boolean hasPlayerNearStructureRepair(ServerLevel world, PendingStructureRepair repair) {
		BlockPos center = repair.structure().center();
		double maxDistanceSqr = STRUCTURE_REPAIR_PLAYER_RADIUS * STRUCTURE_REPAIR_PLAYER_RADIUS;
		for (ServerPlayer player : world.players()) {
			if (!player.isSpectator() && player.blockPosition().distSqr(center) <= maxDistanceSqr) {
				return true;
			}
		}
		return false;
	}

	private static boolean repairNextChangedStructureBlock(ServerLevel world, PendingStructureRepair repair) {
		for (int checked = 0; checked < repair.blocks().size(); checked++) {
			StructureBlockInfo blockInfo = repair.nextBlock();
			if (blockInfo == null) {
				return false;
			}

			BlockPos pos = blockInfo.pos();
			BlockState targetState = blockInfo.state();
			if (targetState.isAir() || world.getBlockState(pos).equals(targetState)) {
				continue;
			}

			world.setBlock(pos, targetState, Block.UPDATE_ALL);
			if (blockInfo.nbt() != null) {
				BlockEntity blockEntity = world.getBlockEntity(pos);
				if (blockEntity != null) {
					blockEntity.load(blockInfo.nbt());
					blockEntity.setChanged();
				}
			}
			world.playSound(null, pos, targetState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.35F, 0.75F + world.getRandom().nextFloat() * 0.2F);
			return true;
		}

		return false;
	}

	public static int hunterAggressionFor(ServerPlayer player) {
		return Mth.clamp(getSaveData(player.server).getDirectorPressure() / 5, 0, 20);
	}

	private static void placeFallbackJournalHouse(ServerLevel world, BlockPos pos) {
		BlockPos origin = pos.offset(-3, 0, -3);
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				world.setBlock(origin.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 0; y <= 4; y++) {
					BlockPos blockPos = origin.offset(x, y, z);
					boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
					if (y == 0) {
						world.setBlock(blockPos, Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
					} else if (wall) {
						world.setBlock(blockPos, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_ALL);
					} else {
						world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
				world.setBlock(origin.offset(x, 5, z), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}

		BlockPos doorLower = origin.offset(3, 1, 6);
		world.setBlock(doorLower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		world.setBlock(doorLower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		world.setBlock(doorLower, Blocks.SPRUCE_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
		world.setBlock(doorLower.above(), Blocks.SPRUCE_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);

		world.setBlock(origin.offset(1, 1, 3), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos chestPos = origin.offset(3, 1, 2);
		world.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.SOUTH)
				.setValue(ChestBlock.TYPE, ChestType.SINGLE), Block.UPDATE_ALL);
		if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
			chest.setItem(13, createJournal());
			chest.setChanged();
		}
	}

	private static boolean hasActiveStalker(ServerLevel world) {
		for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
			if (entity instanceof StalkerEntity) {
				return true;
			}
		}

		return false;
	}

	private static boolean spawnStalkerFor(ServerPlayer player, ServerLevel world) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = STALKER_SPAWN_MIN_DISTANCE + world.getRandom().nextDouble() * (STALKER_SPAWN_MAX_DISTANCE - STALKER_SPAWN_MIN_DISTANCE);
			int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos spawnPos = new BlockPos(x, y, z);
			if (!isValidStalkerSpawn(world, spawnPos)) {
				continue;
			}

			StalkerEntity stalker = new StalkerEntity(STALKER, world);
			stalker.setTargetPlayer(player);
			stalker.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
			stalker.faceTargetNow();
			boolean spawned = world.addFreshEntity(stalker);
			if (spawned) {
				placeStalkerSign(player, world, spawnPos);
				playAttachedSound(player, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 0.8F);
			}
			return spawned;
		}

		return false;
	}

	private static boolean hasActiveMimicVillager(ServerLevel world) {
		List<MimicVillagerEntity> mimics = world.getEntities(EntityTypeTest.forClass(MimicVillagerEntity.class), new AABB(
				-30000000.0D, world.getMinBuildHeight(), -30000000.0D,
				30000000.0D, world.getMaxBuildHeight(), 30000000.0D
		), entity -> true);
		return !mimics.isEmpty();
	}

	private static boolean spawnMimicVillagerFor(ServerPlayer player, ServerLevel world) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return false;
		}

		for (int attempt = 0; attempt < 18; attempt++) {
			double angle = Math.toRadians(player.getYRot() + 180.0F) + (world.getRandom().nextDouble() - 0.5D) * Math.PI;
			double distance = 10.0D + world.getRandom().nextDouble() * 14.0D;
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			int blockX = Mth.floor(x);
			int blockZ = Mth.floor(z);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
			BlockPos spawnPos = new BlockPos(blockX, y, blockZ);
			if (!isValidStalkerSpawn(world, spawnPos)) {
				continue;
			}

			MimicVillagerEntity mimic = new MimicVillagerEntity(MIMIC_VILLAGER, world);
			mimic.setTargetPlayer(player);
			mimic.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
			boolean spawned = world.addFreshEntity(mimic);
			if (spawned) {
				playAttachedSound(player, SoundEvents.VILLAGER_AMBIENT, SoundSource.AMBIENT, 0.7F, 0.6F);
				return true;
			}
		}
		return false;
	}

	private static boolean isValidStalkerSpawn(ServerLevel world, BlockPos spawnPos) {
		return world.getBlockState(spawnPos.below()).blocksMotion()
				&& world.getBlockState(spawnPos).isAir()
				&& world.getBlockState(spawnPos.above()).isAir();
	}

	private static void placeStalkerSign(ServerPlayer player, ServerLevel world, BlockPos stalkerPos) {
		Direction directionToPlayer = Direction.getNearest(player.getX() - (stalkerPos.getX() + 0.5D), 0.0D, player.getZ() - (stalkerPos.getZ() + 0.5D));
		for (int distance = 2; distance <= 5; distance++) {
			BlockPos candidate = stalkerPos.relative(directionToPlayer, distance);
			BlockPos signPos = candidate.atY(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ()));
			if (!canPlaceStalkerSign(world, signPos)) {
				continue;
			}

			BlockState signState = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, signRotationFacingPlayer(player, signPos));
			world.setBlock(signPos, signState, Block.UPDATE_ALL);
			if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
				sign.setText(randomStalkerSignText(world), true);
				sign.setWaxed(true);
				world.sendBlockUpdated(signPos, signState, signState, Block.UPDATE_ALL);
			}
			return;
		}
	}

	private static boolean canPlaceStalkerSign(ServerLevel world, BlockPos signPos) {
		return world.getBlockState(signPos.below()).blocksMotion()
				&& world.getBlockState(signPos).isAir()
				&& world.getBlockState(signPos.above()).isAir();
	}

	private static int signRotationFacingPlayer(ServerPlayer player, BlockPos signPos) {
		double dx = player.getX() - (signPos.getX() + 0.5D);
		double dz = player.getZ() - (signPos.getZ() + 0.5D);
		double yaw = Mth.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D;
		return Mth.floor((yaw + 180.0D) * 16.0D / 360.0D + 0.5D) & 15;
	}

	private static SignText randomStalkerSignText(ServerLevel world) {
		String message = STALKER_SIGN_MESSAGES[world.getRandom().nextInt(STALKER_SIGN_MESSAGES.length)];
		SignText text = new SignText().setColor(DyeColor.RED);
		String[] lines = stalkerSignLines(message);
		for (int i = 0; i < lines.length; i++) {
			text = text.setMessage(i, Component.literal(lines[i]));
		}
		return text;
	}

	private static String[] stalkerSignLines(String message) {
		return switch (message) {
			case "WE STAYED FOREVER" -> new String[]{"WE STAYED", "FOREVER"};
			case "WHY DID YOU LEAVE US?" -> new String[]{"WHY DID YOU", "LEAVE US?"};
			default -> new String[]{message};
		};
	}

	private static boolean placeAbandonedHomeSignOnPlayer(ServerPlayer player, ServerLevel world) {
		BlockPos signPos = abandonedHomeSignPos(player, world);
		if (signPos == null) {
			return false;
		}

		BlockState signState = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, signRotationFacingPlayer(player, signPos));
		world.setBlock(signPos, signState, Block.UPDATE_ALL);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			sign.setText(abandonedHomeSignText(), true);
			sign.setWaxed(true);
			world.sendBlockUpdated(signPos, signState, signState, Block.UPDATE_ALL);
		}
		return true;
	}

	private static BlockPos abandonedHomeSignPos(ServerPlayer player, ServerLevel world) {
		BlockPos playerPos = player.blockPosition();
		for (BlockPos candidate : new BlockPos[]{playerPos, playerPos.below(), playerPos.above()}) {
			if (canPlaceStalkerSign(world, candidate)) {
				return candidate.immutable();
			}
		}

		BlockPos surface = playerPos.atY(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ()));
		return canPlaceStalkerSign(world, surface) ? surface.immutable() : null;
	}

	private static SignText abandonedHomeSignText() {
		SignText text = new SignText().setColor(DyeColor.RED);
		String[] lines = {"YOU BUILT", "THEM A HOME", "JUST TO", "LEAVE THEM"};
		for (int i = 0; i < lines.length; i++) {
			text = text.setMessage(i, Component.literal(lines[i]));
		}
		return text;
	}

	private static void showBelongingMessage(ServerPlayer player, FakeworldSaveData saveData) {
		player.displayClientMessage(Component.literal("I feel like I don't belong here"), true);
		playAttachedSound(player, net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.85F, 1.0F);
		saveData.setBelongingMessageShown();
	}

	static FakeworldSaveData getSaveData(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FakeworldSaveData::load, FakeworldSaveData::new, MOD_ID);
	}

	private static void removeBloodSplatter(Iterable<ServerLevel> levels, ScheduledBloodSplatter splatter) {
		for (ServerLevel level : levels) {
			if (!level.dimension().equals(splatter.dimension())) {
				continue;
			}

			if (level.getBlockState(splatter.pos()).is(BLOOD_SPLATTER)) {
				level.removeBlock(splatter.pos(), false);
			}
			return;
		}
	}

	private record ScheduledBloodSplatter(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static class ScheduledFootsteps {
		private final ResourceKey<Level> dimension;
		private final UUID playerUuid;
		private final int sideSign;
		private int stepIndex;
		private int delayTicks;

		ScheduledFootsteps(ResourceKey<Level> dimension, UUID playerUuid, int sideSign) {
			this.dimension = dimension;
			this.playerUuid = playerUuid;
			this.sideSign = sideSign;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		int sideSign() {
			return this.sideSign;
		}

		int stepIndex() {
			return this.stepIndex;
		}

		int delayTicks() {
			return this.delayTicks;
		}

		void setDelayTicks(int delayTicks) {
			this.delayTicks = delayTicks;
		}

		void advance() {
			this.stepIndex++;
			this.delayTicks = FOOTSTEP_INTERVAL_TICKS;
		}
	}

	private static class FakeJoinPresence {
		private final ResourceKey<Level> dimension;
		private final UUID entityUuid;
		private int ticksRemaining;

		FakeJoinPresence(ResourceKey<Level> dimension, UUID entityUuid, int ticksRemaining) {
			this.dimension = dimension;
			this.entityUuid = entityUuid;
			this.ticksRemaining = ticksRemaining;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID entityUuid() {
			return this.entityUuid;
		}

		int ticksRemaining() {
			return this.ticksRemaining;
		}

		void tick() {
			this.ticksRemaining--;
		}
	}

	private static class PhantomMiningEvent {
		private final ResourceKey<Level> dimension;
		private final UUID playerUuid;
		private final List<BlockPos> path;
		private int nextIndex;
		private int delayTicks = PHANTOM_MINING_INTERVAL_TICKS;

		PhantomMiningEvent(ResourceKey<Level> dimension, UUID playerUuid, List<BlockPos> path) {
			this.dimension = dimension;
			this.playerUuid = playerUuid;
			this.path = path;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		List<BlockPos> path() {
			return this.path;
		}

		int nextIndex() {
			return this.nextIndex;
		}

		int delayTicks() {
			return this.delayTicks;
		}

		void setDelayTicks(int delayTicks) {
			this.delayTicks = delayTicks;
		}

		void advance() {
			this.nextIndex++;
			this.delayTicks = PHANTOM_MINING_INTERVAL_TICKS;
		}
	}

	private static class SkyObjectEvent {
		private final ResourceKey<Level> dimension;
		private final UUID playerUuid;
		private final Vec3 center;
		private final List<BlockPos> blocks = new ArrayList<>();
		private int ticksRemaining;
		private boolean punishedLook;

		SkyObjectEvent(ResourceKey<Level> dimension, UUID playerUuid, Vec3 center, int ticksRemaining) {
			this.dimension = dimension;
			this.playerUuid = playerUuid;
			this.center = center;
			this.ticksRemaining = ticksRemaining;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		Vec3 center() {
			return this.center;
		}

		List<BlockPos> blocks() {
			return this.blocks;
		}

		void addBlock(BlockPos pos) {
			this.blocks.add(pos);
		}

		void clearBlocks() {
			this.blocks.clear();
		}

		int ticksRemaining() {
			return this.ticksRemaining;
		}

		boolean hasPunishedLook() {
			return this.punishedLook;
		}

		void setPunishedLook() {
			this.punishedLook = true;
		}

		void tick() {
			this.ticksRemaining--;
		}
	}

	private static class AnimalAttentionEvent {
		private final ResourceKey<Level> dimension;
		private final UUID playerUuid;
		private final List<AnimalAttentionState> animals;

		AnimalAttentionEvent(ResourceKey<Level> dimension, UUID playerUuid, List<AnimalAttentionState> animals) {
			this.dimension = dimension;
			this.playerUuid = playerUuid;
			this.animals = animals;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		List<AnimalAttentionState> animals() {
			return this.animals;
		}
	}

	private record AnimalAttentionState(UUID animalUuid, boolean wasSilent) {
	}

	private static class DoppelgangerAnimal {
		private final ResourceKey<Level> dimension;
		private final UUID animalUuid;
		private final UUID playerUuid;
		private int remainingTicks = DOPPELGANGER_DURATION_TICKS;

		DoppelgangerAnimal(ResourceKey<Level> dimension, UUID animalUuid, UUID playerUuid) {
			this.dimension = dimension;
			this.animalUuid = animalUuid;
			this.playerUuid = playerUuid;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		UUID animalUuid() {
			return this.animalUuid;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		int remainingTicks() {
			return this.remainingTicks;
		}

		void decrementDuration() {
			this.remainingTicks--;
		}
	}

	private static class PendingHunterSpawn {
		private final ResourceKey<Level> dimension;
		private final BlockPos pos;
		private final UUID playerUuid;
		private int delayTicks = HUNTER_WARNING_TICKS;

		PendingHunterSpawn(ResourceKey<Level> dimension, BlockPos pos, UUID playerUuid) {
			this.dimension = dimension;
			this.pos = pos;
			this.playerUuid = playerUuid;
		}

		ResourceKey<Level> dimension() {
			return this.dimension;
		}

		BlockPos pos() {
			return this.pos;
		}

		UUID playerUuid() {
			return this.playerUuid;
		}

		int delayTicks() {
			return this.delayTicks;
		}

		void decrementDelay() {
			this.delayTicks--;
		}
	}

	private static class PendingStructureRepair {
		private final SpawnedStructureBounds structure;
		private final List<StructureBlockInfo> blocks;
		private int blockIndex;
		private int startDelayTicks = STRUCTURE_REPAIR_START_DELAY_TICKS;
		private int delayTicks = STRUCTURE_REPAIR_INTERVAL_TICKS;

		PendingStructureRepair(SpawnedStructureBounds structure, List<StructureBlockInfo> blocks) {
			this.structure = structure;
			this.blocks = blocks;
		}

		ResourceKey<Level> dimension() {
			return this.structure.dimension();
		}

		SpawnedStructureBounds structure() {
			return this.structure;
		}

		List<StructureBlockInfo> blocks() {
			return this.blocks;
		}

		int startDelayTicks() {
			return this.startDelayTicks;
		}

		void decrementStartDelay() {
			this.startDelayTicks--;
		}

		void resetStartDelay() {
			this.startDelayTicks = STRUCTURE_REPAIR_START_DELAY_TICKS;
		}

		int delayTicks() {
			return this.delayTicks;
		}

		void setDelayTicks(int delayTicks) {
			this.delayTicks = delayTicks;
		}

		void decrementDelay() {
			this.delayTicks--;
		}

		StructureBlockInfo nextBlock() {
			if (this.blocks.isEmpty()) {
				return null;
			}

			StructureBlockInfo block = this.blocks.get(this.blockIndex);
			this.blockIndex = (this.blockIndex + 1) % this.blocks.size();
			return block;
		}
	}

	private record AmbientEventCandidate(String eventKey, int weight, int cooldownTicks, int directorScore, AmbientEventAction action) {
	}

	private enum DirectorMood {
		QUIET(140, 220),
		WATCHING(105, 100),
		STALKING(80, 55),
		PUNISHING(70, 35),
		AFTERMATH(155, 260);

		private final int delayPercent;
		private final int quietWeightPercent;

		DirectorMood(int delayPercent, int quietWeightPercent) {
			this.delayPercent = delayPercent;
			this.quietWeightPercent = quietWeightPercent;
		}

		int delayPercent() {
			return this.delayPercent;
		}

		int quietWeightPercent() {
			return this.quietWeightPercent;
		}

		int weightPercent(String eventKey) {
			if (eventKey.equals("creepy_village")) {
				return switch (this) {
					case QUIET -> 0;
					case WATCHING -> 5;
					case STALKING -> 20;
					case PUNISHING -> 180;
					case AFTERMATH -> 120;
				};
			}

			return switch (this) {
				case QUIET -> quietWeightPercentForEvent(eventKey, 70, 70, 55, 65, 45, 80, 85, 35, 45, 35, 25, 60);
				case WATCHING -> quietWeightPercentForEvent(eventKey, 120, 105, 115, 115, 95, 160, 130, 150, 145, 75, 50, 110);
				case STALKING -> quietWeightPercentForEvent(eventKey, 75, 60, 95, 170, 135, 175, 165, 95, 110, 115, 90, 85);
				case PUNISHING -> quietWeightPercentForEvent(eventKey, 45, 35, 45, 120, 140, 65, 155, 80, 80, 175, 185, 40);
				case AFTERMATH -> quietWeightPercentForEvent(eventKey, 55, 45, 70, 40, 35, 95, 55, 120, 55, 40, 25, 70);
			};
		}

		private static int quietWeightPercentForEvent(String eventKey, int belonging, int journalHouse, int structure, int stalker, int darkness, int footsteps,
				int phantomMining, int skyObject, int animalAttention, int animalDoppelganger, int mimicVillager, int inventoryShuffle) {
			if (eventKey.equals("belonging")) {
				return belonging;
			}
			if (eventKey.equals("journal_house")) {
				return journalHouse;
			}
			if (eventKey.startsWith("structure_")) {
				return structure;
			}
			if (eventKey.equals("stalker")) {
				return stalker;
			}
			if (eventKey.equals("darkness")) {
				return darkness;
			}
			if (eventKey.equals("footsteps")) {
				return footsteps;
			}
			if (eventKey.equals("phantom_mining")) {
				return phantomMining;
			}
			if (eventKey.equals("sky_object")) {
				return skyObject;
			}
			if (eventKey.equals("animal_attention")) {
				return animalAttention;
			}
			if (eventKey.equals("animal_doppelganger")) {
				return animalDoppelganger;
			}
			if (eventKey.equals("mimic_villager")) {
				return mimicVillager;
			}
			if (eventKey.equals("inventory_shuffle")) {
				return inventoryShuffle;
			}
			return 100;
		}

		static DirectorMood fromName(String name) {
			for (DirectorMood mood : values()) {
				if (mood.name().equals(name)) {
					return mood;
				}
			}
			return QUIET;
		}
	}

	private record StructureSpawnDefinition(String name, ResourceLocation id) {
		int chance() {
			return switch (name) {
				case "castle" -> CONFIG.castleStructureWeight;
				case "campsite" -> CONFIG.campsiteStructureWeight;
				case "empty" -> CONFIG.emptyStructureWeight;
				case "tower" -> CONFIG.towerStructureWeight;
				default -> 0;
			};
		}

		int cooldownTicks() {
			return switch (name) {
				case "castle" -> Fakeworld.cooldownTicks(CONFIG.castleStructureCooldownMinutes);
				case "campsite" -> Fakeworld.cooldownTicks(CONFIG.campsiteStructureCooldownMinutes);
				case "empty" -> Fakeworld.cooldownTicks(CONFIG.emptyStructureCooldownMinutes);
				case "tower" -> Fakeworld.cooldownTicks(CONFIG.towerStructureCooldownMinutes);
				default -> 0;
			};
		}
	}

	private record SpawnedStructureBounds(ResourceKey<Level> dimension, String name, BlockPos min, BlockPos max) {
		static SpawnedStructureBounds from(ResourceKey<Level> dimension, String name, BlockPos origin, Vec3i size) {
			BlockPos max = origin.offset(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1));
			return new SpawnedStructureBounds(dimension, name, origin.immutable(), max.immutable());
		}

		boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
			return this.dimension.equals(dimension)
					&& pos.getX() >= this.min.getX()
					&& pos.getY() >= this.min.getY()
					&& pos.getZ() >= this.min.getZ()
					&& pos.getX() <= this.max.getX()
					&& pos.getY() <= this.max.getY()
					&& pos.getZ() <= this.max.getZ();
		}

		boolean intersects(SpawnedStructureBounds other) {
			return this.dimension.equals(other.dimension)
					&& this.min.getX() <= other.max.getX()
					&& this.max.getX() >= other.min.getX()
					&& this.min.getY() <= other.max.getY()
					&& this.max.getY() >= other.min.getY()
					&& this.min.getZ() <= other.max.getZ()
					&& this.max.getZ() >= other.min.getZ();
		}

		BlockPos center() {
			return new BlockPos(
					(this.min.getX() + this.max.getX()) / 2,
					(this.min.getY() + this.max.getY()) / 2,
					(this.min.getZ() + this.max.getZ()) / 2
			);
		}
	}

	public static class FakeworldSaveData extends SavedData {
		private static final String BELONGING_MESSAGE_SHOWN_KEY = "BelongingMessageShown";
		private static final String AMBIENT_EVENT_DELAY_TICKS_KEY = "AmbientEventDelayTicks";
		private static final String AMBIENT_EVENT_DELAY_INITIALIZED_KEY = "AmbientEventDelayInitialized";
		private static final String AMBIENT_EVENT_COOLDOWNS_KEY = "AmbientEventCooldowns";
		private static final String LAST_AMBIENT_EVENT_KEY = "LastAmbientEvent";
		private static final String AMBIENT_EVENT_KEY = "Event";
		private static final String AMBIENT_EVENT_COOLDOWN_TICKS_KEY = "Ticks";
		private static final String PENDING_STALKER_JOURNAL_UPDATES_KEY = "PendingStalkerJournalUpdates";
		private static final String PENDING_HUNTER_JOURNAL_UPDATES_KEY = "PendingHunterJournalUpdates";
		private static final String STALKER_JOURNAL_DELAYS_KEY = "StalkerJournalDelays";
		private static final String HUNTER_JOURNAL_DELAYS_KEY = "HunterJournalDelays";
		private static final String JOURNAL_DELAY_PLAYER_KEY = "Player";
		private static final String JOURNAL_DELAY_TICKS_KEY = "Ticks";
		private static final String STALKER_JOURNAL_PLAYERS_KEY = "StalkerJournalPlayers";
		private static final String HUNTER_JOURNAL_PLAYERS_KEY = "HunterJournalPlayers";
		private static final String RESTORE_JOURNAL_AFTER_DEATH_PLAYERS_KEY = "RestoreJournalAfterDeathPlayers";
		private static final String JOURNAL_HOUSE_KEY = "JournalHouse";
		private static final String JOURNAL_HOUSE_DIMENSION_KEY = "Dimension";
		private static final String JOURNAL_HOUSE_X_KEY = "X";
		private static final String JOURNAL_HOUSE_Y_KEY = "Y";
		private static final String JOURNAL_HOUSE_Z_KEY = "Z";
		private static final String JOURNAL_HOUSE_MESSAGE_PLAYERS_KEY = "JournalHouseMessagePlayers";
		private static final String SPAWNED_STRUCTURES_KEY = "SpawnedStructures";
		private static final String STRUCTURE_NAME_KEY = "Name";
		private static final String STRUCTURE_DIMENSION_KEY = "Dimension";
		private static final String STRUCTURE_MIN_X_KEY = "MinX";
		private static final String STRUCTURE_MIN_Y_KEY = "MinY";
		private static final String STRUCTURE_MIN_Z_KEY = "MinZ";
		private static final String STRUCTURE_MAX_X_KEY = "MaxX";
		private static final String STRUCTURE_MAX_Y_KEY = "MaxY";
		private static final String STRUCTURE_MAX_Z_KEY = "MaxZ";
		private static final String DIRECTOR_PHASE_KEY = "DirectorPhase";
		private static final String DIRECTOR_SCORE_KEY = "DirectorScore";
		private static final String DIRECTOR_MOOD_KEY = "DirectorMood";
		private static final String DIRECTOR_MOOD_TICKS_KEY = "DirectorMoodTicks";
		private static final String DIRECTOR_PRESSURE_KEY = "DirectorPressure";
		private static final String DIRECTOR_EVENT_COUNTS_KEY = "DirectorEventCounts";
		private static final String DIRECTOR_EVENT_COUNT_EVENT_KEY = "Event";
		private static final String DIRECTOR_EVENT_COUNT_VALUE_KEY = "Count";
		private boolean belongingMessageShown;
		private int ambientEventDelayTicks;
		private boolean ambientEventDelayInitialized;
		private String lastAmbientEventKey = "";
		private int directorPhase;
		private int directorScore;
		private DirectorMood directorMood = DirectorMood.QUIET;
		private int directorMoodTicks = DIRECTOR_MOOD_DEFAULT_TICKS;
		private int directorPressure;
		private final Map<String, Integer> ambientEventCooldowns = new HashMap<>();
		private final Map<String, Integer> directorEventCounts = new HashMap<>();
		private final Set<UUID> pendingStalkerJournalUpdates = new HashSet<>();
		private final Set<UUID> pendingHunterJournalUpdates = new HashSet<>();
		private final Map<UUID, Integer> stalkerJournalDelays = new HashMap<>();
		private final Map<UUID, Integer> hunterJournalDelays = new HashMap<>();
		private final Set<UUID> stalkerJournalPlayers = new HashSet<>();
		private final Set<UUID> hunterJournalPlayers = new HashSet<>();
		private final Set<UUID> restoreJournalAfterDeathPlayers = new HashSet<>();
		private boolean journalHouse;
		private ResourceKey<Level> journalHouseDimension = FAKE_OVERWORLD;
		private BlockPos journalHousePos = BlockPos.ZERO;
		private final Set<UUID> journalHouseMessagePlayers = new HashSet<>();
		private final List<SpawnedStructureBounds> spawnedStructures = new ArrayList<>();
		boolean creepyVillageActive;
		boolean creepyVillageRepaired;
		ResourceKey<Level> creepyVillageDimension = FAKE_OVERWORLD;
		BlockPos creepyVillageCenter = BlockPos.ZERO;
		int creepyVillageRadius;
		int creepyVillageRequiredDoorCount;
		int creepyVillageRepairCheckTicks;
		int creepyVillageScanTicks;
		List<CreepyVillageManager.VillagerAnchor> creepyVillageVillagerAnchors = new ArrayList<>();

		public static FakeworldSaveData load(CompoundTag tag) {
			FakeworldSaveData data = new FakeworldSaveData();
			data.belongingMessageShown = tag.getBoolean(BELONGING_MESSAGE_SHOWN_KEY);
			data.ambientEventDelayTicks = tag.getInt(AMBIENT_EVENT_DELAY_TICKS_KEY);
			data.ambientEventDelayInitialized = tag.getBoolean(AMBIENT_EVENT_DELAY_INITIALIZED_KEY);
			data.lastAmbientEventKey = tag.getString(LAST_AMBIENT_EVENT_KEY);
			data.directorPhase = tag.getInt(DIRECTOR_PHASE_KEY);
			data.directorScore = tag.getInt(DIRECTOR_SCORE_KEY);
			data.directorMood = DirectorMood.fromName(tag.getString(DIRECTOR_MOOD_KEY));
			data.directorMoodTicks = tag.contains(DIRECTOR_MOOD_TICKS_KEY, Tag.TAG_INT) ? Math.max(1, tag.getInt(DIRECTOR_MOOD_TICKS_KEY)) : DIRECTOR_MOOD_DEFAULT_TICKS;
			data.directorPressure = Mth.clamp(tag.getInt(DIRECTOR_PRESSURE_KEY), MIN_DIRECTOR_PRESSURE, MAX_DIRECTOR_PRESSURE);
			loadStringIntMap(tag, AMBIENT_EVENT_COOLDOWNS_KEY, data.ambientEventCooldowns);
			loadDirectorEventCounts(tag, data.directorEventCounts);
			loadUuidSet(tag, PENDING_STALKER_JOURNAL_UPDATES_KEY, data.pendingStalkerJournalUpdates);
			loadUuidSet(tag, PENDING_HUNTER_JOURNAL_UPDATES_KEY, data.pendingHunterJournalUpdates);
			loadUuidIntMap(tag, STALKER_JOURNAL_DELAYS_KEY, data.stalkerJournalDelays);
			loadUuidIntMap(tag, HUNTER_JOURNAL_DELAYS_KEY, data.hunterJournalDelays);
			loadUuidSet(tag, STALKER_JOURNAL_PLAYERS_KEY, data.stalkerJournalPlayers);
			loadUuidSet(tag, HUNTER_JOURNAL_PLAYERS_KEY, data.hunterJournalPlayers);
			loadUuidSet(tag, RESTORE_JOURNAL_AFTER_DEATH_PLAYERS_KEY, data.restoreJournalAfterDeathPlayers);
			loadUuidSet(tag, JOURNAL_HOUSE_MESSAGE_PLAYERS_KEY, data.journalHouseMessagePlayers);
			loadSpawnedStructures(tag, data.spawnedStructures);
			if (tag.contains(JOURNAL_HOUSE_KEY, Tag.TAG_COMPOUND)) {
				CompoundTag houseTag = tag.getCompound(JOURNAL_HOUSE_KEY);
				data.journalHouse = true;
				ResourceLocation dimensionId = ResourceLocation.tryParse(houseTag.getString(JOURNAL_HOUSE_DIMENSION_KEY));
				if (dimensionId != null) {
					data.journalHouseDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
				}
				data.journalHousePos = new BlockPos(
						houseTag.getInt(JOURNAL_HOUSE_X_KEY),
						houseTag.getInt(JOURNAL_HOUSE_Y_KEY),
						houseTag.getInt(JOURNAL_HOUSE_Z_KEY)
				);
			}
			CreepyVillageManager.load(data, tag);
			return data;
		}

		private static void loadUuidSet(CompoundTag tag, String key, Set<UUID> uuids) {
			ListTag list = tag.getList(key, Tag.TAG_STRING);
			for (int i = 0; i < list.size(); i++) {
				try {
					uuids.add(UUID.fromString(list.getString(i)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		private static ListTag saveUuidSet(Set<UUID> uuids) {
			ListTag list = new ListTag();
			for (UUID uuid : uuids) {
				list.add(StringTag.valueOf(uuid.toString()));
			}
			return list;
		}

		private static void loadUuidIntMap(CompoundTag tag, String key, Map<UUID, Integer> values) {
			ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				try {
					values.put(UUID.fromString(entry.getString(JOURNAL_DELAY_PLAYER_KEY)), entry.getInt(JOURNAL_DELAY_TICKS_KEY));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		private static ListTag saveUuidIntMap(Map<UUID, Integer> values) {
			ListTag list = new ListTag();
			for (Map.Entry<UUID, Integer> value : values.entrySet()) {
				CompoundTag entry = new CompoundTag();
				entry.putString(JOURNAL_DELAY_PLAYER_KEY, value.getKey().toString());
				entry.putInt(JOURNAL_DELAY_TICKS_KEY, value.getValue());
				list.add(entry);
			}
			return list;
		}

		private static void loadStringIntMap(CompoundTag tag, String key, Map<String, Integer> values) {
			ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				String eventKey = entry.getString(AMBIENT_EVENT_KEY);
				if (!eventKey.isEmpty()) {
					values.put(eventKey, entry.getInt(AMBIENT_EVENT_COOLDOWN_TICKS_KEY));
				}
			}
		}

		private static ListTag saveStringIntMap(Map<String, Integer> values) {
			ListTag list = new ListTag();
			for (Map.Entry<String, Integer> value : values.entrySet()) {
				if (value.getValue() <= 0) {
					continue;
				}
				CompoundTag entry = new CompoundTag();
				entry.putString(AMBIENT_EVENT_KEY, value.getKey());
				entry.putInt(AMBIENT_EVENT_COOLDOWN_TICKS_KEY, value.getValue());
				list.add(entry);
			}
			return list;
		}

		private static void loadDirectorEventCounts(CompoundTag tag, Map<String, Integer> values) {
			ListTag list = tag.getList(DIRECTOR_EVENT_COUNTS_KEY, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				String eventKey = entry.getString(DIRECTOR_EVENT_COUNT_EVENT_KEY);
				if (!eventKey.isEmpty()) {
					values.put(eventKey, entry.getInt(DIRECTOR_EVENT_COUNT_VALUE_KEY));
				}
			}
		}

		private static ListTag saveDirectorEventCounts(Map<String, Integer> values) {
			ListTag list = new ListTag();
			for (Map.Entry<String, Integer> value : values.entrySet()) {
				CompoundTag entry = new CompoundTag();
				entry.putString(DIRECTOR_EVENT_COUNT_EVENT_KEY, value.getKey());
				entry.putInt(DIRECTOR_EVENT_COUNT_VALUE_KEY, value.getValue());
				list.add(entry);
			}
			return list;
		}

		private static void loadSpawnedStructures(CompoundTag tag, List<SpawnedStructureBounds> structures) {
			ListTag list = tag.getList(SPAWNED_STRUCTURES_KEY, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString(STRUCTURE_DIMENSION_KEY));
				if (dimensionId == null) {
					continue;
				}

				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
				BlockPos min = new BlockPos(entry.getInt(STRUCTURE_MIN_X_KEY), entry.getInt(STRUCTURE_MIN_Y_KEY), entry.getInt(STRUCTURE_MIN_Z_KEY));
				BlockPos max = new BlockPos(entry.getInt(STRUCTURE_MAX_X_KEY), entry.getInt(STRUCTURE_MAX_Y_KEY), entry.getInt(STRUCTURE_MAX_Z_KEY));
				structures.add(new SpawnedStructureBounds(dimension, entry.getString(STRUCTURE_NAME_KEY), min, max));
			}
		}

		private static ListTag saveSpawnedStructures(List<SpawnedStructureBounds> structures) {
			ListTag list = new ListTag();
			for (SpawnedStructureBounds structure : structures) {
				CompoundTag entry = new CompoundTag();
				entry.putString(STRUCTURE_NAME_KEY, structure.name());
				entry.putString(STRUCTURE_DIMENSION_KEY, structure.dimension().location().toString());
				entry.putInt(STRUCTURE_MIN_X_KEY, structure.min().getX());
				entry.putInt(STRUCTURE_MIN_Y_KEY, structure.min().getY());
				entry.putInt(STRUCTURE_MIN_Z_KEY, structure.min().getZ());
				entry.putInt(STRUCTURE_MAX_X_KEY, structure.max().getX());
				entry.putInt(STRUCTURE_MAX_Y_KEY, structure.max().getY());
				entry.putInt(STRUCTURE_MAX_Z_KEY, structure.max().getZ());
				list.add(entry);
			}
			return list;
		}

		public boolean hasShownBelongingMessage() {
			return this.belongingMessageShown;
		}

		public void setBelongingMessageShown() {
			this.belongingMessageShown = true;
			this.setDirty();
		}

		public int getAmbientEventDelayTicks() {
			return this.ambientEventDelayTicks;
		}

		public void setAmbientEventDelayTicks(int ambientEventDelayTicks) {
			this.ambientEventDelayTicks = ambientEventDelayTicks;
			this.setDirty();
		}

		public boolean hasInitializedAmbientEventDelay() {
			return this.ambientEventDelayInitialized;
		}

		public void setInitializedAmbientEventDelay() {
			this.ambientEventDelayInitialized = true;
			this.setDirty();
		}

		public void decrementAmbientEventDelayTicks() {
			if (this.ambientEventDelayTicks > 0) {
				this.ambientEventDelayTicks--;
			}
		}

		public String getLastAmbientEventKey() {
			return this.lastAmbientEventKey;
		}

		public void setLastAmbientEventKey(String eventKey) {
			this.lastAmbientEventKey = eventKey;
			this.setDirty();
		}

		public int getAmbientEventCooldown(String eventKey) {
			return this.ambientEventCooldowns.getOrDefault(eventKey, 0);
		}

		public void setAmbientEventCooldown(String eventKey, int ticks) {
			if (ticks <= 0) {
				this.ambientEventCooldowns.remove(eventKey);
			} else {
				this.ambientEventCooldowns.put(eventKey, ticks);
			}
			this.setDirty();
		}

		public void tickAmbientEventCooldowns(int ticks) {
			if (this.ambientEventCooldowns.isEmpty()) {
				return;
			}

			Iterator<Map.Entry<String, Integer>> iterator = this.ambientEventCooldowns.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Integer> entry = iterator.next();
				int remainingTicks = entry.getValue() - ticks;
				if (remainingTicks <= 0) {
					iterator.remove();
				} else {
					entry.setValue(remainingTicks);
				}
			}
			this.setDirty();
		}

		public int getDirectorPhase() {
			return this.directorPhase;
		}

		public int getDirectorScore() {
			return this.directorScore;
		}

		public DirectorMood getDirectorMood() {
			return this.directorMood;
		}

		public int getDirectorPressure() {
			return this.directorPressure;
		}

		public int addDirectorPressure(int delta) {
			int pressure = Mth.clamp(this.directorPressure + delta, MIN_DIRECTOR_PRESSURE, MAX_DIRECTOR_PRESSURE);
			if (pressure != this.directorPressure) {
				this.directorPressure = pressure;
				this.setDirty();
			}
			return this.directorPressure;
		}

		public void advanceDirectorMood(DirectorMood nextMood) {
			int ticks = Math.max(0, this.directorMoodTicks - 1);
			if (ticks > 0) {
				this.directorMoodTicks = ticks;
			} else {
				this.directorMood = nextMood;
				this.directorMoodTicks = DIRECTOR_MOOD_DEFAULT_TICKS;
			}
			this.setDirty();
		}

		public int getDirectorEventCount(String eventKey) {
			return this.directorEventCounts.getOrDefault(eventKey, 0);
		}

		public void recordDirectorEvent(String eventKey, int score) {
			this.directorEventCounts.put(eventKey, this.getDirectorEventCount(eventKey) + 1);
			this.directorScore = Math.max(0, this.directorScore + Math.max(0, score));
			this.updateDirectorPhase();
			this.setDirty();
		}

		public void updateDirectorPhase() {
			int phase = 0;
			if (this.directorScore >= DIRECTOR_PHASE_3_SCORE) {
				phase = 3;
			} else if (this.directorScore >= DIRECTOR_PHASE_2_SCORE) {
				phase = 2;
			} else if (this.directorScore >= DIRECTOR_PHASE_1_SCORE) {
				phase = 1;
			}

			if (phase != this.directorPhase) {
				this.directorPhase = phase;
				this.setDirty();
			}
		}

		public boolean hasPendingStalkerJournalUpdates() {
			return !this.pendingStalkerJournalUpdates.isEmpty();
		}

		public boolean hasPendingHunterJournalUpdates() {
			return !this.pendingHunterJournalUpdates.isEmpty();
		}

		public boolean hasPendingStalkerJournalUpdate(UUID playerUuid) {
			return this.pendingStalkerJournalUpdates.contains(playerUuid);
		}

		public boolean hasPendingHunterJournalUpdate(UUID playerUuid) {
			return this.pendingHunterJournalUpdates.contains(playerUuid);
		}

		public void addPendingStalkerJournalUpdate(UUID playerUuid, int delayTicks) {
			this.stalkerJournalDelays.put(playerUuid, delayTicks);
			if (this.pendingStalkerJournalUpdates.add(playerUuid)) {
				this.setDirty();
			} else {
				this.setDirty();
			}
		}

		public void addPendingHunterJournalUpdate(UUID playerUuid, int delayTicks) {
			this.hunterJournalDelays.put(playerUuid, delayTicks);
			if (this.pendingHunterJournalUpdates.add(playerUuid)) {
				this.setDirty();
			} else {
				this.setDirty();
			}
		}

		public void clearPendingStalkerJournalUpdate(UUID playerUuid) {
			if (this.pendingStalkerJournalUpdates.remove(playerUuid)) {
				this.setDirty();
			}
			if (this.stalkerJournalDelays.remove(playerUuid) != null) {
				this.setDirty();
			}
		}

		public void clearPendingHunterJournalUpdate(UUID playerUuid) {
			if (this.pendingHunterJournalUpdates.remove(playerUuid)) {
				this.setDirty();
			}
			if (this.hunterJournalDelays.remove(playerUuid) != null) {
				this.setDirty();
			}
		}

		public int decrementStalkerJournalDelay(UUID playerUuid) {
			return this.decrementJournalDelay(this.stalkerJournalDelays, playerUuid);
		}

		public int decrementHunterJournalDelay(UUID playerUuid) {
			return this.decrementJournalDelay(this.hunterJournalDelays, playerUuid);
		}

		private int decrementJournalDelay(Map<UUID, Integer> delays, UUID playerUuid) {
			int ticks = delays.getOrDefault(playerUuid, 0);
			if (ticks <= 0) {
				return 0;
			}

			ticks--;
			delays.put(playerUuid, ticks);
			this.setDirty();
			return ticks;
		}

		public boolean hasStalkerJournalEntry(UUID playerUuid) {
			return this.stalkerJournalPlayers.contains(playerUuid);
		}

		public boolean hasHunterJournalEntry(UUID playerUuid) {
			return this.hunterJournalPlayers.contains(playerUuid);
		}

		public void addStalkerJournalEntry(UUID playerUuid) {
			if (this.stalkerJournalPlayers.add(playerUuid)) {
				this.setDirty();
			}
		}

		public void addHunterJournalEntry(UUID playerUuid) {
			if (this.hunterJournalPlayers.add(playerUuid)) {
				this.setDirty();
			}
		}

		public boolean shouldRestoreJournalAfterDeath(UUID playerUuid) {
			return this.restoreJournalAfterDeathPlayers.contains(playerUuid);
		}

		public void setRestoreJournalAfterDeath(UUID playerUuid) {
			if (this.restoreJournalAfterDeathPlayers.add(playerUuid)) {
				this.setDirty();
			}
		}

		public void clearRestoreJournalAfterDeath(UUID playerUuid) {
			if (this.restoreJournalAfterDeathPlayers.remove(playerUuid)) {
				this.setDirty();
			}
		}

		public boolean hasJournalHouse() {
			return this.journalHouse;
		}

		public ResourceKey<Level> getJournalHouseDimension() {
			return this.journalHouseDimension;
		}

		public BlockPos getJournalHousePos() {
			return this.journalHousePos;
		}

		public void setJournalHouse(ResourceKey<Level> dimension, BlockPos pos) {
			this.journalHouse = true;
			this.journalHouseDimension = dimension;
			this.journalHousePos = pos.immutable();
			this.journalHouseMessagePlayers.clear();
			this.setDirty();
		}

		public boolean hasSeenJournalHouseMessage(UUID playerUuid) {
			return this.journalHouseMessagePlayers.contains(playerUuid);
		}

		public void setSeenJournalHouseMessage(UUID playerUuid) {
			if (this.journalHouseMessagePlayers.add(playerUuid)) {
				this.setDirty();
			}
		}

		public void addSpawnedStructure(SpawnedStructureBounds structure) {
			this.spawnedStructures.add(structure);
			this.setDirty();
		}

		public boolean intersectsSpawnedStructure(SpawnedStructureBounds structure) {
			for (SpawnedStructureBounds existing : this.spawnedStructures) {
				if (existing.intersects(structure)) {
					return true;
				}
			}
			return false;
		}

		public boolean isInsideSpawnedStructure(ResourceKey<Level> dimension, BlockPos pos) {
			return this.spawnedStructureAt(dimension, pos).isPresent();
		}

		public Optional<SpawnedStructureBounds> spawnedStructureAt(ResourceKey<Level> dimension, BlockPos pos) {
			for (SpawnedStructureBounds structure : this.spawnedStructures) {
				if (structure.contains(dimension, pos)) {
					return Optional.of(structure);
				}
			}
			return Optional.empty();
		}

		public boolean hasActiveCreepyVillage() {
			return this.creepyVillageActive;
		}

		public boolean hasActiveUnrepairedCreepyVillage() {
			return this.creepyVillageActive && !this.creepyVillageRepaired;
		}

		public boolean isCreepyVillageRepaired() {
			return this.creepyVillageRepaired;
		}

		public ResourceKey<Level> getCreepyVillageDimension() {
			return this.creepyVillageDimension;
		}

		public BlockPos getCreepyVillageCenter() {
			return this.creepyVillageCenter;
		}

		public int getCreepyVillageRadius() {
			return this.creepyVillageRadius;
		}

		public int getCreepyVillageRequiredDoorCount() {
			return this.creepyVillageRequiredDoorCount;
		}

		public int getCreepyVillageRepairCheckTicks() {
			return this.creepyVillageRepairCheckTicks;
		}

		public void setCreepyVillageRepairCheckTicks(int ticks) {
			this.creepyVillageRepairCheckTicks = ticks;
		}

		public void decrementCreepyVillageRepairCheckTicks() {
			if (this.creepyVillageRepairCheckTicks > 0) {
				this.creepyVillageRepairCheckTicks--;
			}
		}

		public int getCreepyVillageScanTicks() {
			return this.creepyVillageScanTicks;
		}

		public void setCreepyVillageScanTicks(int ticks) {
			this.creepyVillageScanTicks = ticks;
		}

		public void decrementCreepyVillageScanTicks() {
			if (this.creepyVillageScanTicks > 0) {
				this.creepyVillageScanTicks--;
			}
		}

		public List<CreepyVillageManager.VillagerAnchor> getCreepyVillageVillagerAnchors() {
			return this.creepyVillageVillagerAnchors;
		}

		public void setCreepyVillageVillagerAnchors(List<CreepyVillageManager.VillagerAnchor> anchors) {
			this.creepyVillageVillagerAnchors.clear();
			this.creepyVillageVillagerAnchors.addAll(anchors);
			this.setDirty();
		}

		public void clearCreepyVillageVillagerAnchors() {
			this.creepyVillageVillagerAnchors.clear();
			this.setDirty();
		}

		public void setCreepyVillage(ResourceKey<Level> dimension, BlockPos center, int radius, int requiredDoorCount) {
			this.creepyVillageActive = true;
			this.creepyVillageRepaired = false;
			this.creepyVillageDimension = dimension;
			this.creepyVillageCenter = center.immutable();
			this.creepyVillageRadius = radius;
			this.creepyVillageRequiredDoorCount = requiredDoorCount;
			this.creepyVillageRepairCheckTicks = 100;
			this.creepyVillageVillagerAnchors.clear();
			this.setDirty();
		}

		public void setCreepyVillageRepaired() {
			this.creepyVillageRepaired = true;
			this.setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag) {
			tag.putBoolean(BELONGING_MESSAGE_SHOWN_KEY, this.belongingMessageShown);
			tag.putInt(AMBIENT_EVENT_DELAY_TICKS_KEY, this.ambientEventDelayTicks);
			tag.putBoolean(AMBIENT_EVENT_DELAY_INITIALIZED_KEY, this.ambientEventDelayInitialized);
			tag.putString(LAST_AMBIENT_EVENT_KEY, this.lastAmbientEventKey);
			tag.putInt(DIRECTOR_PHASE_KEY, this.directorPhase);
			tag.putInt(DIRECTOR_SCORE_KEY, this.directorScore);
			tag.putString(DIRECTOR_MOOD_KEY, this.directorMood.name());
			tag.putInt(DIRECTOR_MOOD_TICKS_KEY, this.directorMoodTicks);
			tag.putInt(DIRECTOR_PRESSURE_KEY, this.directorPressure);
			tag.put(AMBIENT_EVENT_COOLDOWNS_KEY, saveStringIntMap(this.ambientEventCooldowns));
			tag.put(DIRECTOR_EVENT_COUNTS_KEY, saveDirectorEventCounts(this.directorEventCounts));
			tag.put(PENDING_STALKER_JOURNAL_UPDATES_KEY, saveUuidSet(this.pendingStalkerJournalUpdates));
			tag.put(PENDING_HUNTER_JOURNAL_UPDATES_KEY, saveUuidSet(this.pendingHunterJournalUpdates));
			tag.put(STALKER_JOURNAL_DELAYS_KEY, saveUuidIntMap(this.stalkerJournalDelays));
			tag.put(HUNTER_JOURNAL_DELAYS_KEY, saveUuidIntMap(this.hunterJournalDelays));
			tag.put(STALKER_JOURNAL_PLAYERS_KEY, saveUuidSet(this.stalkerJournalPlayers));
			tag.put(HUNTER_JOURNAL_PLAYERS_KEY, saveUuidSet(this.hunterJournalPlayers));
			tag.put(RESTORE_JOURNAL_AFTER_DEATH_PLAYERS_KEY, saveUuidSet(this.restoreJournalAfterDeathPlayers));
			tag.put(JOURNAL_HOUSE_MESSAGE_PLAYERS_KEY, saveUuidSet(this.journalHouseMessagePlayers));
			tag.put(SPAWNED_STRUCTURES_KEY, saveSpawnedStructures(this.spawnedStructures));
			if (this.journalHouse) {
				CompoundTag houseTag = new CompoundTag();
				houseTag.putString(JOURNAL_HOUSE_DIMENSION_KEY, this.journalHouseDimension.location().toString());
				houseTag.putInt(JOURNAL_HOUSE_X_KEY, this.journalHousePos.getX());
				houseTag.putInt(JOURNAL_HOUSE_Y_KEY, this.journalHousePos.getY());
				houseTag.putInt(JOURNAL_HOUSE_Z_KEY, this.journalHousePos.getZ());
				tag.put(JOURNAL_HOUSE_KEY, houseTag);
			}
			CreepyVillageManager.save(this, tag);
			return tag;
		}
	}
}
