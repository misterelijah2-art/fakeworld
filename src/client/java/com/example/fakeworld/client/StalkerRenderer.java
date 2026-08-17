package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import com.example.fakeworld.StalkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class StalkerRenderer extends MobRenderer<StalkerEntity, HumanoidModel<StalkerEntity>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation(Fakeworld.MOD_ID, "textures/entity/stalker.png");

	public StalkerRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.0F);
	}

	@Override
	public ResourceLocation getTextureLocation(StalkerEntity entity) {
		return TEXTURE;
	}
}
