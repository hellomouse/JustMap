package ru.bulldog.justmap.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import ru.bulldog.justmap.client.JustMapClient;
import ru.bulldog.justmap.client.config.ClientParams;
import ru.bulldog.justmap.client.config.ConfigFactory;
import ru.bulldog.justmap.client.widget.DropDownListWidget;
import ru.bulldog.justmap.client.widget.ListElementWidget;
import ru.bulldog.justmap.map.data.Layer;
import ru.bulldog.justmap.map.data.WorldData;
import ru.bulldog.justmap.map.data.WorldKey;
import ru.bulldog.justmap.map.data.WorldManager;
import ru.bulldog.justmap.map.IMap;
import ru.bulldog.justmap.map.MapPlayerManager;
import ru.bulldog.justmap.map.data.ChunkData;
import ru.bulldog.justmap.map.data.RegionData;
import ru.bulldog.justmap.map.icon.PlayerIcon;
import ru.bulldog.justmap.map.icon.WaypointIcon;
import ru.bulldog.justmap.map.waypoint.Waypoint;
import ru.bulldog.justmap.map.waypoint.WaypointKeeper;
import ru.bulldog.justmap.util.Colors;
import ru.bulldog.justmap.util.DataUtil;
import ru.bulldog.justmap.util.Dimension;
import ru.bulldog.justmap.util.LangUtil;
import ru.bulldog.justmap.util.PosUtil;
import ru.bulldog.justmap.util.RuleUtil;
import ru.bulldog.justmap.util.math.MathUtil;

public class Worldmap extends MapScreen implements IMap {

	private final static LiteralText TITLE = new LiteralText("Worldmap");
	
	private static Worldmap worldmap;
	
	public static Worldmap getScreen() {
		if (worldmap == null) {
			worldmap = new Worldmap();
		}
		return worldmap;
	}
	
	private int scaledWidth;
	private int scaledHeight;
	private double centerX;
	private double centerZ;
	private double startX;
	private double startZ;	
	private double endX;
	private double endZ;
	private double shiftW;
	private double shiftH;
	private float imageScale = 1.0F;
	private boolean playerTracking = true;
	private long updateInterval = 50;
	private long updated = 0;
	private int mapLevel = 0;
	private DropDownListWidget mapMenu;
	private WorldData worldData;
	private WorldKey world;
	private BlockPos centerPos;
	private String cursorCoords;
	private Layer mapLayer;
	
	private List<WaypointIcon> waypoints = new ArrayList<>();
	private List<PlayerIcon> players = new ArrayList<>();
	
	private Worldmap() {
		super(TITLE);
	}

	@Override
	public void init() {		
		super.init();
		
		this.paddingTop = 8;
		this.paddingBottom = 8;
		
		this.worldData = WorldManager.getData();
		WorldKey worldKey = WorldManager.getWorldKey();
		if (centerPos == null || !worldKey.equals(world)) {
			this.centerPos = DataUtil.currentPos();
			this.world = worldKey;
		} else if (playerTracking) {
			this.centerPos = DataUtil.currentPos();
		}
		this.cursorCoords = PosUtil.posToString(centerPos);

		this.updateScale();

		if (Dimension.isNether(world.getDimension())) {
			this.mapLayer = Layer.NETHER;
			this.mapLevel = DataUtil.coordY() / mapLayer.height;
		} else {
			this.mapLayer = Layer.SURFACE;
			this.mapLevel = 0;
		}
		
		this.waypoints.clear();
		List<Waypoint> wps = WaypointKeeper.getInstance().getWaypoints(world, true);
		if (wps != null) {
			Stream<Waypoint> stream = wps.stream().filter(wp -> MathUtil.getDistance(centerPos, wp.pos) <= wp.showRange);
			for (Waypoint wp : stream.toArray(Waypoint[]::new)) {
				WaypointIcon waypoint = new WaypointIcon(this, wp);
				this.waypoints.add(waypoint);
			}
		}
		this.players.clear();
		if (RuleUtil.allowPlayerRadar()) {
			List<AbstractClientPlayerEntity> players = this.client.world.getPlayers();
			for (PlayerEntity player : players) {
				if (player == client.player) continue;
				this.players.add(new PlayerIcon(this, player));
			}
		}
		
		this.addMapMenu();
		this.addMapButtons();
	}
	
