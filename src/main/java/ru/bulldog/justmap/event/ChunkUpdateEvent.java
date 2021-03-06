package ru.bulldog.justmap.event;

import net.minecraft.world.chunk.WorldChunk;

import ru.bulldog.justmap.map.data.ChunkData;
import ru.bulldog.justmap.map.data.Layer;

public class ChunkUpdateEvent {
	public final WorldChunk worldChunk;
	public final ChunkData mapChunk;
	public final Layer layer;
	public final int level;
	public final boolean update;
	
	public ChunkUpdateEvent(WorldChunk worldChunk, ChunkData mapChunk, Layer layer, int level, boolean update) {
		this.worldChunk = worldChunk;
		this.mapChunk = mapChunk;
		this.layer = layer;
		this.level = level;
		this.update = update;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ChunkUpdateEvent)) return false;		
		ChunkUpdateEvent event = (ChunkUpdateEvent) obj;
		return this.chunkEquals(event.worldChunk) &&
			   this.layer.equals(event.layer) &&
			   this.level == event.level;
	}
	
	private boolean chunkEquals(WorldChunk chunk) {
		return this.worldChunk.getPos().equals(chunk.getPos());
	}
}
