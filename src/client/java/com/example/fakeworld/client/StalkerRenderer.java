package com.example.fakeworld.client;

import com.example.fakeworld.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {

	private static final ResourceLocation TEXTURE =
			new ResourceLocation("fakeworld", "textures/entity/stalker.png");

	public StalkerRenderer(EntityRendererProvider.Context context) {
		super(context, new StalkerModel());
	}

	@Override
	public ResourceLocation getTextureLocation(StalkerEntity entity) {
		return TEXTURE;
	}
}
