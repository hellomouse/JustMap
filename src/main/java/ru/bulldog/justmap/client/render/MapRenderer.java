package ru.bulldog.justmap.client.render;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import ru.bulldog.justmap.client.JustMapClient;
import ru.bulldog.justmap.client.config.ClientParams;
import ru.bulldog.justmap.map.DirectionArrow;
import ru.bulldog.justmap.map.icon.EntityIcon;
import ru.bulldog.justmap.map.icon.PlayerIcon;
import ru.bulldog.justmap.map.icon.WaypointIcon;
//import ru.bulldog.justmap.map.minimap.ChunkGrid;
import ru.bulldog.justmap.map.minimap.MapPosition;
import ru.bulldog.justmap.map.minimap.MapSkin;
import ru.bulldog.justmap.map.minimap.MapText;
import ru.bulldog.justmap.map.minimap.Minimap;
import ru.bulldog.justmap.map.minimap.TextManager;
import ru.bulldog.justmap.util.DrawHelper.TextAlignment;
import ru.bulldog.justmap.util.Colors;
import ru.bulldog.justmap.util.DrawHelper;
import ru.bulldog.justmap.util.PosUtil;
import ru.bulldog.justmap.util.math.Line;
import ru.bulldog.justmap.util.math.Line.Point;
import ru.bulldog.justmap.util.math.MathUtil;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;

@Environment(EnvType.CLIENT)
public class MapRenderer {
	
	private static MapRenderer instance;
	
	protected MapPosition mapPosition;
	protected int border = 2;
	
	private int offset;
	private int posX, posY;
	private int mapX, mapY;
	private int mapW, mapH;
	private int imgX, imgY;
	private int imgW, imgH;
	private float rotation;
	private int lastX;
	private int lastZ;

	private final Minimap minimap;
	
	private MapTexture mapTexture;
	private Tessellator tessellator = Tessellator.getInstance();
	private BufferBuilder builder = tessellator.getBuffer();
	
	private TextManager textManager;
	
	private MapText dirN = new MapText(TextAlignment.CENTER, "N");
	private MapText dirS = new MapText(TextAlignment.CENTER, "S");
	private MapText dirE = new MapText(TextAlignment.CENTER, "E");
	private MapText dirW = new MapText(TextAlignment.CENTER, "W");
	
	private final MinecraftClient client = MinecraftClient.getInstance();
	
	private MapSkin mapSkin;
	//private ChunkGrid chunkGrid;
	
	public static MapRenderer getInstance() {
		if (instance == null) {
			instance = new MapRenderer();
		}
		
		return instance;
	}
	
	private MapRenderer() {
		this.minimap = JustMapClient.MAP;
		this.offset = ClientParams.positionOffset;
		this.mapPosition = ClientParams.mapPosition;
		this.textManager = this.minimap.getTextManager();
	}
	
	public int getX() {
		return this.posX;
	}
	
	public int getY() {
		return this.posY;
	}
	
	public int getBorder() {
		return this.border;
	}
	
