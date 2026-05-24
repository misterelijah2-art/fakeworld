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
			world.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 0.8F + world.getRandom().nextFloat() * 0.4F);
			event.advance(PHANTOM_MINING_INTERVAL_TICKS);
		}
	}

	private static void tickSkyObjectEvents(MinecraftServer server) {
		Iterator<SkyObjectEvent> iterator = SKY_OBJECT_EVENTS.iterator();
		while (iterator.hasNext()) {
			SkyObjectEvent event = iterator.next();
			ServerLevel world = server.getLevel(event.dimension());
			ServerPlayer player = server.getPlayerList().getPlayer(event.playerUuid());
			if (world == null || player == null || !player.level().dimension().equals(event.dimension())) {
				iterator.remove();
				continue;
			}

			event.tick();

			if (event.ticksRemaining() <= 0) {
				iterator.remove();
				continue;
			}

			Vec3 playerPos = player.position();
			double lookDot = player.getLookAngle().dot(new Vec3(0, 1, 0));
			if (event.ticksRemaining() < SKY_OBJECT_DURATION_TICKS - 20 && lookDot > SKY_OBJECT_LOOK_DOT) {
				player.hurt(world.damageSources().fell(), SKY_OBJECT_DAMAGE);
			}
		}
	}

	private static void tickAnimalStarePathCooldowns() {
		ANIMAL_STARE_PATH_COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= 1);
		ANIMAL_STARE_PATH_COOLDOWNS.replaceAll((k, v) -> v - 1);
		ANIMAL_STARE_EVENT_COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= 1);
		ANIMAL_STARE_EVENT_COOLDOWNS.replaceAll((k, v) -> v - 1);
	}

	private static void tickAlwaysStaringAnimals(MinecraftServer server) {
		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null) return;

		for (ServerPlayer player : fakeOverworld.players()) {
			List<Animal> nearby = fakeOverworld.getEntitiesOfClass(Animal.class,
					player.getBoundingBox().inflate(ALWAYS_STARE_ANIMAL_RADIUS));
			for (Animal animal : nearby) {
				animal.getLookControl().setLookAt(player, 30F, 30F);
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
				iterator.remove();
				continue;
			}

			boolean anyAlive = false;
			for (UUID animalUuid : event.animalUuids()) {
				Entity entity = world.getEntity(animalUuid);
				if (entity instanceof Animal animal && animal.isAlive()) {
					anyAlive = true;
					double dist = animal.distanceTo(player);
					if (dist > ANIMAL_ATTENTION_MIN_DISTANCE) {
						Vec3 dir = player.position().subtract(animal.position()).normalize();
						animal.setDeltaMovement(dir.scale(ANIMAL_ATTENTION_SPEED));
					}
					animal.getLookControl().setLookAt(player, 30F, 30F);
				}
			}

			if (!anyAlive) {
				iterator.remove();
			}
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

			Entity entity = world.getEntity(doppelganger.entityUuid());
			if (entity == null || !entity.isAlive()) {
				iterator.remove();
				continue;
			}

			doppelganger.tick();
			if (doppelganger.ticksRemaining() <= 0) {
				entity.discard();
				iterator.remove();
			}
		}
	}

	private static void tickTameDogTraps(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> iterator = TAME_DOG_KILL_TIMERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks > 0) {
				entry.setValue(ticks);
				continue;
			}

			iterator.remove();
			ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
			if (fakeOverworld == null) continue;

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.level().dimension().equals(FAKE_OVERWORLD)) continue;

			List<Wolf> dogs = fakeOverworld.getEntitiesOfClass(Wolf.class,
					player.getBoundingBox().inflate(TAME_DOG_SCAN_RADIUS),
					wolf -> wolf.getTags().contains(TAME_DOG_TAG));
			for (Wolf dog : dogs) {
				dog.hurt(fakeOverworld.damageSources().genericKill(), Float.MAX_VALUE);
			}
		}
	}

	private static void tickPendingHunterSpawns(MinecraftServer server) {
		Iterator<PendingHunterSpawn> iterator = PENDING_HUNTER_SPAWNS.iterator();
		while (iterator.hasNext()) {
			PendingHunterSpawn pending = iterator.next();
			int ticks = pending.delayTicks() - 1;
			if (ticks > 0) {
				pending.setDelayTicks(ticks);
				continue;
			}

			iterator.remove();
			ServerLevel world = server.getLevel(pending.dimension());
			ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid());
			if (world == null || player == null || !player.level().dimension().equals(pending.dimension())) continue;

			spawnHunterNear(player, world);
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

			int delay = repair.delayTicks() - 1;
			if (delay > 0) {
				repair.setDelayTicks(delay);
				continue;
			}

			boolean anyPlayerNear = world.players().stream().anyMatch(p ->
					p.blockPosition().distSqr(repair.origin()) < STRUCTURE_REPAIR_PLAYER_RADIUS * STRUCTURE_REPAIR_PLAYER_RADIUS);
			if (anyPlayerNear) {
				repair.setDelayTicks(STRUCTURE_REPAIR_START_DELAY_TICKS);
				continue;
			}

			if (repair.nextIndex() >= repair.blocks().size()) {
				iterator.remove();
				continue;
			}

			StructureTemplate.StructureBlockInfo info = repair.blocks().get(repair.nextIndex());
			world.setBlock(info.pos(), info.state(), Block.UPDATE_ALL);
			if (info.nbt() != null) {
				BlockEntity be = world.getBlockEntity(info.pos());
				if (be != null) {
					be.load(info.nbt());
				}
			}
			repair.advance(STRUCTURE_REPAIR_INTERVAL_TICKS);
		}
	}

	private static void tickTimedTerror(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> iterator = TIMED_TERROR_PLAYERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks > 0) {
				entry.setValue(ticks);
				continue;
			}

			iterator.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				stopTerrorSound(player);
				spawnHunterNear(player, (ServerLevel) player.level());
			}
		}
	}

	private static void tickPendingJournalUpdates(MinecraftServer server, FakeworldSaveData saveData) {
		if (saveData.getJournalUpdateDelayTicks() <= 0) return;
		int remaining = saveData.getJournalUpdateDelayTicks() - 1;
		saveData.setJournalUpdateDelayTicks(remaining);
		if (remaining > 0) return;

		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null) return;

		for (ServerPlayer player : fakeOverworld.players()) {
			updateJournalObservations(player, saveData);
		}
	}

	private static void tickJournalHouse(MinecraftServer server, FakeworldSaveData saveData) {
		if (!saveData.hasJournalHouse()) return;

		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null) return;

		BlockPos housePos = saveData.getJournalHousePos();
		for (ServerPlayer player : fakeOverworld.players()) {
			double dist = player.blockPosition().distSqr(housePos);
			if (dist < JOURNAL_HOUSE_MESSAGE_DISTANCE * JOURNAL_HOUSE_MESSAGE_DISTANCE
					&& !saveData.hasSeenJournalHouseMessage(player.getUUID())) {
				saveData.markJournalHouseMessageSeen(player.getUUID());
				player.sendSystemMessage(Component.literal("Something about this place feels familiar.").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static void tickFakeOverworldSleep(MinecraftServer server) {
		RECENT_FAKE_OVERWORLD_SLEEPERS.entrySet().removeIf(e -> e.getValue() <= 1);
		RECENT_FAKE_OVERWORLD_SLEEPERS.replaceAll((k, v) -> v - 1);
	}

	private static void tickAmbientEventScheduler(MinecraftServer server, FakeworldSaveData saveData) {
		ServerLevel fakeOverworld = server.getLevel(FAKE_OVERWORLD);
		if (fakeOverworld == null) return;

		List<ServerPlayer> fakeWorldPlayers = fakeOverworld.players();
		if (fakeWorldPlayers.isEmpty()) return;

		int scheduledTick = saveData.getAmbientEventScheduledTick();
		int currentTick = server.getTickCount();

		if (scheduledTick > 0 && currentTick < scheduledTick) return;

		if (scheduledTick > 0) {
			runAmbientEventDirector(server, fakeOverworld, fakeWorldPlayers, saveData);
		}

		int cooldownMinutes = saveData.getDirectorMood().cooldownMinutes();
		int nextTick = currentTick + cooldownMinutes * 60 * 20;
		saveData.setAmbientEventScheduledTick(nextTick);
	}

	private static void runAmbientEventDirector(MinecraftServer server, ServerLevel fakeOverworld, List<ServerPlayer> fakeWorldPlayers, FakeworldSaveData saveData) {
		DirectorMood mood = saveData.getDirectorMood();
		List<AmbientEventCandidate> candidates = new ArrayList<>();

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("ambient_sound") < 1) {
			candidates.add(new AmbientEventCandidate("ambient_sound", eventWeight(saveData, mood, "ambient_sound", CONFIG.soundeventWeight, 1), cooldownTicks(CONFIG.soundeventCooldownMinutes), 1, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				triggerAmbientSoundEvent(target);
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("darkness") < DARKNESS_MAX_AMBIENT_EVENTS) {
			candidates.add(new AmbientEventCandidate("darkness", eventWeight(saveData, mood, "darkness", CONFIG.darknessWeight, 1), cooldownTicks(CONFIG.darknessCooldownMinutes), 1, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				triggerDarknessEvent(target);
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("fake_join") < FAKE_JOIN_MAX_AMBIENT_EVENTS) {
			candidates.add(new AmbientEventCandidate("fake_join", eventWeight(saveData, mood, "fake_join", CONFIG.fakeJoinWeight, 1), cooldownTicks(CONFIG.fakeJoinCooldownMinutes), 1, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				triggerFakeJoinEvent(target, fakeOverworld);
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("footsteps") < Integer.MAX_VALUE) {
			candidates.add(new AmbientEventCandidate("footsteps", eventWeight(saveData, mood, "footsteps", CONFIG.footstepsWeight, 1), cooldownTicks(CONFIG.footstepsCooldownMinutes), 1, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				scheduleFootstepsBehind(target, fakeOverworld);
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 1
				&& saveData.getDirectorEventCount("stalker") < Integer.MAX_VALUE) {
			candidates.add(new AmbientEventCandidate("stalker", eventWeight(saveData, mood, "stalker", CONFIG.stalkerWeight, 1), cooldownTicks(CONFIG.stalkerCooldownMinutes), 1, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				return spawnStalkerFor(target, fakeOverworld);
			}));
		}

		if (saveData.getDirectorPhase() >= 2
				&& saveData.getDirectorEventCount("phantom_mining") < PHANTOM_MINING_MAX_AMBIENT_EVENTS) {
			candidates.add(new AmbientEventCandidate("phantom_mining", eventWeight(saveData, mood, "phantom_mining", CONFIG.phantomMiningWeight, 2), cooldownTicks(CONFIG.phantomMiningCooldownMinutes), 2, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				return startPhantomMining(target, fakeOverworld);
			}));
		}

		if (saveData.getDirectorPhase() >= 2
				&& saveData.getDirectorEventCount("sky_object") < SKY_OBJECT_MAX_AMBIENT_EVENTS) {
			candidates.add(new AmbientEventCandidate("sky_object", eventWeight(saveData, mood, "sky_object", CONFIG.skyObjectWeight, 2), cooldownTicks(CONFIG.skyObjectCooldownMinutes), 2, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				startSkyObjectEvent(target, fakeOverworld);
				return true;
			}));
		}

		if (saveData.getDirectorPhase() >= 2
				&& saveData.getDirectorEventCount("animal_attention") < Integer.MAX_VALUE) {
			candidates.add(new AmbientEventCandidate("animal_attention", eventWeight(saveData, mood, "animal_attention", CONFIG.animalAttentionWeight, 2), cooldownTicks(CONFIG.animalAttentionCooldownMinutes), 2, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				return startAnimalAttention(target, fakeOverworld);
			}));
		}

		if (saveData.getDirectorPhase() >= 3
				&& saveData.getDirectorEventCount("doppelganger") < Integer.MAX_VALUE) {
			candidates.add(new AmbientEventCandidate("doppelganger", eventWeight(saveData, mood, "doppelganger", CONFIG.doppelgangerWeight, 3), cooldownTicks(CONFIG.doppelgangerCooldownMinutes), 3, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				return spawnAnimalDoppelganger(target, fakeOverworld);
			}));
		}

		if (saveData.getDirectorPhase() >= 3
				&& saveData.getDirectorEventCount("hunter") < Integer.MAX_VALUE) {
			candidates.add(new AmbientEventCandidate("hunter", eventWeight(saveData, mood, "hunter", CONFIG.hunterWeight, 3), cooldownTicks(CONFIG.hunterCooldownMinutes), 3, () -> {
				ServerPlayer target = randomPlayer(fakeWorldPlayers, fakeOverworld);
				if (target == null) return false;
				startTimedTerrorFor(target);
				return true;
			}));
		}

		if (candidates.isEmpty()) return;

		for (int attempt = 0; attempt < AMBIENT_EVENT_FAILED_PICK_RETRIES; attempt++) {
			AmbientEventCandidate picked = weightedRandom(candidates, fakeOverworld);
			if (picked == null) break;
			boolean success = picked.action().get();
			if (success) {
				saveData.recordDirectorEvent(picked.eventId(), picked.cooldownTicks());
				saveData.advanceDirectorMood(DIRECTOR_MOOD_DEFAULT_TICKS);
				int pressureGain = picked.phaseRequired() >= 3 ? QUIET_DIRECTOR_PRESSURE_GAIN * 2 : QUIET_DIRECTOR_PRESSURE_GAIN;
				saveData.setDirectorPressure(Math.min(MAX_DIRECTOR_PRESSURE, saveData.getDirectorPressure() + pressureGain));
				return;
			}
			saveData.setDirectorPressure(Math.min(MAX_DIRECTOR_PRESSURE, saveData.getDirectorPressure() + FAILED_EVENT_DIRECTOR_PRESSURE_GAIN));
		}
	}

	private static void triggerAmbientSoundEvent(ServerPlayer player) {
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				Holder.direct(SOUNDEVENT_SOUND),
				SoundSource.AMBIENT,
				player.getX(), player.getY(), player.getZ(),
				1.0F, 1.0F,
				player.level().getRandom().nextLong()
		));
	}

	// ---- helper stubs that must exist in master (referenced above) ----

	private static boolean canQueueAmbientEvent(FakeworldSaveData saveData, String eventId, int phaseRequired) {
		return saveData.getDirectorPhase() >= phaseRequired
				&& saveData.getAmbientEventCooldownTick(eventId) <= 0;
	}

	private static int eventWeight(FakeworldSaveData saveData, DirectorMood mood, String eventId, int baseWeight, int phaseRequired) {
		return baseWeight;
	}

	private static int cooldownTicks(int minutes) {
		return minutes * 60 * 20;
	}

	private static ServerPlayer randomPlayer(List<ServerPlayer> players, ServerLevel level) {
		if (players.isEmpty()) return null;
		return players.get(level.getRandom().nextInt(players.size()));
	}

	private static <T extends WeightedEntry> T weightedRandom(List<T> entries, ServerLevel level) {
		int total = entries.stream().mapToInt(WeightedEntry::weight).sum();
		if (total <= 0) return null;
		int roll = level.getRandom().nextInt(total);
		int cumulative = 0;
		for (T entry : entries) {
			cumulative += entry.weight();
			if (roll < cumulative) return entry;
		}
		return null;
	}

	// ---- rest of master methods follow (kept verbatim) ----

	private static void removeBloodSplatter(Iterable<ServerLevel> levels, ScheduledBloodSplatter splatter) {
		for (ServerLevel level : levels) {
			if (level.dimension().equals(splatter.dimension())) {
				BlockState state = level.getBlockState(splatter.pos());
				if (state.is(BLOOD_SPLATTER)) {
					level.removeBlock(splatter.pos(), false);
				}
				return;
			}
		}
	}

	private static void triggerDarknessEvent(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, false));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				Holder.direct(DARKNESS_SOUND),
				SoundSource.AMBIENT,
				player.getX(), player.getY(), player.getZ(),
				1.0F, 1.0F,
				player.level().getRandom().nextLong()
		));
	}

	private static void shuffleInventory(ServerPlayer player, ServerLevel world) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				items.add(stack.copy());
			}
		}
		Collections.shuffle(items, world.getRandom().asJavaRandom());
		int itemIndex = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && itemIndex < items.size(); i++) {
			if (!player.getInventory().getItem(i).isEmpty()) {
				player.getInventory().setItem(i, items.get(itemIndex++));
			}
		}
		player.inventoryMenu.broadcastChanges();
	}

	private static void scheduleFootstepsBehind(ServerPlayer player, ServerLevel world) {
		int sideSign = world.getRandom().nextBoolean() ? 1 : -1;
		SCHEDULED_FOOTSTEPS.add(new ScheduledFootsteps(player.getUUID(), world.dimension(), 0, sideSign));
	}

	private static Vec3 currentFootstepPos(ServerPlayer player, ServerLevel world, int stepIndex, int sideSign) {
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 behind = look.scale(-1).normalize();
		double distance = FOOTSTEP_START_DISTANCE + stepIndex * 1.5;
		return player.position().add(behind.scale(distance)).add(right.scale(sideSign * 0.5));
	}

	private static void playFootstep(ServerLevel world, Vec3 pos, int stepIndex) {
		float pitch = 0.85F + world.getRandom().nextFloat() * 0.3F;
		world.playSound(null, pos.x, pos.y, pos.z, net.minecraft.sounds.SoundEvents.GRASS_STEP, SoundSource.PLAYERS, 0.6F, pitch);
	}

	private static void triggerFakeJoinEvent(ServerPlayer player, ServerLevel world) {
		String fakeName = generateFakeName(world);
		net.minecraft.network.protocol.game.ClientboundChatPacket joinPacket = new net.minecraft.network.protocol.game.ClientboundChatPacket(
				net.minecraft.network.chat.Component.translatable("multiplayer.player.joined", Component.literal(fakeName).withStyle(ChatFormatting.YELLOW)),
				net.minecraft.network.chat.ChatType.bind(net.minecraft.network.chat.ChatType.SYSTEM, world.registryAccess(), Component.literal("Server")),
				java.util.Optional.empty()
		);
		player.connection.send(joinPacket);

		int nametagDistance = FAKE_JOIN_NAMETAG_MIN_DISTANCE + world.getRandom().nextInt(FAKE_JOIN_NAMETAG_MAX_DISTANCE - FAKE_JOIN_NAMETAG_MIN_DISTANCE);
		Vec3 spawnPos = randomHorizontalOffset(player.position(), nametagDistance, world);

		ArmorStand stand = new ArmorStand(world, spawnPos.x, spawnPos.y, spawnPos.z);
		stand.setCustomName(Component.literal(fakeName));
		stand.setCustomNameVisible(true);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		world.addFreshEntity(stand);
		FAKE_JOIN_PRESENCES.add(new FakeJoinPresence(stand.getUUID(), world.dimension(), FAKE_JOIN_PRESENCE_DURATION_TICKS));
	}

	private static String generateFakeName(ServerLevel world) {
		String[] prefixes = {"Shadow", "Ghost", "Void", "Dark", "Silent", "Hollow", "Lost", "Forgotten"};
		String[] suffixes = {"Walker", "Watcher", "Seeker", "Dreamer", "Wanderer", "Stalker", "Hunter", "Lurker"};
		return prefixes[world.getRandom().nextInt(prefixes.length)] + suffixes[world.getRandom().nextInt(suffixes.length)];
	}

	private static Vec3 randomHorizontalOffset(Vec3 origin, double distance, ServerLevel world) {
		double angle = world.getRandom().nextDouble() * 2 * Math.PI;
		return origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
	}

	private static boolean startPhantomMining(ServerPlayer player, ServerLevel world) {
		BlockPos playerPos = player.blockPosition();
		if (playerPos.getY() > -PHANTOM_MINING_MIN_DEPTH) return false;

		List<BlockPos> path = findMiningPath(world, playerPos);
		if (path.size() < PHANTOM_MINING_MIN_LENGTH) return false;

		PHANTOM_MINING_EVENTS.add(new PhantomMiningEvent(player.getUUID(), world.dimension(), path, PHANTOM_MINING_INTERVAL_TICKS));
		return true;
	}

	private static List<BlockPos> findMiningPath(ServerLevel world, BlockPos start) {
		List<BlockPos> path = new ArrayList<>();
		BlockPos current = start.offset(
				world.getRandom().nextInt(7) - 3,
				-2 - world.getRandom().nextInt(4),
				world.getRandom().nextInt(7) - 3
		);

		Direction dir = Direction.from2DDataValue(world.getRandom().nextInt(4));
		int length = PHANTOM_MINING_MIN_LENGTH + world.getRandom().nextInt(PHANTOM_MINING_MAX_LENGTH - PHANTOM_MINING_MIN_LENGTH);
		for (int i = 0; i < length; i++) {
			if (!world.getBlockState(current).isAir()) {
				path.add(current);
			}
			current = current.relative(dir);
		}
		return path;
	}

	private static void startSkyObjectEvent(ServerPlayer player, ServerLevel world) {
		SKY_OBJECT_EVENTS.add(new SkyObjectEvent(player.getUUID(), world.dimension(), SKY_OBJECT_DURATION_TICKS));
		saveData -> saveData.setDirectorPressure(Math.min(MAX_DIRECTOR_PRESSURE,
				getSaveData(world.getServer()).getDirectorPressure() + SKY_OBJECT_DIRECTOR_PRESSURE_GAIN));
	}

	private static boolean startAnimalAttention(ServerPlayer player, ServerLevel world) {
		List<Animal> animals = world.getEntitiesOfClass(Animal.class,
				player.getBoundingBox().inflate(ANIMAL_ATTENTION_RADIUS),
				a -> !(a instanceof Wolf));
		if (animals.isEmpty()) return false;

		if (animals.size() > ANIMAL_ATTENTION_MAX_ANIMALS) {
			animals = animals.subList(0, ANIMAL_ATTENTION_MAX_ANIMALS);
		}

		List<UUID> uuids = new ArrayList<>();
		for (Animal a : animals) uuids.add(a.getUUID());
		ANIMAL_ATTENTION_EVENTS.add(new AnimalAttentionEvent(player.getUUID(), world.dimension(), uuids));
		return true;
	}

	private static boolean spawnStalkerFor(ServerPlayer player, ServerLevel world) {
		Vec3 pos = findStalkerPosition(player, world);
		if (pos == null) return false;

		StalkerEntity stalker = STALKER.create(world);
		if (stalker == null) return false;
		stalker.moveTo(pos.x, pos.y, pos.z, world.getRandom().nextFloat() * 360F, 0F);
		stalker.setTarget(player);
		stalker.setStalkedPlayer(player.getUUID());
		world.addFreshEntity(stalker);

		scheduleAbandonedHomeSign(player, world, getSaveData(world.getServer()));
		return true;
	}

	private static Vec3 findStalkerPosition(ServerPlayer player, ServerLevel world) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = world.getRandom().nextDouble() * 2 * Math.PI;
			double dist = STALKER_SPAWN_MIN_DISTANCE + world.getRandom().nextDouble() * (STALKER_SPAWN_MAX_DISTANCE - STALKER_SPAWN_MIN_DISTANCE);
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
			BlockPos candidate = new BlockPos((int) x, y, (int) z);
			if (world.getBlockState(candidate).isAir() && world.getBlockState(candidate.below()).isSolid()) {
				return new Vec3(x, y, z);
			}
		}
		return null;
	}

	private static void scheduleAbandonedHomeSign(ServerPlayer player, ServerLevel world, FakeworldSaveData saveData) {
		if (saveData.getDirectorEventCount("abandoned_home_sign") >= ABANDONED_HOME_SIGN_MAX_EVENTS) return;

		BlockPos signPos = findSignPosition(player, world);
		if (signPos == null) return;

		placeAbandonedHomeSign(world, signPos);
		saveData.recordDirectorEvent("abandoned_home_sign", 0);
	}

	private static BlockPos findSignPosition(ServerPlayer player, ServerLevel world) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = world.getRandom().nextDouble() * 2 * Math.PI;
			double dist = 8 + world.getRandom().nextDouble() * 16;
			int x = (int) (player.getX() + Math.cos(angle) * dist);
			int z = (int) (player.getZ() + Math.sin(angle) * dist);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.below()).isSolid()) {
				return pos;
			}
		}
		return null;
	}

	private static void placeAbandonedHomeSign(ServerLevel world, BlockPos pos) {
		String message = STALKER_SIGN_MESSAGES[world.getRandom().nextInt(STALKER_SIGN_MESSAGES.length)];
		Direction facing = Direction.from2DDataValue(world.getRandom().nextInt(4));
		BlockState signState = net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, facing.get2DDataValue() * 4);
		world.setBlock(pos, signState, Block.UPDATE_ALL);

		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof SignBlockEntity sign) {
			SignText text = new SignText();
			text = text.setMessage(1, Component.literal(message).withStyle(ChatFormatting.DARK_RED));
			sign.setAllowedPlayerEditor(null);
			sign.setText(text, true);
			sign.setChanged();
		}
	}

	private static void stopTerrorSound(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(
				new ResourceLocation(MOD_ID, "terror"), SoundSource.AMBIENT));
		TIMED_TERROR_PLAYERS.remove(player.getUUID());
	}

	private static void startTimedTerrorFor(ServerPlayer player) {
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				Holder.direct(TERROR_SOUND),
				SoundSource.AMBIENT,
				player.getX(), player.getY(), player.getZ(),
				1.0F, 1.0F,
				player.level().getRandom().nextLong()
		));
		TIMED_TERROR_PLAYERS.put(player.getUUID(), HUNTER_WARNING_TICKS);
	}

	private static void spawnHunterNear(ServerPlayer player, ServerLevel world) {
		Vec3 pos = findStalkerPosition(player, world);
		if (pos == null) return;

		HunterEntity hunter = HUNTER.create(world);
		if (hunter == null) return;
		hunter.moveTo(pos.x, pos.y, pos.z, world.getRandom().nextFloat() * 360F, 0F);
		hunter.setTarget(player);
		world.addFreshEntity(hunter);
	}

	private static boolean spawnAnimalDoppelganger(ServerPlayer player, ServerLevel world) {
		List<Animal> nearby = world.getEntitiesOfClass(Animal.class,
				player.getBoundingBox().inflate(DOPPELGANGER_LOADED_CHUNK_RADIUS * 16),
				a -> !(a instanceof Wolf));
		if (nearby.isEmpty()) return false;

		Animal original = nearby.get(world.getRandom().nextInt(nearby.size()));
		Animal 