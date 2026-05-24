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