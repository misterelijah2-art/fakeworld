package com.example.fakeworld.client;

import com.example.fakeworld.StalkerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class StalkerModel extends GeoModel<StalkerEntity> {

	private static final ResourceLocation MODEL =
			new ResourceLocation("fakeworld", "geo/stalker.geo.json");
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("fakeworld", "textures/entity/stalker.png");
	private static final ResourceLocation ANIMATIONS =
			new ResourceLocation("fakeworld", "animations/stalker.animation.json");

	@Override
	public ResourceLocation getModelResource(StalkerEntity entity) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(StalkerEntity entity) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(StalkerEntity entity) {
		return ANIMATIONS;
	}
}
