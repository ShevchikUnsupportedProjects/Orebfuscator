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

package com.lishid.orebfuscator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

import com.lishid.orebfuscator.internal.IBlockAccess;
import com.lishid.orebfuscator.internal.InternalAccessor;

public class OrebfuscatorConfig {

	// Constant/persistent data
	private static int AvailableProcessors = Runtime.getRuntime().availableProcessors();

	// Main engine config
	public static boolean Enabled = true;
	public static boolean UpdateOnDamage = true;
	public static int EngineMode = 2;
	public static int InitialRadius = 1;
	public static int UpdateRadius = 2;
	public static int OrebfuscatorPriority = 1;
	public static int CompressionLevel = 1;
	public static int ProcessingThreads = AvailableProcessors - 1;

	// Utilities
	private static boolean[] ObfuscateBlocks = new boolean[4096];
	private static boolean[] NetherObfuscateBlocks = new boolean[4096];
	private static Integer[] RandomBlocks = new Integer[] { 1, 4, 5, 14, 15, 16, 21, 46, 48, 49, 56, 73, 82, 129 };
	private static Integer[] NetherRandomBlocks = new Integer[] { 13, 87, 88, 112, 153 };
	private static HashSet<String> DisabledWorlds = new HashSet<String>();


	public static IBlockAccess blockAccess;
	private static boolean[] TransparentBlocks = new boolean[4096];
	private static boolean TransparentCached = false;

	public static boolean isBlockTransparent(int i) {
		if (blockAccess == null) {
			blockAccess = InternalAccessor.Instance.newBlockAccess();
		}

		if (!TransparentCached) {
			// Generate TransparentBlocks by reading them from Minecraft
			generateTransparentBlocks();
		}

		return TransparentBlocks[i];
	}

	private static void generateTransparentBlocks() {
		for (int i = 0; i < TransparentBlocks.length; i++) {
			TransparentBlocks[i] = blockAccess.isBlockTransparent(i);
		}
		TransparentCached = true;
	}

	public static boolean isObfuscated(int id, boolean nether) {
		// Nether case
		if (nether) {
			if (id == 87) {
				return true;
			}
			return NetherObfuscateBlocks[id];
		}

		// Normal case
		if (id == 1) {
			return true;
		}

		return ObfuscateBlocks[id];
	}

	public static boolean isWorldDisabled(String name) {
		return DisabledWorlds.contains(name);
	}

	public static String getDisabledWorlds() {
		String retval = "";
		for (String world : DisabledWorlds) {
			retval += world + ", ";
		}
		return retval.length() > 1 ? retval.substring(0, retval.length() - 2) : retval;
	}

	public static int getRandomBlockID(int index, boolean nether) {
		if (nether) {
			return (int) NetherRandomBlocks[index];
		}
		return (int) RandomBlocks[index];
	}

	public static Integer[] getRandomBlocks(boolean nether) {
		if (nether) {
			return NetherRandomBlocks;
		}
		return (RandomBlocks);
	}

	public static void shuffleRandomBlocks() {
		synchronized (RandomBlocks) {
			Collections.shuffle(Arrays.asList(RandomBlocks));
		}
	}

	// Set

	public static void setEngineMode(int data) {
		setData("Integers.EngineMode", data);
		EngineMode = data;
	}

	public static void setUpdateRadius(int data) {
		setData("Integers.UpdateRadius", data);
		UpdateRadius = data;
	}

	public static void setInitialRadius(int data) {
		if (data < 1) {
			data = 1;
		}
		setData("Integers.InitialRadius", data);
		InitialRadius = data;
	}

	public static void setProcessingThreads(int data) {
		setData("Integers.ProcessingThreads", data);
		ProcessingThreads = data;
	}

	public static void setEnabled(boolean data) {
		setData("Booleans.Enabled", data);
		Enabled = data;
	}

	public static void setDisabledWorlds(String name, boolean data) {
		if (!data) {
			DisabledWorlds.remove(name);
		}
		else {
			DisabledWorlds.add(name);
		}
		setData("Lists.DisabledWorlds", DisabledWorlds);
	}