	public void updateParams() {		
		int winW = client.getWindow().getScaledWidth();
		int winH = client.getWindow().getScaledHeight();
		
		this.offset = ClientParams.positionOffset;
		this.mapPosition = ClientParams.mapPosition;
		
		this.mapW = minimap.getWidth();
		this.mapH = minimap.getHeight();
		this.posX = offset;
		this.posY = offset;
		this.mapX = posX + border;
		this.mapY = posY + border;
		
		this.rotation = client.player.headYaw;
		
		TextManager.TextPosition textPos = TextManager.TextPosition.UNDER;
		
		switch (mapPosition) {
			case TOP_LEFT:
				break;
			case TOP_CENTER:
				this.mapX = winW / 2 - mapW / 2;
				this.posX = mapX - border;
				break;
			case TOP_RIGHT:
				this.mapX = winW - offset - mapW - border;
				this.posX = mapX - border;
				break;
			case MIDDLE_RIGHT:
				this.mapX = winW - offset - mapW - border;
				this.mapY = winH / 2 - mapH / 2;
				this.posX = mapX - border;
				this.posY = mapY - border;
				break;
			case MIDDLE_LEFT:
				this.mapY = winH / 2 - mapH / 2;
				this.posY = mapY - border;
				break;
			case BOTTOM_LEFT:
				textPos = TextManager.TextPosition.ABOVE;
				this.mapY = winH - offset - mapH - border;
				this.posY = mapY - border;
				break;
			case BOTTOM_RIGHT:
				textPos = TextManager.TextPosition.ABOVE;
				this.mapX = winW - offset - mapW - border;
				this.posX = mapX - border;
				this.mapY = winH - offset - mapH - border;
				this.posY = mapY - border;
				break;
		}
		
		if (ClientParams.rotateMap) {
			this.imgW = (int) (mapW * 1.42);
			this.imgH = (int) (mapH * 1.42);
			this.imgX = mapX - (imgW - mapW) / 2;
			this.imgY = mapY - (imgH - mapH) / 2;
		} else {
			this.imgW = this.mapW;
			this.imgH = this.mapH;
			this.imgX = this.mapX;
			this.imgY = this.mapY;
		}
		
		this.textManager.setPosition(
			mapX, mapY + (textPos == TextManager.TextPosition.UNDER && minimap.isMapVisible() ?
				mapH + border + 3 :
				-(border + 3))
		);
		this.textManager.setDirection(textPos);
		this.textManager.setSpacing(12);
		
		int centerX = mapX + mapW / 2;
		int centerY = mapY + mapH / 2;
		int mapR = mapX + mapW;
		int mapB = mapY + mapH;
		
		Point center = new Point(centerX, centerY);
		Point pointN = new Point(centerX, mapY);
		Point pointS = new Point(centerX, mapB);
		Point pointE = new Point(mapR, centerY);
		Point pointW = new Point(mapX, centerY);
		
		if (ClientParams.rotateMap) {
			float rotate = MathUtil.correctAngle(rotation) + 180;
			double angle = Math.toRadians(-rotate);
			
			Line radius = new Line(center, pointN);
			Line corner = new Line(center, new Point(mapX, mapY));
			
			radius.add(corner.lenght() - radius.lenght());
			int len = radius.lenght();
			
			pointN.y = centerY - len;
			pointS.y = centerY + len;
			pointE.x = centerX + len;
			pointW.x = centerX - len;
			
			calculatePos(center, pointN, mapR, mapB, angle);
			calculatePos(center, pointS, mapR, mapB, angle);
			calculatePos(center, pointE, mapR, mapB, angle);
			calculatePos(center, pointW, mapR, mapB, angle);
		}
		
		this.textManager.add(dirN, pointN.x, pointN.y - 5);
		this.textManager.add(dirS, pointS.x, pointS.y - 5);
		this.textManager.add(dirE, pointE.x, pointE.y - 5);
		this.textManager.add(dirW, pointW.x, pointW.y - 5);
		
		if (ClientParams.useSkins) {
			this.mapSkin = MapSkin.getSkin(ClientParams.currentSkin);
			
			this.border = mapSkin.resizable ?
						  (int) (mapW * ((float)(mapSkin.border) / mapSkin.getWidth())) :
						  mapSkin.border;
		}
	}
	
	private void calculatePos(Point center, Point dir, int mr, int mb, double angle) {		
		int posX = (int) (center.x + (dir.x - center.x) * Math.cos(angle) - (dir.y - center.y) * Math.sin(angle));
		int posY = (int) (center.y + (dir.y - center.y) * Math.cos(angle) + (dir.x - center.x) * Math.sin(angle));
		posX = MathUtil.clamp(posX, mapX, mr);
		posY = MathUtil.clamp(posY, mapY, mb);
		
		dir.x = posX; dir.y = posY;
	}
	
