package ru.bulldog.justmap.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.EditGameRulesScreen;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.realms.gui.screen.RealmsGenericErrorScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.TranslatableText;

import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.advancedinfo.AdvancedInfo;
import ru.bulldog.justmap.client.config.ClientConfig;
import ru.bulldog.justmap.event.ChunkUpdateListener;
import ru.bulldog.justmap.map.data.WorldManager;
import ru.bulldog.justmap.map.minimap.Minimap;
import ru.bulldog.justmap.util.DataUtil;
import ru.bulldog.justmap.util.tasks.TaskManager;

public class JustMapClient implements ClientModInitializer {
	public final static MinecraftClient MINECRAFT = MinecraftClient.getInstance();
	public final static ClientConfig CONFIG = ClientConfig.get();
	public final static Minimap MAP = new Minimap();

	private static boolean canMapping = false;	
	private static boolean isOnTitleScreen = true;

	@Override
	public void onInitializeClient() {
		KeyHandler.initKeyBindings();

		ClientChunkEvents.CHUNK_LOAD.register(WorldManager::onChunkLoad);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean isTitle = this.isOnTitleScreen(minecraft.currentScreen);
			if (isTitle && !isOnTitleScreen) {
				JustMapClient.stop();
			}
			isOnTitleScreen = isTitle;
			
			AdvancedInfo.getInstance().updateInfo();
			WorldManager.update();
			KeyHandler.update();

			if (!canMapping()) return;

			DataUtil.update();
			JustMapClient.MAP.update();
			WorldManager.memoryControl();
			ChunkUpdateListener.proceed();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			JustMapClient.stop();
			TaskManager.shutdown();
		});
	}
	
	private static void stop() {
		stopMapping();
		ChunkUpdateListener.stop();
		JustMap.WORKER.execute("Clearing map cache...", WorldManager::close);
	}
	
	public static void startMapping() {
		canMapping = true;
	}
	
	public static void stopMapping() {
		canMapping = false;
	}
	
	public static boolean canMapping() {
		MinecraftClient minecraft = DataUtil.getMinecraft();
		return !isOnTitleScreen && canMapping && minecraft.world != null &&
				(minecraft.getCameraEntity() != null || minecraft.player != null);
	}
	
	private boolean isOnTitleScreen(Screen currentScreen) {
		if (currentScreen == null) return false;
		
		boolean isTitleScreen = false;
		if (currentScreen.getTitle() instanceof TranslatableText) {
			TranslatableText title = (TranslatableText) currentScreen.getTitle();
			isTitleScreen = title.getKey().equals("dataPack.title");
		}
		
		return currentScreen instanceof TitleScreen ||
			   currentScreen instanceof SelectWorldScreen ||
		       currentScreen instanceof MultiplayerScreen ||
		       currentScreen instanceof BackupPromptScreen ||
		       currentScreen instanceof CreateWorldScreen ||
		       currentScreen instanceof EditGameRulesScreen ||
		       currentScreen instanceof EditWorldScreen ||
		       currentScreen instanceof RealmsMainScreen ||
		       currentScreen instanceof RealmsGenericErrorScreen ||
		       isTitleScreen;
	}
}