	private void addMapMenu() {
		LangUtil langUtil = new LangUtil("gui.worldmap");
		this.mapMenu = this.addChild(new DropDownListWidget(25, paddingTop + 2, 100, 22));
		this.mapMenu.addElement(new ListElementWidget(langUtil.getText("add_waypoint"), () -> {
			JustMapClient.MAP.createWaypoint(world, centerPos);
			return true;
		}));
		this.mapMenu.addElement(new ListElementWidget(langUtil.getText("set_map_pos"), () -> {
			client.openScreen(new MapPositionScreen(this));
			return true;
		}));
		this.mapMenu.addElement(new ListElementWidget(langUtil.getText("open_map_config"), () -> {
			client.openScreen(ConfigFactory.getConfigScreen(this));
			return true;
		}));
	}
	
	private void addMapButtons() {
		this.addButton(new ButtonWidget(width - 24, 10, 20, 20, new LiteralText("x"), (b) -> onClose()));		
		this.addButton(new ButtonWidget(width / 2 - 10, height - paddingBottom - 44, 20, 20, new LiteralText("\u2191"), (b) -> moveMap(Direction.NORTH)));
		this.addButton(new ButtonWidget(width / 2 - 10, height - paddingBottom - 22, 20, 20, new LiteralText("\u2193"), (b) -> moveMap(Direction.SOUTH)));
		this.addButton(new ButtonWidget(width / 2 - 32, height - paddingBottom - 32, 20, 20, new LiteralText("\u2190"), (b) -> moveMap(Direction.WEST)));
		this.addButton(new ButtonWidget(width / 2 + 12, height - paddingBottom - 32, 20, 20, new LiteralText("\u2192"), (b) -> moveMap(Direction.EAST)));		
		this.addButton(new ButtonWidget(width - 24, height / 2 - 21, 20, 20, new LiteralText("+"), (b) -> changeScale(-0.25F)));
		this.addButton(new ButtonWidget(width - 24, height / 2 + 1, 20, 20, new LiteralText("-"), (b) -> changeScale(+0.25F)));		
		this.addButton(new ButtonWidget(width - 24, height - paddingBottom - 22, 20, 20, new LiteralText("\u271C"), (b) -> setCenterByPlayer()));
		this.addButton(new ButtonWidget(4, paddingTop + 2, 20, 20, new LiteralText("\u2630"), (b) -> mapMenu.toggleVisible()));
		this.addButton(new ButtonWidget(4, height - paddingBottom - 22, 20, 20, new LiteralText("\u2726"), (b) -> client.openScreen(new WaypointsList(this))));
	}
	
	@Override
	public void renderBackground(MatrixStack matrixStack) {
		fill(matrixStack, x, 0, x + width, height, 0xFF444444);
		this.drawMap();
	}
	
	@Override
	public void renderForeground(MatrixStack matrices) {
		RenderSystem.disableDepthTest();
		int iconSize = (int) (ClientParams.worldmapIconSize / imageScale);
		iconSize = iconSize % 2 != 0 ? iconSize + 1 : iconSize;
		iconSize = MathUtil.clamp(iconSize, 6, (int) (ClientParams.worldmapIconSize * 1.2));
		for (WaypointIcon icon : waypoints) {
			icon.setPosition(
				MathUtil.screenPos(icon.waypoint.pos.getX(), startX, endX, width) - shiftW,
				MathUtil.screenPos(icon.waypoint.pos.getZ(), startZ, endZ, height) - shiftH
			);
			icon.draw(iconSize);
		}
		for (PlayerIcon icon : players) {
			icon.setPosition(
					MathUtil.screenPos(icon.getX(), startX, endX, width) - shiftW,
					MathUtil.screenPos(icon.getZ(), startZ, endZ, height) - shiftH
			);
			icon.draw(matrices, iconSize);
		}
		
		ClientPlayerEntity player = client.player;
		
		double playerX = player.getX();
		double playerZ = player.getZ();
		double arrowX = MathUtil.screenPos(playerX, startX, endX, width) - shiftW;
		double arrowY = MathUtil.screenPos(playerZ, startZ, endZ, height) - shiftH;
		
		MapPlayerManager.getPlayer(player).getIcon().draw(arrowX, arrowY, iconSize, true);
		
		this.drawBorders(paddingTop, paddingBottom);
		drawCenteredString(matrices, client.textRenderer, cursorCoords, width / 2, paddingTop + 4, Colors.WHITE);
		RenderSystem.enableDepthTest();
	}
	
