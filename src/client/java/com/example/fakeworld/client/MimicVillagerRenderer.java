package com.example.fakeworld.client;

import com.example.fakeworld.MimicVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MimicVillagerRenderer extends MobRenderer<MimicVillagerEntity, VillagerModel<MimicVillagerEntity>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

	public MimicVillagerRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
	}

	@Override
	public ResourceLocation getTextureLocation(MimicVillagerEntity entity) {
		return TEXTURE;
	}
}
