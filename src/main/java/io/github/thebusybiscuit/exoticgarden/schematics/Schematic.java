package io.github.thebusybiscuit.exoticgarden.schematics;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.Tree;
import io.github.thebusybiscuit.exoticgarden.schematics.org.jnbt.ByteArrayTag;
import io.github.thebusybiscuit.exoticgarden.schematics.org.jnbt.CompoundTag;
import io.github.thebusybiscuit.exoticgarden.schematics.org.jnbt.NBTInputStream;
import io.github.thebusybiscuit.exoticgarden.schematics.org.jnbt.ShortTag;
import io.github.thebusybiscuit.exoticgarden.schematics.org.jnbt.Tag;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;


/*
 *
 * This class is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This class is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this class. If not, see <http://www.gnu.org/licenses/>.
 *
 */

/**
 * This class was originally written by Max but was modified for ExoticGarden under the same
 * license as the original work.
 *
 * @author Max
 * @author TheBusyBiscuit
 */
@EnableAsync
public class Schematic {

	// ConcurrentHashMap 的 computeIfAbsent 是原子操作
	private static ConcurrentHashMap<String, BaseBlock> threadSafeHeadCache = new ConcurrentHashMap<>();
	
    private final short[] blocks;
    private final byte[] data;
    private final short width;
    private final short length;
    private final short height;
    private final String name;

    public Schematic(String name, short[] blocks, byte[] data, short width, short length, short height) {
        this.blocks = blocks;
        this.data = data;
        this.width = width;
        this.length = length;
        this.height = height;
        this.name = name;
    }

    private static BaseBlock getCachedHead(@NotNull String base64Value, int rotationIndex) {
        String cacheKey = base64Value + "|" + rotationIndex;
        
        // 1. 创建 NBT 数据
        return threadSafeHeadCache.computeIfAbsent(cacheKey, k -> {
        	// 1. 构建纹理的完整URL
           

            // 4. 构建1.21.1所需的完整NBT结构
            //    根标签是 "profile"，包含 id (UUID数组) 和 properties (纹理列表)
            com.sk89q.jnbt.CompoundTag nbt = new com.sk89q.jnbt.CompoundTag(Map.of(
                "profile", new com.sk89q.jnbt.CompoundTag(Map.of(
                    // 生成一个随机UUID，使用IntArrayTag表示
                    "id", new IntArrayTag(new int[]{
                        UUID.randomUUID().hashCode(),
                        UUID.randomUUID().hashCode(),
                        UUID.randomUUID().hashCode(),
                        UUID.randomUUID().hashCode()
                    }),
                    "properties", new ListTag(com.sk89q.jnbt.CompoundTag.class, List.of(
                        new com.sk89q.jnbt.CompoundTag(Map.of(
                            "name", new StringTag("textures"),
                            "value", new StringTag(base64Value)
                            // 注意：1.21.1中通常不需要 signature，除非你处理的是正版玩家的签名数据
                        ))
                    ))
                ))
            ));
        	
        	// 2. 获取基础方块状态（带旋转）
            BlockState state = BlockState.get("minecraft:player_head[rotation=" + rotationIndex + "]");
            
            // 3. 创建带 NBT 的 BaseBlock
            return new BaseBlock(state, nbt);
        });
    }

	@Async
    public static String textureToBase64(String texture) {
		 String textureUrl = "http://textures.minecraft.net/texture/" + texture;

         // 2. 构建描述纹理的JSON对象
         String textureJson = String.format(
             "{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}",
             textureUrl
         );

         // 3. 对JSON进行Base64编码，得到Value字段
         String base64Value = Base64.getEncoder().encodeToString(textureJson.getBytes());
         return base64Value;
    }
    @Async
    public static void setRandomFacingHeadFromTexture(EditSession editSession, BlockVector3 pos, String texture) {
    	
    	String base64Value = textureToBase64(texture);
        int rotationIndex = ThreadLocalRandom.current().nextInt(16);
        BaseBlock headState = getCachedHead(base64Value, rotationIndex);
        editSession.setBlock(pos, headState);
   	
    }
    @Async
    public static void pasteSchematic(Location loc, Tree tree, boolean doPhysics) {
        pasteSchematic(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tree, doPhysics);
    }
    
