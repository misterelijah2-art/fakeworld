package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class FakeworldModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FakeworldModMenu::createConfigScreen;
	}

	private static Screen createConfigScreen(Screen parent) {
		Fakeworld.CONFIG.validate();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("text.fakeworld.config.title"))
				.setSavingRunnable(Fakeworld.CONFIG::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("text.fakeworld.config.category.general"));
		general.addEntry(entries.startBooleanToggle(Component.translatable("text.fakeworld.config.ambientDirectorEnabled"), Fakeworld.CONFIG.ambientDirectorEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(value -> Fakeworld.CONFIG.ambientDirectorEnabled = value)
				.build());
		general.addEntry(entries.startBooleanToggle(Component.translatable("text.fakeworld.config.ambientDebugMessages"), Fakeworld.CONFIG.ambientDebugMessages)
				.setDefaultValue(false)
				.setSaveConsumer(value -> Fakeworld.CONFIG.ambientDebugMessages = value)
				.build());
		general.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config.bleedingTreeChancePercent"), Fakeworld.CONFIG.bleedingTreeChancePercent, 0, 100)
				.setDefaultValue(10)
				.setTextGetter(value -> Component.literal(value + "%"))
				.setSaveConsumer(value -> Fakeworld.CONFIG.bleedingTreeChancePercent = value)
				.build());
		general.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config.ambientEventMinDelaySeconds"), Fakeworld.CONFIG.ambientEventMinDelaySeconds, 30, 3600)
				.setDefaultValue(45)
				.setSaveConsumer(value -> Fakeworld.CONFIG.ambientEventMinDelaySeconds = value)
				.build());
		general.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config.ambientEventMaxDelaySeconds"), Fakeworld.CONFIG.ambientEventMaxDelaySeconds, 30, 7200)
				.setDefaultValue(140)
				.setSaveConsumer(value -> Fakeworld.CONFIG.ambientEventMaxDelaySeconds = value)
				.build());
		general.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config.ambientQuietWeight"), Fakeworld.CONFIG.ambientQuietWeight, 0, 300)
				.setDefaultValue(160)
				.setSaveConsumer(value -> Fakeworld.CONFIG.ambientQuietWeight = value)
				.build());

		ConfigCategory weights = builder.getOrCreateCategory(Component.translatable("text.fakeworld.config.category.weights"));
		addWeight(entries, weights, "belongingMessageWeight", Fakeworld.CONFIG.belongingMessageWeight, 120, value -> Fakeworld.CONFIG.belongingMessageWeight = value);
		addWeight(entries, weights, "fakeAdvancementWeight", Fakeworld.CONFIG.fakeAdvancementWeight, 35, value -> Fakeworld.CONFIG.fakeAdvancementWeight = value);
		addWeight(entries, weights, "journalHouseWeight", Fakeworld.CONFIG.journalHouseWeight, 30, value -> Fakeworld.CONFIG.journalHouseWeight = value);
		addWeight(entries, weights, "stalkerWeight", Fakeworld.CONFIG.stalkerWeight, 50, value -> Fakeworld.CONFIG.stalkerWeight = value);
		addWeight(entries, weights, "darknessWeight", Fakeworld.CONFIG.darknessWeight, 60, value -> Fakeworld.CONFIG.darknessWeight = value);
		addWeight(entries, weights, "inventoryShuffleWeight", Fakeworld.CONFIG.inventoryShuffleWeight, 40, value -> Fakeworld.CONFIG.inventoryShuffleWeight = value);
		addWeight(entries, weights, "footstepsWeight", Fakeworld.CONFIG.footstepsWeight, 60, value -> Fakeworld.CONFIG.footstepsWeight = value);
		addWeight(entries, weights, "fakeJoinWeight", Fakeworld.CONFIG.fakeJoinWeight, 25, value -> Fakeworld.CONFIG.fakeJoinWeight = value);
		addWeight(entries, weights, "phantomMiningWeight", Fakeworld.CONFIG.phantomMiningWeight, 35, value -> Fakeworld.CONFIG.phantomMiningWeight = value);
		addWeight(entries, weights, "skyObjectWeight", Fakeworld.CONFIG.skyObjectWeight, 24, value -> Fakeworld.CONFIG.skyObjectWeight = value);
		addWeight(entries, weights, "animalAttentionWeight", Fakeworld.CONFIG.animalAttentionWeight, 50, value -> Fakeworld.CONFIG.animalAttentionWeight = value);
		addWeight(entries, weights, "animalDoppelgangerWeight", Fakeworld.CONFIG.animalDoppelgangerWeight, 30, value -> Fakeworld.CONFIG.animalDoppelgangerWeight = value);
		addWeight(entries, weights, "tameDogWeight", Fakeworld.CONFIG.tameDogWeight, 25, value -> Fakeworld.CONFIG.tameDogWeight = value);
		addWeight(entries, weights, "mimicVillagerWeight", Fakeworld.CONFIG.mimicVillagerWeight, 35, value -> Fakeworld.CONFIG.mimicVillagerWeight = value);
		addWeight(entries, weights, "creepyVillageWeight", Fakeworld.CONFIG.creepyVillageWeight, 18, value -> Fakeworld.CONFIG.creepyVillageWeight = value);
		addWeight(entries, weights, "abandonedHomeSignWeight", Fakeworld.CONFIG.abandonedHomeSignWeight, 14, value -> Fakeworld.CONFIG.abandonedHomeSignWeight = value);
		addWeight(entries, weights, "castleStructureWeight", Fakeworld.CONFIG.castleStructureWeight, 10, value -> Fakeworld.CONFIG.castleStructureWeight = value);
		addWeight(entries, weights, "campsiteStructureWeight", Fakeworld.CONFIG.campsiteStructureWeight, 60, value -> Fakeworld.CONFIG.campsiteStructureWeight = value);
		addWeight(entries, weights, "emptyStructureWeight", Fakeworld.CONFIG.emptyStructureWeight, 60, value -> Fakeworld.CONFIG.emptyStructureWeight = value);
		addWeight(entries, weights, "towerStructureWeight", Fakeworld.CONFIG.towerStructureWeight, 100, value -> Fakeworld.CONFIG.towerStructureWeight = value);

		ConfigCategory cooldowns = builder.getOrCreateCategory(Component.translatable("text.fakeworld.config.category.cooldowns"));
		addCooldown(entries, cooldowns, "belongingMessageCooldownMinutes", Fakeworld.CONFIG.belongingMessageCooldownMinutes, 20, value -> Fakeworld.CONFIG.belongingMessageCooldownMinutes = value);
		addCooldown(entries, cooldowns, "fakeAdvancementCooldownMinutes", Fakeworld.CONFIG.fakeAdvancementCooldownMinutes, 7, value -> Fakeworld.CONFIG.fakeAdvancementCooldownMinutes = value);
		addCooldown(entries, cooldowns, "journalHouseCooldownMinutes", Fakeworld.CONFIG.journalHouseCooldownMinutes, 20, value -> Fakeworld.CONFIG.journalHouseCooldownMinutes = value);
		addCooldown(entries, cooldowns, "stalkerCooldownMinutes", Fakeworld.CONFIG.stalkerCooldownMinutes, 5, value -> Fakeworld.CONFIG.stalkerCooldownMinutes = value);
		addCooldown(entries, cooldowns, "darknessCooldownMinutes", Fakeworld.CONFIG.darknessCooldownMinutes, 6, value -> Fakeworld.CONFIG.darknessCooldownMinutes = value);
		addCooldown(entries, cooldowns, "inventoryShuffleCooldownMinutes", Fakeworld.CONFIG.inventoryShuffleCooldownMinutes, 6, value -> Fakeworld.CONFIG.inventoryShuffleCooldownMinutes = value);
		addCooldown(entries, cooldowns, "footstepsCooldownMinutes", Fakeworld.CONFIG.footstepsCooldownMinutes, 4, value -> Fakeworld.CONFIG.footstepsCooldownMinutes = value);
		addCooldown(entries, cooldowns, "fakeJoinCooldownMinutes", Fakeworld.CONFIG.fakeJoinCooldownMinutes, 14, value -> Fakeworld.CONFIG.fakeJoinCooldownMinutes = value);
		addCooldown(entries, cooldowns, "phantomMiningCooldownMinutes", Fakeworld.CONFIG.phantomMiningCooldownMinutes, 8, value -> Fakeworld.CONFIG.phantomMiningCooldownMinutes = value);
		addCooldown(entries, cooldowns, "skyObjectCooldownMinutes", Fakeworld.CONFIG.skyObjectCooldownMinutes, 16, value -> Fakeworld.CONFIG.skyObjectCooldownMinutes = value);
		addCooldown(entries, cooldowns, "animalAttentionCooldownMinutes", Fakeworld.CONFIG.animalAttentionCooldownMinutes, 5, value -> Fakeworld.CONFIG.animalAttentionCooldownMinutes = value);
		addCooldown(entries, cooldowns, "animalDoppelgangerCooldownMinutes", Fakeworld.CONFIG.animalDoppelgangerCooldownMinutes, 10, value -> Fakeworld.CONFIG.animalDoppelgangerCooldownMinutes = value);
		addCooldown(entries, cooldowns, "tameDogCooldownMinutes", Fakeworld.CONFIG.tameDogCooldownMinutes, 12, value -> Fakeworld.CONFIG.tameDogCooldownMinutes = value);
		addCooldown(entries, cooldowns, "mimicVillagerCooldownMinutes", Fakeworld.CONFIG.mimicVillagerCooldownMinutes, 9, value -> Fakeworld.CONFIG.mimicVillagerCooldownMinutes = value);
		addCooldown(entries, cooldowns, "creepyVillageCooldownMinutes", Fakeworld.CONFIG.creepyVillageCooldownMinutes, 25, value -> Fakeworld.CONFIG.creepyVillageCooldownMinutes = value);
		addCooldown(entries, cooldowns, "abandonedHomeSignCooldownMinutes", Fakeworld.CONFIG.abandonedHomeSignCooldownMinutes, 12, value -> Fakeworld.CONFIG.abandonedHomeSignCooldownMinutes = value);
		addCooldown(entries, cooldowns, "castleStructureCooldownMinutes", Fakeworld.CONFIG.castleStructureCooldownMinutes, 30, value -> Fakeworld.CONFIG.castleStructureCooldownMinutes = value);
		addCooldown(entries, cooldowns, "campsiteStructureCooldownMinutes", Fakeworld.CONFIG.campsiteStructureCooldownMinutes, 8, value -> Fakeworld.CONFIG.campsiteStructureCooldownMinutes = value);
		addCooldown(entries, cooldowns, "emptyStructureCooldownMinutes", Fakeworld.CONFIG.emptyStructureCooldownMinutes, 8, value -> Fakeworld.CONFIG.emptyStructureCooldownMinutes = value);
		addCooldown(entries, cooldowns, "towerStructureCooldownMinutes", Fakeworld.CONFIG.towerStructureCooldownMinutes, 5, value -> Fakeworld.CONFIG.towerStructureCooldownMinutes = value);

		return builder.build();
	}

	private static void addWeight(ConfigEntryBuilder entries, ConfigCategory category, String key, int value, int defaultValue, IntConsumer saveConsumer) {
		category.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config." + key), value, 0, 300)
				.setDefaultValue(defaultValue)
				.setSaveConsumer(saveConsumer::accept)
				.build());
	}

	private static void addCooldown(ConfigEntryBuilder entries, ConfigCategory category, String key, int value, int defaultValue, IntConsumer saveConsumer) {
		category.addEntry(entries.startIntSlider(Component.translatable("text.fakeworld.config." + key), value, 0, 240)
				.setDefaultValue(defaultValue)
				.setTextGetter(minutes -> Component.literal(minutes + " min"))
				.setSaveConsumer(saveConsumer::accept)
				.build());
	}

	@FunctionalInterface
	private interface IntConsumer {
		void accept(int value);
	}
}