	private void drawMap() {		
		int cornerX = centerPos.getX() - scaledWidth / 2;
		int cornerZ = centerPos.getZ() - scaledHeight / 2;
		
		BlockPos.Mutable currentPos = new BlockPos.Mutable();
		int cY = centerPos.getY();
		
		int picX = 0, picW = 0;
		while(picX < scaledWidth) {
			int cX = cornerX + picX;
			int picY = 0, picH = 0;
			while (picY < scaledHeight) {				
				int cZ = cornerZ + picY;
				
				RegionData region = this.worldData.getRegion(this, currentPos.set(cX, cY, cZ));
				region.swapLayer(mapLayer, mapLevel);
				
				picW = 512;
				picH = 512;
				int imgX = cX - (region.getX() << 9);
				int imgY = cZ - (region.getZ() << 9);
				
				if (picX + picW >= scaledWidth) picW = (int) (scaledWidth - picX);
				if (picY + picH >= scaledHeight) picH = (int) (scaledHeight - picY);
				if (imgX + picW >= 512) picW = 512 - imgX;
				if (imgY + picH >= 512) picH = 512 - imgY;
				
				double scX = picX / imageScale;
				double scY = picY / imageScale;
				
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				region.draw(scX, scY, imgX, imgY, picW, picH, imageScale);
				
				picY += picH > 0 ? picH : 512;
			}
			
			picX += picW > 0 ? picW : 512;
		}
	}
	
	private void calculateShift() {
		this.centerX = (centerPos.getX() >> 4) << 4;
		this.centerZ = (centerPos.getZ() >> 4) << 4;
		this.startX = centerX - scaledWidth / 2;
		this.startZ = centerZ - scaledHeight / 2;
		this.endX = startX + scaledWidth;
		this.endZ = startZ + scaledHeight;
		
		double screenCX = MathUtil.screenPos(centerPos.getX(), startX, endX, width);
		double screenCY = MathUtil.screenPos(centerPos.getZ(), startZ, endZ, height);
		
		this.shiftW = screenCX - width / 2F;
		this.shiftH = screenCY - height / 2F;
	}
	
	public void setCenterByPlayer() {
		this.playerTracking = true;
		this.centerPos = DataUtil.currentPos();  		
		this.calculateShift();
	}
	
	private void updateScale() {
		this.scaledWidth = (int) Math.ceil(width * imageScale);
		this.scaledHeight = (int) Math.ceil(height * imageScale);		
		if (scaledWidth > 2580) {
			this.imageScale = 2580F / width;
			this.updateScale();
			
			return;
		}
		this.calculateShift();		
		this.updateInterval = (long) (imageScale > 1 ? 10 * imageScale : 10);
	}
	
	private void changeScale(float value) {
		this.imageScale = MathUtil.clamp(this.imageScale + value, 0.5F, 3F);
		this.updateScale();
	}
	