    /**
     * 将 FAWE 的 BlockType 转换为 Bukkit 的 Material
     * @param faweBlockType FAWE 的方块类型对象
     * @return 对应的 Bukkit Material，如果找不到则返回 null
     */
    @Async
    public static Material toBukkitMaterial(BlockType faweBlockType) {
        if (faweBlockType == null) {
            return null;
        }

        // 1. 从 BlockType 获取其 Minecraft 命名空间的 ID (例如 "minecraft:stone")
        String minecraftId = faweBlockType.getId();

        // 2. 去除命名空间前缀 "minecraft:"，得到纯名称 (例如 "stone")
        // 注意: 如果方块来自其他模组，命名空间可能不同，需要根据实际情况处理
        String materialName = minecraftId;
        if (minecraftId.startsWith("minecraft:")) {
            materialName = minecraftId.substring("minecraft:".length());
        }

        // 3. 使用 Bukkit 的 Material.getMaterial() 通过名称获取
        // 警告: Material.getMaterial(String) 是大小写敏感的，且需要是 Bukkit 标准名称（全大写）
        // 因此需要将 "stone" 转换为 "STONE"
        Material bukkitMaterial = Material.getMaterial(materialName.toUpperCase());

        // 或者，如果你有 BlockState，可以使用 BukkitAdapter.adapt()
        // Material bukkitMaterial = BukkitAdapter.adapt(faweBlockState).getType();

        return bukkitMaterial;
    }
    
    /**
     * 将 Bukkit Material 转换为 FAWE BlockType
     * @param bukkitMaterial Bukkit 的 Material（例如 Material.STONE）
     * @return 对应的 FAWE BlockType，如果转换失败则返回 null
     */
    @Async
    public static BlockType toBlockType(Material bukkitMaterial) {
        if (bukkitMaterial == null || !bukkitMaterial.isBlock()) {
            return null; // 只有方块才能转换
        }

     // 获取 Bukkit Material 的名称（例如 "STONE"）
        String materialName = bukkitMaterial.name();
        
        // 转换为小写的 Minecraft ID 格式（例如 "stone"）
        String minecraftId = "minecraft:" + materialName.toLowerCase();
        
        // 使用 BlockTypes 解析
        return BlockTypes.get(minecraftId);
    }
    
