/*
 * Copyright (C) 2011-2014 lishid.  All rights reserved.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.lishid.orebfuscator.obfuscation;

import java.io.File;
import java.util.zip.Deflater;

import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import com.lishid.orebfuscator.OrebfuscatorConfig;
import com.lishid.orebfuscator.cache.ObfuscatedCachedChunk;
import com.lishid.orebfuscator.internal.IPacket51;
import com.lishid.orebfuscator.internal.IPacket56;
import com.lishid.orebfuscator.internal.InternalAccessor;

public class Calculations {
	public static final ThreadLocal<byte[]> buffer = new ThreadLocal<byte[]>() {
		@Override
		protected byte[] initialValue() {
			return new byte[65536];
		}
	};

	public static final ThreadLocal<Deflater> localDeflater = new ThreadLocal<Deflater>() {
		@Override
		protected Deflater initialValue() {
			// Not used from orebfuscator thread, best speed instead
			return new Deflater(Deflater.BEST_SPEED);
		}
	};

	public static void Obfuscate(Object packet, Player player) {
		IPacket51 packet51 = InternalAccessor.Instance.newPacket51();
		packet51.setPacket(packet);
		Calculations.Obfuscate(packet51, player);
	}

	public static void Obfuscate(IPacket56 packet, Player player) {
		if (packet.getFieldData(packet.getOutputBuffer()) != null) {
			return;
		}

		ChunkInfo[] infos = getInfo(packet, player);

		for (int chunkNum = 0; chunkNum < infos.length; chunkNum++) {
			// Create an info objects
			ChunkInfo info = infos[chunkNum];
			info.buffer = buffer.get();
			ComputeChunkInfoAndObfuscate(info, (byte[]) packet.getFieldData(packet.getBuildBuffer()));
		}
	}

	public static void Obfuscate(IPacket51 packet, Player player) {
		Obfuscate(packet, player, true);
	}

	public static void Obfuscate(IPacket51 packet, Player player, boolean needCompression) {
		ChunkInfo info = getInfo(packet, player);
		info.buffer = buffer.get();

		if (info.chunkMask == 0 && info.extraMask == 0) {
			return;
		}

		if (info.buffer == null || info.buffer.length == 0) {
			return;
		}

		ComputeChunkInfoAndObfuscate(info, packet.getBuffer());

		if (needCompression) {
			Deflater deflater = localDeflater.get();
			packet.compress(deflater);
		}
	}

	public static ChunkInfo[] getInfo(IPacket56 packet, Player player) {
		ChunkInfo[] infos = new ChunkInfo[packet.getPacketChunkNumber()];

		int dataStartIndex = 0;

		int[] x = packet.getX();
		int[] z = packet.getZ();

		byte[][] inflatedBuffers = (byte[][]) packet.getFieldData(packet.getInflatedBuffers());

		int[] chunkMask = packet.getChunkMask();
		int[] extraMask = packet.getExtraMask();

		byte[] buildBuffer = (byte[]) packet.getFieldData(packet.getBuildBuffer());

		// Check for spigot and fix accordingly
		if (buildBuffer.length == 0) {
			int finalBufferSize = 0;
			for (int i = 0; i < inflatedBuffers.length; i++) {
				finalBufferSize += inflatedBuffers[i].length;
			}

			buildBuffer = new byte[finalBufferSize];
			int bufferLocation = 0;
			for (int i = 0; i < inflatedBuffers.length; i++) {
				System.arraycopy(inflatedBuffers[i], 0, buildBuffer, bufferLocation, inflatedBuffers[i].length);
				bufferLocation += inflatedBuffers[i].length;
			}

			packet.setFieldData(packet.getBuildBuffer(), buildBuffer);
		}

		for (int chunkNum = 0; chunkNum < packet.getPacketChunkNumber(); chunkNum++) {
			// Create an info objects
			ChunkInfo info = new ChunkInfo();
			infos[chunkNum] = info;
			info.world = player.getWorld();
			info.player = player;
			info.chunkX = x[chunkNum];
			info.chunkZ = z[chunkNum];
			info.chunkMask = chunkMask[chunkNum];
			info.extraMask = extraMask[chunkNum];
			info.data = buildBuffer;
			info.startIndex = dataStartIndex;
			info.size = inflatedBuffers[chunkNum].length;

			dataStartIndex += info.size;
		}

		return infos;
	}

	public static ChunkInfo getInfo(IPacket51 packet, Player player) {
		// Create an info objects
		ChunkInfo info = new ChunkInfo();
		info.world = player.getWorld();
		info.player = player;
		info.chunkX = packet.getX();
		info.chunkZ = packet.getZ();
		info.chunkMask = packet.getChunkMask();
		info.extraMask = packet.getExtraMask();
		info.data = packet.getBuffer();
		info.startIndex = 0;
		return info;
	}

	public static void ComputeChunkInfoAndObfuscate(ChunkInfo info, byte[] original) {
		// Compute chunk number
		for (int i = 0; i < 16; i++) {
			if ((info.chunkMask & 1 << i) > 0) {
				info.chunkSectionToIndexMap[i] = info.chunkSectionNumber;
				info.chunkSectionNumber++;
			}
			else {
				info.chunkSectionToIndexMap[i] = -1;
			}
			if ((info.extraMask & 1 << i) > 0) {
				info.extraSectionToIndexMap[i] = info.extraSectionNumber;
				info.extraSectionNumber++;
			}
		}

		info.size = 2048 * (5 * info.chunkSectionNumber + info.extraSectionNumber) + 256;
		info.blockSize = 4096 * info.chunkSectionNumber;

		if (info.startIndex + info.blockSize > info.data.length) {
			return;
		}

		// Obfuscate
		if (!OrebfuscatorConfig.isWorldDisabled(info.world.getName()) && OrebfuscatorConfig.Enabled) {
			byte[] obfuscated = Obfuscate(info);
			// Copy the data out of the buffer
			System.arraycopy(obfuscated, 0, original, info.startIndex, info.blockSize);
		}
	}

	@SuppressWarnings("deprecation")
	public static byte[] Obfuscate(ChunkInfo info) {
		boolean isNether = info.world.getEnvironment() == Environment.NETHER;
		// Used for caching
		ObfuscatedCachedChunk cache = null;
		// Hash used to check cache consistency
		long hash = 0L;
		// Start with caching false
		info.useCache = false;

		int initialRadius = OrebfuscatorConfig.InitialRadius;

		// Expand buffer if not enough space
		if (info.blockSize > info.buffer.length) {
			info.buffer = new byte[info.blockSize];
			buffer.set(info.buffer);
		}

		// Copy data into buffer
		System.arraycopy(info.data, info.startIndex, info.buffer, 0, info.blockSize);

		// Caching
		if (OrebfuscatorConfig.UseCache) {
			// Sanitize buffer for caching
			PrepareBufferForCaching(info.buffer, info.blockSize);

			// Get cache folder
			File cacheFolder = new File(OrebfuscatorConfig.getCacheFolder(), info.world.getName());
			// Create cache objects
			cache = new ObfuscatedCachedChunk(cacheFolder, info.chunkX, info.chunkZ);
			info.useCache = true;
			// Hash the chunk
			hash = CalculationsUtil.Hash(info.buffer, info.blockSize);

			// Check if hash is consistent
			cache.Read();

			long storedHash = cache.getHash();

			if (storedHash == hash && cache.data != null) {
				// Caching done, de-sanitize buffer
				RepaintChunkToBuffer(cache.data, info);

				// Hash match, use the cached data instead and skip calculations
				return cache.data;
			}
		}

		// Track of pseudo-randomly assigned randomBlock
		int randomIncrement = 0;

		int engineMode = OrebfuscatorConfig.EngineMode;

		int randomBlocksLength = OrebfuscatorConfig.getRandomBlocks(isNether).length;

		// Loop over 16x16x16 chunks in the 16x256x16 column
		int currentTypeIndex = 0;
		int currentExtendedIndex = 0;
		for (int i = 0; i < 16; i++) {
			if ((info.chunkMask & 1 << i) != 0) {
				currentExtendedIndex += 4096 + 2048 + 2048 + 2048;
			}
		}

		int startX = info.chunkX << 4;
		int startZ = info.chunkZ << 4;

		for (int i = 0; i < 16; i++) {
			// If the bitmask indicates this chunk is sent...
			if ((info.chunkMask & 1 << i) != 0) {

				OrebfuscatorConfig.shuffleRandomBlocks();
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							boolean usesExtra = ((info.extraMask & 1 << i) != 0);

							int blockY = (i << 4) + y;
							int typeID = info.world.getBlockTypeIdAt(startX + x, blockY, startZ + z);
							
							/*int typeIDinfo.data[info.startIndex + currentTypeIndex];
							
							if (usesExtra) {
								byte extended = info.data[info.startIndex + currentExtendedIndex];
	                            byte extra = 0;
	                            int extraint = 0;
								if (currentTypeIndex % 2 == 0) {
									extra = (byte) (extended & 0x0F);
								} else {
									extra = (byte) (extended >> 4);
								}
								if (extra != 0) {
									if (extra > 0) {
										extra -= 16;
									}
									if (typeID > 0) {
										typeID -= 256;
									}
									typeID += (17 + extra) *256;
								}
							}*/

							// Obfuscate block if needed
							if (OrebfuscatorConfig.isObfuscated(typeID, isNether) && !areAjacentBlocksTransparent(info, typeID, startX + x, blockY, startZ + z, initialRadius)) {
								if (engineMode == 1) {
									// Engine mode 1, replace with stone
									info.buffer[currentTypeIndex] = (byte) (isNether ? 87 : 1);
								} else if (engineMode == 2) {
									// Ending mode 2, replace with random block
									randomIncrement = CalculationsUtil.increment(randomIncrement, randomBlocksLength);
									int randomBlockID = OrebfuscatorConfig.getRandomBlockID(randomIncrement, isNether);
									info.buffer[currentTypeIndex] = (byte) randomBlockID;
								}
							}

							if (usesExtra) {
								if (currentTypeIndex % 2 == 1) {
									currentExtendedIndex++;
								}
							}
							currentTypeIndex++;
						}
					}
				}
			}
		}

		// If cache is still allowed
		if (info.useCache) {
			cache.Write(hash, info.buffer);
		}

		// Free memory taken by cache quickly
		if (cache != null) {
			cache.free();
		}

		// Caching done, de-sanitize buffer
		if (OrebfuscatorConfig.UseCache) {
			RepaintChunkToBuffer(info.buffer, info);
		}

		return info.buffer;
	}

	private static byte[] cacheMap = new byte[256];
	static {
		buildCacheMap();
	}

	public static void buildCacheMap() {
		for (int i = 0; i < 256; i++) {
			cacheMap[i] = (byte) i;
			if (OrebfuscatorConfig.isBlockTransparent((short) i)) {
				cacheMap[i] = 0;
			}
		}
	}

	private static void PrepareBufferForCaching(byte[] data, int length) {
		for (int i = 0; i < length; i++) {
			data[i] = cacheMap[(data[i] + 256) % 256];
		}
	}

	private static void RepaintChunkToBuffer(byte[] data, ChunkInfo info) {
		RepaintChunkToBuffer1(data, info);
	}

	private static void RepaintChunkToBuffer1(byte[] data, ChunkInfo info) {
		byte[] original = info.data;
		int start = info.startIndex;
		int length = info.blockSize;

		for (int i = 0; i < length; i++) {
			if (data[i] == 0 && original[start + i] != 0) {
				if (OrebfuscatorConfig.isBlockTransparent(original[start + i])) {
					data[i] = original[start + i];
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static boolean areAjacentBlocksTransparent(ChunkInfo info, int currentBlockID, int x, int y, int z, int countdown) {
		if (y >= info.world.getMaxHeight() || y < 0) {
			return true;
		}

		int id = 0;
		if (CalculationsUtil.isChunkLoaded(info.world, x >> 4, z >> 4)) {
			id = (byte) info.world.getBlockTypeIdAt(x, y, z);
		}
		else {
			id = 1;
			info.useCache = false;
		}

		if (id != currentBlockID && OrebfuscatorConfig.isBlockTransparent(id)) {
			return true;
		}

		if (countdown == 0) {
			return false;
		}

		if (areAjacentBlocksTransparent(info, currentBlockID, x, y + 1, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(info, currentBlockID, x, y - 1, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(info, currentBlockID, x + 1, y, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(info, currentBlockID, x - 1, y, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(info, currentBlockID, x, y, z + 1, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(info, currentBlockID, x, y, z - 1, countdown - 1)) {
			return true;
		}

		return false;
	}

	public static boolean areAjacentBlocksBright(ChunkInfo info, int x, int y, int z, int countdown) {
		if (CalculationsUtil.isChunkLoaded(info.world, x >> 4, z >> 4)) {
			if (info.world.getBlockAt(x, y, z).getLightLevel() > 0) {
				return true;
			}
		}
		else {
			return true;
		}

		if (countdown == 0) {
			return false;
		}

		if (areAjacentBlocksBright(info, x, y + 1, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(info, x, y - 1, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(info, x + 1, y, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(info, x - 1, y, z, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(info, x, y, z + 1, countdown - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(info, x, y, z - 1, countdown - 1)) {
			return true;
		}

		return false;
	}
}