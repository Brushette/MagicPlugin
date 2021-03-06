package com.elmakers.mine.bukkit.utility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.block.LegacySchematic;
import com.elmakers.mine.bukkit.block.MaterialAndData;
import com.elmakers.mine.bukkit.block.Schematic;

@SuppressWarnings("unchecked")
public class SchematicUtils extends CompatibilityUtils {

    public static boolean loadSchematic(InputStream input, Schematic schematic, Logger log) {
        if (input == null || schematic == null || class_NBTCompressedStreamTools_loadFileMethod == null) return false;

        try {
            Object nbtData = class_NBTCompressedStreamTools_loadFileMethod.invoke(null, input);
            if (nbtData == null) {
                return false;
            }

            short width = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Width");
            short height = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Height");
            short length = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Length");

            Object palette = class_NBTTagCompound_getCompoundMethod.invoke(nbtData, "Palette");
            byte[] blockData = (byte[])class_NBTTagCompound_getByteArrayMethod.invoke(nbtData, "BlockData");
            int[] blockMap = null;
            Map<Integer, MaterialAndData> paletteMap = null;

            if (palette != null) {
                // Map the palette
                paletteMap = new HashMap<>();
                Set<String> keys = (Set<String>)class_NBTTagCompound_getKeysMethod.invoke(palette);
                for (String key : keys) {
                    int index = (int)class_NBTTagCompound_getIntMethod.invoke(palette, key);
                    Material material = CompatibilityUtils.getMaterial(key);
                    if (material != null) {
                        paletteMap.put(index, new MaterialAndData(material, key));
                    }
                }
            }
            if (blockData != null) {
                int varInt = 0;
                int varIntLength = 0;
                int index = 0;
                blockMap = new int[width * height * length];

                for (int i = 0; i < blockData.length; i++) {
                    varInt |= (blockData[i] & 127) << (varIntLength++ * 7);

                    if ((blockData[i] & 128) == 128) {
                        continue;
                    }

                    blockMap[index++] = varInt;
                    varIntLength = 0;
                    varInt = 0;
                }
                if (index != blockMap.length) {
                    log.warning("Block data array length does not match dimensions in schematic");
                }
            }

            // Load entities
            Collection<Object> tileEntityData = new ArrayList<>();
            Collection<Object> entityData = new ArrayList<>();

            Object entityList = class_NBTTagCompound_getListMethod.invoke(nbtData, "Entities", NBT_TYPE_COMPOUND);
            Object tileEntityList = null;
            if ((boolean)class_NBTTagCompound_hasKeyMethod.invoke(nbtData, "BlockEntities")) {
                tileEntityList = class_NBTTagCompound_getListMethod.invoke(nbtData, "BlockEntities", NBT_TYPE_COMPOUND);
            } else {
                class_NBTTagCompound_getListMethod.invoke(nbtData, "TileEntities", NBT_TYPE_COMPOUND);
            }

            if (entityList != null) {
                int size = (Integer)class_NBTTagList_sizeMethod.invoke(entityList);
                for (int i = 0; i < size; i++) {
                    Object entity = class_NBTTagList_getMethod.invoke(entityList, i);
                    entityData.add(entity);
                }
            }

            if (tileEntityList != null) {
                int size = (Integer)class_NBTTagList_sizeMethod.invoke(tileEntityList);
                for (int i = 0; i < size; i++) {
                    Object tileEntity = class_NBTTagList_getMethod.invoke(tileEntityList, i);
                    tileEntityData.add(tileEntity);
                }
            }

            Vector origin = new Vector(0, 0, 0);
            int[] offset = (int[])class_NBTTagCompound_getIntArrayMethod.invoke(nbtData, "Offset");
            if (offset != null && offset.length == 3) {
                origin.setX(offset[0]);
                origin.setY(offset[1]);
                origin.setZ(offset[2]);
            }
            schematic.load(width, height, length, blockMap, paletteMap, tileEntityData, entityData, origin);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean loadLegacySchematic(InputStream input, LegacySchematic schematic) {
        if (input == null || schematic == null || class_NBTCompressedStreamTools_loadFileMethod == null) return false;

        try {
            Object nbtData = class_NBTCompressedStreamTools_loadFileMethod.invoke(null, input);
            if (nbtData == null) {
                return false;
            }

            // Version check
            String materials = (String)class_NBTTagCompound_getStringMethod.invoke(nbtData, "Materials");
            if (!materials.equals("Alpha")) {
                Bukkit.getLogger().warning("Schematic is not in Alpha format");
                return false;
            }

            short width = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Width");
            short height = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Height");
            short length = (Short)class_NBTTagCompound_getShortMethod.invoke(nbtData, "Length");

            byte[] blockIds = (byte[])class_NBTTagCompound_getByteArrayMethod.invoke(nbtData, "Blocks");

            // Have to combine block ids to get 12 bits of ids
            // Thanks to the WorldEdit team for showing me how to do this.
            short[] blocks = new short[blockIds.length];
            byte[] addBlocks = new byte[0];
            if ((Boolean)class_NBTTagCompound_hasKeyMethod.invoke(nbtData, "AddBlocks")) {
                addBlocks = (byte[])class_NBTTagCompound_getByteArrayMethod.invoke(nbtData, "AddBlocks");
            }
            for (int index = 0; index < blocks.length; index++) {
                if ((index >> 1) >= addBlocks.length) {
                    blocks[index] = (short) (blockIds[index] & 0xFF);
                } else {
                    if ((index & 1) == 0) {
                        blocks[index] = (short) (((addBlocks[index >> 1] & 0x0F) << 8) + (blockIds[index] & 0xFF));
                    } else {
                        blocks[index] = (short) (((addBlocks[index >> 1] & 0xF0) << 4) + (blockIds[index] & 0xFF));
                    }
                }
            }

            byte[] data = (byte[])class_NBTTagCompound_getByteArrayMethod.invoke(nbtData, "Data");

            Collection<Object> tileEntityData = new ArrayList<>();
            Collection<Object> entityData = new ArrayList<>();

            Object entityList = class_NBTTagCompound_getListMethod.invoke(nbtData, "Entities", NBT_TYPE_COMPOUND);
            Object tileEntityList = class_NBTTagCompound_getListMethod.invoke(nbtData, "TileEntities", NBT_TYPE_COMPOUND);

            if (entityList != null) {
                int size = (Integer)class_NBTTagList_sizeMethod.invoke(entityList);
                for (int i = 0; i < size; i++) {
                    Object entity = class_NBTTagList_getMethod.invoke(entityList, i);
                    entityData.add(entity);
                }
            }

            if (tileEntityList != null) {
                int size = (Integer)class_NBTTagList_sizeMethod.invoke(tileEntityList);
                for (int i = 0; i < size; i++) {
                    Object tileEntity = class_NBTTagList_getMethod.invoke(tileEntityList, i);
                    tileEntityData.add(tileEntity);
                }
            }

            int originX = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOriginX");
            int originY = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOriginY");
            int originZ = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOriginZ");

            int offsetX = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOffsetX");
            int offsetY = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOffsetY");
            int offsetZ = (Integer)class_NBTTagCompound_getIntMethod.invoke(nbtData, "WEOffsetZ");

            schematic.load(width, height, length, blocks, data, tileEntityData, entityData, new Vector(originX, originY, originZ), new Vector(offsetX, offsetY, offsetZ));
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }
}