    @Async
    public static void pasteSchematic(World world, int x1, int y1, int z1, Tree tree, boolean doPhysics) {
    	Bukkit.getScheduler().runTaskAsynchronously(ExoticGarden.getInstance(), () -> {
    		Schematic schematic;

            try {
                schematic = tree.getSchematic();
            } catch (IOException e) {
                ExoticGarden.instance.getLogger().log(Level.WARNING, "Could not paste Schematic for Tree: " + tree.getFruitID() + "_TREE (" + e.getClass().getSimpleName() + ')', e);
                return;
            }

            com.sk89q.worldedit.world.World faweworld = BukkitAdapter.adapt(world);
            BlockDataController blockDataController = Slimefun.getDatabaseManager().getBlockDataController();
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
            		.world(BukkitAdapter.adapt(world))
                    .allowedRegionsEverywhere() // 允许任何区域
                    .limitUnlimited() // 解除限制
                    .changeSetNull() // 不记录变化
                    .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                    .build()) {
                
            	short[] blocks = schematic.getBlocks();
                byte[] blockData = schematic.getData();

                short length = schematic.getLength();
                short width = schematic.getWidth();
                short height = schematic.getHeight();

                // Performance - avoid repeatedly calculating this value in a loop
                int processedX = x1 - length / 2;
                int processedZ = z1 - width / 2;

                for (int x = 0; x < width; ++x) {
                    for (int y = 0; y < height; ++y) {
                        for (int z = 0; z < length; ++z) {
                            int index = y * width * length + z * width + x;

                            int blockX = x + processedX;
                            int blockY = y + y1;
                            int blockZ = z + processedZ;
                            Location loc = new Location(world, blockX, blockY, blockZ);
                            BlockVector3 pos = BlockVector3.at(blockX, blockY, blockZ);
                            BlockState blockState = editSession.getBlock(pos);

                            Material bukkitMaterial = toBukkitMaterial(blockState.getBlockType());
                            if (blockState.isAir() || org.bukkit.Tag.SAPLINGS.isTagged(bukkitMaterial) || (!bukkitMaterial.isSolid() && !bukkitMaterial.isInteractable() && !SlimefunTag.UNBREAKABLE_MATERIALS.isTagged(bukkitMaterial))) {
                                Material material = parseId(blocks[index], blockData[index]);

                                
                                	if (material != null) {
                                        if (blocks[index] != 0) {
                                        	editSession.setBlock(pos, toBlockType(material));
                                            //block.setType(material, doPhysics);
                                        }

                                       

                                        if (org.bukkit.Tag.LEAVES.isTagged(material) && ThreadLocalRandom.current().nextInt(100) < 25) {
                                            Optional<SlimefunItem> slimefunItemOptional = Optional.ofNullable(SlimefunItem.getByItem(tree.getItem()));

                                            /*
                                             * Fix: There already a block in this location.
                                             */
                                            try {
                                                slimefunItemOptional.ifPresent(slimefunItem -> blockDataController.createBlock(loc, slimefunItem.getId()));
                                            } catch (IllegalStateException illegalStateException) {
                                                // ignore
                                            }
                                        } else if (material == Material.PLAYER_HEAD) {
                                        	setRandomFacingHeadFromTexture(editSession, pos, tree.getTexture());

                                            Optional<SlimefunItem> slimefunItemOptional =
                                                    Optional.ofNullable(SlimefunItem.getByItem(tree.getFruit()));

                                            slimefunItemOptional.ifPresent(slimefunItem -> blockDataController.createBlock(loc, slimefunItem.getId()));
                                        }
                                    }
                                	
                                
                            }
                        }
                    }
                    
                    
                }
            	
            	
                editSession.flushQueue();
                
            } catch (Exception e) {
            	e.printStackTrace();
                throw new RuntimeException("批量设置头颅失败", e);
            }
    		
    	});
        
        
    }

    public static Material parseId(short blockId, byte blockData) {
        switch (blockId) {
            case 6:
                if (blockData == 0) return Material.OAK_SAPLING;
                if (blockData == 1) return Material.SPRUCE_SAPLING;
                if (blockData == 2) return Material.BIRCH_SAPLING;
                if (blockData == 3) return Material.JUNGLE_SAPLING;
                if (blockData == 4) return Material.ACACIA_SAPLING;
                if (blockData == 5) return Material.DARK_OAK_SAPLING;
                break;
            case 17:
                if (blockData == 0 || blockData == 4 || blockData == 8 || blockData == 12) return Material.OAK_LOG;
                if (blockData == 1 || blockData == 5 || blockData == 9 || blockData == 13) return Material.SPRUCE_LOG;
                if (blockData == 2 || blockData == 6 || blockData == 10 || blockData == 14) return Material.BIRCH_LOG;
                if (blockData == 3 || blockData == 7 || blockData == 11 || blockData == 15) return Material.JUNGLE_LOG;
                break;
            case 18:
                if (blockData == 0 || blockData == 4 || blockData == 8 || blockData == 12) return Material.OAK_LEAVES;
                if (blockData == 1 || blockData == 5 || blockData == 9 || blockData == 13)
                    return Material.SPRUCE_LEAVES;
                if (blockData == 2 || blockData == 6 || blockData == 10 || blockData == 14)
                    return Material.BIRCH_LEAVES;
                if (blockData == 3 || blockData == 7 || blockData == 11 || blockData == 15)
                    return Material.JUNGLE_LEAVES;
                return Material.OAK_LEAVES;
            case 161:
                if (blockData == 0 || blockData == 4 || blockData == 8 || blockData == 12)
                    return Material.ACACIA_LEAVES;
                if (blockData == 1 || blockData == 5 || blockData == 9 || blockData == 13)
                    return Material.DARK_OAK_LEAVES;
                break;
            case 162:
                if (blockData == 0 || blockData == 4 || blockData == 8 || blockData == 12) return Material.ACACIA_LOG;
                if (blockData == 1 || blockData == 5 || blockData == 9 || blockData == 13) return Material.DARK_OAK_LOG;
                break;
            case 144:
                return Material.PLAYER_HEAD;
            default:
                return null;
        }

        return null;
    }

    public static Schematic loadSchematic(File file) {
        try {
            Map<String, Tag> schematic;

            try (NBTInputStream stream = new NBTInputStream(new FileInputStream(file))) {
                CompoundTag schematicTag = (CompoundTag) stream.readTag();

                if (!schematicTag.getName().equals("Schematic")) {
                    throw new IllegalArgumentException("Tag \"Schematic\" does not exist or is not first");
                }

                schematic = schematicTag.getValue();
                if (!schematic.containsKey("Blocks")) {
                    throw new IllegalArgumentException("Schematic file is missing a \"Blocks\" tag");
                }
            }

            short width = getChildTag(schematic, "Width", ShortTag.class).getValue();
            short length = getChildTag(schematic, "Length", ShortTag.class).getValue();
            short height = getChildTag(schematic, "Height", ShortTag.class).getValue();

            // Get blocks
            byte[] blockId = getChildTag(schematic, "Blocks", ByteArrayTag.class).getValue();
            byte[] blockData = getChildTag(schematic, "Data", ByteArrayTag.class).getValue();
            byte[] addId = new byte[0];
            short[] blocks = new short[blockId.length]; // Have to later combine IDs

            // We support 4096 block IDs using the same method as vanilla Minecraft, where
            // the highest 4 bits are stored in a separate byte array.
            if (schematic.containsKey("AddBlocks")) {
                addId = getChildTag(schematic, "AddBlocks", ByteArrayTag.class).getValue();
            }

            // Combine the AddBlocks data with the first 8-bit block ID
            for (int index = 0; index < blockId.length; index++) {
                if ((index >> 1) >= addId.length) { // No corresponding AddBlocks index
                    blocks[index] = (short) (blockId[index] & 0xFF);
                } else {
                    if ((index & 1) == 0) {
                        blocks[index] = (short) (((addId[index >> 1] & 0x0F) << 8) + (blockId[index] & 0xFF));
                    } else {
                        blocks[index] = (short) (((addId[index >> 1] & 0xF0) << 4) + (blockId[index] & 0xFF));
                    }
                }
            }

            return new Schematic(file.getName().replace(".schematic", ""), blocks, blockData, width, length, height);
        } catch (Throwable e) {
            ExoticGarden.getInstance().getLogger().log(Level.SEVERE, "Failed to load schematic " + file.getName(), e);
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get child tag of a NBT structure.
     *
     * @param items    The parent tag map
     * @param key      The name of the tag to get
     * @param expected The expected type of the tag
     * @return child tag casted to the expected type
     * @throws IllegalArgumentException if the tag does not exist or the tag is not of the
     *                                  expected type
     */
    private static <T extends Tag> T getChildTag(Map<String, Tag> items, String key, Class<T> expected) {
        if (!items.containsKey(key)) {
            throw new IllegalArgumentException("Schematic file is missing a \"" + key + "\" tag");
        }

        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new IllegalArgumentException(key + " tag is not of tag type " + expected.getName());
        }

        return expected.cast(tag);
    }

    /**
     * @return the blocks
     */
    public short[] getBlocks() {
        return blocks;
    }

    public String getName() {
        return name;
    }

    /**
     * @return the data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return the width
     */
    public short getWidth() {
        return width;
    }

    /**
     * @return the length
     */
    public short getLength() {
        return length;
    }

    /**
     * @return the height
     */
    public short getHeight() {
        return height;
    }

}
