package com.example.fakeworld.mixin;

import com.example.fakeworld.Fakeworld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Redirect(
			method = "getPlayerForLogin",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;")
	)
	private ServerLevel fakeworld$createNewPlayerInFakeOverworld(MinecraftServer server) {
		return fakeworld$getFakeOverworld(server);
	}

	@Redirect(
			method = {"placeNewPlayer", "respawn"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;")
	)
	private ServerLevel fakeworld$useFakeOverworldWhenVanillaAsksForOverworld(MinecraftServer server, ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) {
			return fakeworld$getFakeOverworld(server);
		}

		return server.getLevel(dimension);
	}

	@Redirect(
			method = {"placeNewPlayer", "respawn"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;")
	)
	private ServerLevel fakeworld$useFakeOverworldForVanillaFallback(MinecraftServer server) {
		return fakeworld$getFakeOverworld(server);
	}

	private static ServerLevel fakeworld$getFakeOverworld(MinecraftServer server) {
		ServerLevel fakeOverworld = server.getLevel(Fakeworld.FAKE_OVERWORLD);
		return fakeOverworld != null ? fakeOverworld : server.overworld();
	}
}