	private void moveMap(Direction direction) {
		long time = System.currentTimeMillis();
		if (time - updated < updateInterval) return;		
		switch (direction) {
			case NORTH:
				this.centerPos = centerPos.add(0, 0, -16);
				break;
			case SOUTH:
				this.centerPos = centerPos.add(0, 0, 16);
				break;
			case EAST:
				this.centerPos = centerPos.add(16, 0, 0);
				break;
			case WEST:
				this.centerPos = centerPos.add(-16, 0, 0);
				break;
			default: break;
		}		
		this.calculateShift();
		this.playerTracking = false;
		this.updated = time;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch(keyCode) {
			case GLFW.GLFW_KEY_W:
			case GLFW.GLFW_KEY_UP:
				this.moveMap(Direction.NORTH);
		  		return true;
		  	case GLFW.GLFW_KEY_S:
		  	case GLFW.GLFW_KEY_DOWN:
		  		this.moveMap(Direction.SOUTH);
		  		return true;
		  	case GLFW.GLFW_KEY_A:
		  	case GLFW.GLFW_KEY_LEFT:
		  		this.moveMap(Direction.WEST);
		  		return true;
		  	case GLFW.GLFW_KEY_D:
		  	case GLFW.GLFW_KEY_RIGHT:
		  		this.moveMap(Direction.EAST);
		  		return true;
		  	case GLFW.GLFW_KEY_MINUS:
		  	case GLFW.GLFW_KEY_KP_SUBTRACT:
		  		this.changeScale(0.25F);
		  		return true;
		  	case GLFW.GLFW_KEY_EQUAL:
		  	case GLFW.GLFW_KEY_KP_ADD:
		  		this.changeScale(-0.25F);
		  		return true;
		  	case GLFW.GLFW_KEY_X:
		  		this.setCenterByPlayer();
		  		return true;
		  	case GLFW.GLFW_KEY_M:
		  		this.onClose();
		  		return true;
		  	default:
		  		return super.keyPressed(keyCode, scanCode, modifiers);
		}
	}
	
	@Override
	public boolean mouseDragged(double d, double e, int i, double f, double g) {
		if (super.mouseDragged(d, e, i, f, g)) return true;
		
		if (i == 0) {
			long time = System.currentTimeMillis();
			if (time - updated < updateInterval) return true;
			
			int x = centerPos.getX();
			int y = centerPos.getY();
			int z = centerPos.getZ();
			
			x -= Math.round(2 * f * imageScale);
			z -= Math.round(2 * g * imageScale);
			
			this.centerPos = new BlockPos(x, y, z);			
			this.calculateShift();
			this.playerTracking = false;
			this.updated = time;
		
			return true;
		}
		
		return false;
	}
	
	private int pixelToPos(double x, int cx, double range, double scaledRange) {
		double x1 = cx - scaledRange / 2;
		double x2 = x1 + scaledRange;
		
		return MathUtil.worldPos(x, x1, x2, range);
	}
	
	private BlockPos cursorBlockPos(double x, double y) {
		
		int posX = this.pixelToPos(x, centerPos.getX(), width, scaledWidth);
		int posZ = this.pixelToPos(y, centerPos.getZ(), height, scaledHeight);
		
		int chunkX = posX >> 4;
		int chunkZ = posZ >> 4;
		
		ChunkData mapChunk = this.worldData.getChunk(chunkX, chunkZ);
		
		int cx = posX - (chunkX << 4);
		int cz = posZ - (chunkZ << 4);
		
		int posY = mapChunk.getChunkLevel(mapLayer, mapLevel).sampleHeightmap(cx, cz);
		posY = posY == -1 ? centerPos.getY() : posY;
		
		return new BlockPos(posX, posY, posZ);
	}
	
	@Override
	public void mouseMoved(double d, double e) {		
		this.cursorCoords = PosUtil.posToString(cursorBlockPos(d, e));
	}
	
	private int clicks = 0;
	private long clicked = 0;
	
	@Override
	public boolean mouseReleased(double d, double e, int i) {
		if (super.mouseReleased(d, e, i)) return true; 
		
		if (i == 0) {
			long time = System.currentTimeMillis();
			if (time - clicked > 300) clicks = 0;
			
			if (++clicks == 2) {			
				JustMapClient.MAP.createWaypoint(world, cursorBlockPos(d, e));
				
				clicked = 0;
				clicks = 0;
			} else {
				clicked = time;
			}
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean isRotated() {
		return false;
	}
	
	@Override
	public boolean mouseScrolled(double d, double e, double f) {
		boolean scrolled = super.mouseScrolled(d, e, f);
		this.changeScale(f > 0 ? -0.25F : 0.25F);
		return scrolled;
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public int getScaledWidth() {
		return this.scaledWidth;
	}

	@Override
	public int getScaledHeight() {
		return this.scaledHeight;
	}

	@Override
	public float getScale() {
		return this.getScale();
	}

	@Override
	public Layer getLayer() {
		return this.mapLayer;
	}

	@Override
	public int getLevel() {
		return this.mapLevel;
	}

	@Override
	public BlockPos getCenter() {
		return this.centerPos;
	}
}
