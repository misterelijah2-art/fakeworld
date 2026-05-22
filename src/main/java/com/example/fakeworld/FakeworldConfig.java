package com.example.fakeworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class FakeworldConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fakeworld.json");

	public int configVersion = 7;

	// Set to false to completely disable the fake overworld dimension.
	// When disabled, world generation behaves as vanilla (e.g. superflat stays superflat).
	public boolean fakeworldEnabled = true;

	public boolean ambientDirectorEnabled = true;
	public boolean ambientDebugMessages = false;

	public int bleedingTreeChancePercent = 10;
	public int ambientEventMinDelaySeconds = 45;
	public int ambientEventMaxDelaySeconds = 140;
	public int ambientQuietWeight = 160;

	public int belongingMessageWeight = 120;
	public int fakeAdvancementWeight = 35;
	public int journalHouseWeight = 30;
	public int stalkerWeight = 50;
	public int darknessWeight = 60;
	public int inventoryShuffleWeight = 40;
	public int footstepsWeight = 60;
	public int fakeJoinWeight = 25;
	public int phantomMiningWeight = 35;
	public int skyObjectWeight = 24;
	public int animalAttentionWeight = 50;
	public int animalDoppelgangerWeight = 30;
	public int tameDogWeight = 25;
	public int mimicVillagerWeight = 35;
	public int creepyVillageWeight = 18;
	public int abandonedHomeSignWeight = 14;

	public int castleStructureWeight = 10;
	public int campsiteStructureWeight = 60;
	public int emptyStructureWeight = 60;
	public int towerStructureWeight = 100;

	public int belongingMessageCooldownMinutes = 20;
	public int fakeAdvancementCooldownMinutes = 7;
	public int journalHouseCooldownMinutes = 20;
	public int stalkerCooldownMinutes = 5;
	public int darknessCooldownMinutes = 6;
	public int inventoryShuffleCooldownMinutes = 6;
	public int footstepsCooldownMinutes = 4;
	public int fakeJoinCooldownMinutes = 14;
	public int phantomMiningCooldownMinutes = 8;
	public int skyObjectCooldownMinutes = 16;
	public int animalAttentionCooldownMinutes = 5;
	public int animalDoppelgangerCooldownMinutes = 10;
	public int tameDogCooldownMinutes = 12;
	public int mimicVillagerCooldownMinutes = 9;
	public int creepyVillageCooldownMinutes = 25;
	public int abandonedHomeSignCooldownMinutes = 12;

	public int castleStructureCooldownMinutes = 30;
	public int campsiteStructureCooldownMinutes = 8;
	public int emptyStructureCooldownMinutes = 8;
	public int towerStructureCooldownMinutes = 5;

	public static FakeworldConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				FakeworldConfig config = GSON.fromJson(reader, FakeworldConfig.class);
				if (config != null) {
					config.validate();
					config.save();
					return config;
				}
			} catch (IOException | RuntimeException exception) {
				Fakeworld.LOGGER.warn("Failed to load fakeworld config; using defaults.", exception);
			}
		}

		FakeworldConfig config = new FakeworldConfig();
		config.save();
		return config;
	}

	public void save() {
		validate();
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			Fakeworld.LOGGER.warn("Failed to save fakeworld config.", exception);
		}
	}

	public void validate() {
		if (configVersion < 2) {
			tameDogWeight = 25;
			tameDogCooldownMinutes = 12;
			configVersion = 2;
		}
		if (configVersion < 3) {
			fakeAdvancementWeight = 35;
			fakeAdvancementCooldownMinutes = 7;
			configVersion = 3;
		}
		if (configVersion < 4) {
			fakeJoinWeight = 25;
			fakeJoinCooldownMinutes = 14;
			configVersion = 4;
		}
		if (configVersion < 5) {
			phantomMiningWeight = 35;
			phantomMiningCooldownMinutes = 8;
			configVersion = 5;
		}
		if (configVersion < 6) {
			skyObjectWeight = 24;
			skyObjectCooldownMinutes = 16;
			configVersion = 6;
		}
		if (configVersion < 7) {
			// fakeworldEnabled defaults to true for existing configs
			fakeworldEnabled = true;
			configVersion = 7;
		}

		bleedingTreeChancePercent = clampPercent(bleedingTreeChancePercent);
		ambientEventMinDelaySeconds = Mth.clamp(ambientEventMinDelaySeconds, 30, 3600);
		ambientEventMaxDelaySeconds = Mth.clamp(ambientEventMaxDelaySeconds, ambientEventMinDelaySeconds, 7200);
		ambientQuietWeight = clampWeight(ambientQuietWeight);

		belongingMessageWeight = clampWeight(belongingMessageWeight);
		fakeAdvancementWeight = clampWeight(fakeAdvancementWeight);
		journalHouseWeight = clampWeight(journalHouseWeight);
		stalkerWeight = clampWeight(stalkerWeight);
		darknessWeight = clampWeight(darknessWeight);
		inventoryShuffleWeight = clampWeight(inventoryShuffleWeight);
		footstepsWeight = clampWeight(footstepsWeight);
		fakeJoinWeight = clampWeight(fakeJoinWeight);
		phantomMiningWeight = clampWeight(phantomMiningWeight);
		skyObjectWeight = clampWeight(skyObjectWeight);
		animalAttentionWeight = clampWeight(animalAttentionWeight);
		animalDoppelgangerWeight = clampWeight(animalDoppelgangerWeight);
		tameDogWeight = clampWeight(tameDogWeight);
		mimicVillagerWeight = clampWeight(mimicVillagerWeight);
		creepyVillageWeight = clampWeight(creepyVillageWeight);
		abandonedHomeSignWeight = clampWeight(abandonedHomeSignWeight);

		castleStructureWeight = clampWeight(castleStructureWeight);
		campsiteStructureWeight = clampWeight(campsiteStructureWeight);
		emptyStructureWeight = clampWeight(emptyStructureWeight);
		towerStructureWeight = clampWeight(towerStructureWeight);

		belongingMessageCooldownMinutes = clampCooldown(belongingMessageCooldownMinutes);
		fakeAdvancementCooldownMinutes = clampCooldown(fakeAdvancementCooldownMinutes);
		journalHouseCooldownMinutes = clampCooldown(journalHouseCooldownMinutes);
		stalkerCooldownMinutes = clampCooldown(stalkerCooldownMinutes);
		darknessCooldownMinutes = clampCooldown(darknessCooldownMinutes);
		inventoryShuffleCooldownMinutes = clampCooldown(inventoryShuffleCooldownMinutes);
		footstepsCooldownMinutes = clampCooldown(footstepsCooldownMinutes);
		fakeJoinCooldownMinutes = clampCooldown(fakeJoinCooldownMinutes);
		phantomMiningCooldownMinutes = clampCooldown(phantomMiningCooldownMinutes);
		skyObjectCooldownMinutes = clampCooldown(skyObjectCooldownMinutes);
		animalAttentionCooldownMinutes = clampCooldown(animalAttentionCooldownMinutes);
		animalDoppelgangerCooldownMinutes = clampCooldown(animalDoppelgangerCooldownMinutes);
		tameDogCooldownMinutes = clampCooldown(tameDogCooldownMinutes);
		mimicVillagerCooldownMinutes = clampCooldown(mimicVillagerCooldownMinutes);
		creepyVillageCooldownMinutes = clampCooldown(creepyVillageCooldownMinutes);
		abandonedHomeSignCooldownMinutes = clampCooldown(abandonedHomeSignCooldownMinutes);

		castleStructureCooldownMinutes = clampCooldown(castleStructureCooldownMinutes);
		campsiteStructureCooldownMinutes = clampCooldown(campsiteStructureCooldownMinutes);
		emptyStructureCooldownMinutes = clampCooldown(emptyStructureCooldownMinutes);
		towerStructureCooldownMinutes = clampCooldown(towerStructureCooldownMinutes);
	}

	public int cooldownTicks(int minutes) {
		return Mth.clamp(minutes, 0, 240) * 60 * 20;
	}

	private static int clampPercent(int value) {
		return Mth.clamp(value, 0, 100);
	}

	private static int clampWeight(int value) {
		return Mth.clamp(value, 0, 300);
	}

	private static int clampCooldown(int value) {
		return Mth.clamp(value, 0, 240);
	}
}