	private void prepareTexture() {
		int textureSize = minimap.getScaledSize();
		if (mapTexture == null || mapTexture.getWidth() != textureSize || mapTexture.getHeight() != textureSize) {
			if (mapTexture != null) this.mapTexture.close();			
			this.mapTexture = new MapTexture(textureSize, textureSize);
			//this.chunkGrid = new ChunkGrid(mapX, mapY, mapW, mapH);
		}		
		if (minimap.changed) {
			this.mapTexture.copyImage(minimap.getImage());
			this.mapTexture.upload();
			this.lastX = minimap.getLasX();
			this.lastZ = minimap.getLastZ();
			this.minimap.changed = false;
		}
		this.backingImage.copyFrom(minimap.getImage());
		this.texture.upload();
	}
	
	public void draw() {
		if (!minimap.isMapVisible() || client.player == null) {
			return;
		}
		
		this.updateParams();
		
		int winH = client.getWindow().getFramebufferHeight();
		double scale = client.getWindow().getScaleFactor();
		
		int scaledX = (int) (mapX * scale);
		int scaledY = (int) (winH - (mapY + mapH) * scale);
		int scaledW = (int) (mapW * scale);
		int scaledH = (int) (mapH * scale);
		
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableDepthTest();
		
		if (ClientParams.useSkins) {
			mapSkin.draw(posX, posY, mapW + border * 2);
		}
		
		if (this.mapTexture == null || this.minimap.changed) {
			this.prepareTexture();
		}
		
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(scaledX, scaledY, scaledW, scaledH);
		
		float mult = 1 / minimap.getScale();		
		float offX = (float) (PosUtil.doubleCoordX() - this.lastX) * mult;
		float offZ = (float) (PosUtil.doubleCoordZ() - this.lastZ) * mult;
		
		RenderSystem.pushMatrix();
		if (ClientParams.rotateMap) {
			float moveX = imgX + imgW / 2;
			float moveY = imgY + imgH / 2;
			RenderSystem.translatef(moveX, moveY, 0.0F);
			RenderSystem.rotatef(-rotation + 180, 0, 0, 1.0F);
			RenderSystem.translatef(-moveX, -moveY, 0.0F);
		}
		RenderSystem.translatef(-offX, -offZ, 0.0F);
		
		this.drawMap();

		//if (ClientParams.showGrid) {
		//	this.chunkGrid.update(lastX, lastZ);
		//	this.chunkGrid.draw();
		//}
		if (Minimap.allowEntityRadar()) {
			if (Minimap.allowPlayerRadar()) {
				for (PlayerIcon player : minimap.getPlayerIcons()) {
					player.draw(mapX, mapY, rotation);
				}
			}
			if (Minimap.allowCreatureRadar() || Minimap.allowHostileRadar()) {
				for (EntityIcon entity : minimap.getEntities()) {
					entity.draw(mapX, mapY, rotation);
				}
			}
		}		
		RenderSystem.popMatrix();
		
		DrawHelper.DRAWER.drawRightAlignedString(
				client.textRenderer, Float.toString(minimap.getScale()),
				mapX + mapW - 3, mapY + mapH - 10, Colors.WHITE);
		
		for (WaypointIcon waypoint : minimap.getWaypoints()) {
			if (!waypoint.isHidden()) {
				waypoint.draw(mapX, mapY, rotation);
			}
		}
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		
		int arrowX = mapX + mapW / 2;
		int arrowY = mapY + mapH / 2;
		
		DirectionArrow.draw(arrowX, arrowY, ClientParams.rotateMap ? 180 : rotation);
		
		this.textManager.draw();
		
		RenderSystem.enableDepthTest();
	}
	
	private void drawMap() {
		RenderSystem.bindTexture(mapTexture.getId());
		if (minimap.getScale() >= 0.75) {
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		} else {
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		}
		
		this.builder.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);		
		this.builder.vertex(imgX, imgY - 4, 1.0).texture(0.0F, 0.0F).next();
		this.builder.vertex(imgX, imgY + imgH + 4, 1.0).texture(0.0F, 1.0F).next();
		this.builder.vertex(imgX + imgW + 4, imgY + imgH + 4, 1.0).texture(1.0F, 1.0F).next();
		this.builder.vertex(imgX + imgW + 4, imgY - 4, 1.0).texture(1.0F, 0.0F).next();
		
		this.tessellator.draw();
	}
}