	private static boolean getBoolean(String path, boolean defaultData) {
		if (getConfig().get(path) == null) {
			setData(path, defaultData);
		}
		return getConfig().getBoolean(path, defaultData);
	}

	private static int getInt(String path, int defaultData) {
		if (getConfig().get(path) == null) {
			setData(path, defaultData);
		}
		return getConfig().getInt(path, defaultData);
	}

	private static List<Integer> getIntList(String path, List<Integer> defaultData) {
		if (getConfig().get(path) == null) {
			setData(path, defaultData);
		}
		return getConfig().getIntegerList(path);
	}

	private static Integer[] getIntList2(String path, List<Integer> defaultData) {
		if (getConfig().get(path) == null) {
			setData(path, defaultData);
		}
		return getConfig().getIntegerList(path).toArray(new Integer[1]);
	}

	private static List<String> getStringList(String path, List<String> defaultData) {
		if (getConfig().get(path) == null) {
			setData(path, defaultData);
		}
		return getConfig().getStringList(path);
	}

	private static void setData(String path, Object data) {
		try {
			getConfig().set(path, data);
			save();
		}
		catch (Exception e) {
			Orebfuscator.log(e);
		}
	}

	private static void setBlockValues(boolean[] boolArray, List<Integer> blocks, boolean transparent) {
		for (int i = 0; i < boolArray.length; i++) {
			boolArray[i] = blocks.contains(i);

			// If block is transparent while we don't want them to, or the other way around
			if (transparent != isBlockTransparent((short) i)) {
				// Remove it
				boolArray[i] = false;
			}
		}
	}

	public static void load() {

		EngineMode = getInt("Integers.EngineMode", EngineMode);
		if (EngineMode != 1 && EngineMode != 2) {
			EngineMode = 2;
			Orebfuscator.log("EngineMode must be 1 or 2.");
		}

		InitialRadius = clamp(getInt("Integers.InitialRadius", InitialRadius), 1, 2);

		UpdateRadius = clamp(getInt("Integers.UpdateRadius", UpdateRadius), 1, 5);
		ProcessingThreads = clamp(getInt("Integers.ProcessingThreads", ProcessingThreads), 1, AvailableProcessors);

		OrebfuscatorPriority = clamp(getInt("Integers.OrebfuscatorPriority", OrebfuscatorPriority), Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
		CompressionLevel = clamp(getInt("Integers.CompressionLevel", CompressionLevel), 1, 9);
		UpdateOnDamage = getBoolean("Booleans.UpdateOnDamage", UpdateOnDamage);
		Enabled = getBoolean("Booleans.Enabled", Enabled);

		// Read block lists
		setBlockValues(ObfuscateBlocks, getIntList("Lists.ObfuscateBlocks", Arrays.asList(new Integer[] { 14, 15, 16, 21, 54, 56, 73, 74, 129, 130 })), false);
		setBlockValues(NetherObfuscateBlocks, getIntList("Lists.NetherObfuscateBlocks", Arrays.asList(new Integer[] { 87, 153 })), false);

		// Disable worlds
		DisabledWorlds = new HashSet<String>(getStringList("Lists.DisabledWorlds", new ArrayList<String>(DisabledWorlds)));

		RandomBlocks = getIntList2("Lists.RandomBlocks", Arrays.asList(RandomBlocks));
		NetherRandomBlocks = getIntList2("Lists.NetherRandomBlocks", Arrays.asList(NetherRandomBlocks));

		// Validate RandomBlocks
		for (int i = 0; i < RandomBlocks.length; i++) {
			// Don't want people to put chests and other stuff that lags the hell out of players.
			if (RandomBlocks[i] == null || OrebfuscatorConfig.isBlockTransparent(RandomBlocks[i])) {
				RandomBlocks[i] = 1;
			}
		}

		save();
	}

	public static void reload() {
		Orebfuscator.instance.reloadConfig();
		load();
	}

	public static void save() {
		Orebfuscator.instance.saveConfig();
	}

	private static FileConfiguration getConfig() {
		return Orebfuscator.instance.getConfig();
	}

	public static int clamp(int value, int min, int max) {
		if (value < min) {
			value = min;
		}
		if (value > max) {
			value = max;
		}
		return value;
	}
}
