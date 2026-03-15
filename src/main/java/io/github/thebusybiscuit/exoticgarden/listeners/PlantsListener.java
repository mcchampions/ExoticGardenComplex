package io.github.thebusybiscuit.exoticgarden.listeners;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;

import io.github.thebusybiscuit.exoticgarden.Berry;
import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.ExoticItems;
import io.github.thebusybiscuit.exoticgarden.PlantType;
import io.github.thebusybiscuit.exoticgarden.Tree;
import io.github.thebusybiscuit.exoticgarden.items.BonemealableItem;
import io.github.thebusybiscuit.exoticgarden.schematics.Schematic;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import io.ncbpfluffybear.fluffymachines.utils.FluffyItems;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

@EnableAsync
public class PlantsListener implements Listener {

    private static final Map<String, SlimefunTag> nameLookup = new HashMap<>();
    private static final SlimefunTag[] valuesCache = SlimefunTag.values();
    private final Config cfg;
    private final ExoticGarden plugin;

    public PlantsListener(ExoticGarden plugin) {
        this.plugin = plugin;
        cfg = plugin.getCfg();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onWateringCanWater(PlayerInteractEvent e) {
        if (!ExoticGarden.instance.isFluffyEnabled()) {
            return;
        }

        Block b = e.getClickedBlock();
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();

        // 空手不处理
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }

        final ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return;
        }

        final Optional<String> id = Slimefun.getItemDataService().getItemData(itemMeta);

        if (b != null && id.isPresent() && id.get().equals(FluffyItems.WATERING_CAN.getItemId()) && e.getHand() == EquipmentSlot.HAND) {
            waterStructure(b.getLocation(), e, item);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onGrow(StructureGrowEvent e) {
        if (PaperLib.isPaper()) {
            if (PaperLib.isChunkGenerated(e.getLocation())) {
                growStructure(e);
            } else {
                PaperLib.getChunkAtAsync(e.getLocation()).thenRun(() -> growStructure(e));
            }
        } else {
            if (!e.getLocation().getChunk().isLoaded()) {
                e.getLocation().getChunk().load();
            }
            growStructure(e);
        }
    }

    @EventHandler
    @Async
    public void onGenerate(ChunkPopulateEvent e) {
        if (!cfg.getOrSetDefault("options.auto-generate-plants", true)) {
            return;
        }

        final World world = e.getWorld();

        if (!Slimefun.getWorldSettingsService().isWorldEnabled(world)) {
            return;
        }

        if (!cfg.getStringList("world-blacklist").contains(world.getName())) {
            Random random = ThreadLocalRandom.current();

            final int worldLimit = getWorldBorder(world);

            if (random.nextInt(100) < cfg.getInt("chances.BUSH")) {
                Berry berry = ExoticGarden.getBerries().get(random.nextInt(ExoticGarden.getBerries().size()));
                if (berry.getType().equals(PlantType.ORE_PLANT)) return;

                int chunkX = e.getChunk().getX();
                int chunkZ = e.getChunk().getZ();

                // Middle of chunk between 3-13 (to avoid loading neighbouring chunks)
                int x = chunkX * 16 + random.nextInt(10) + 3;
                int z = chunkZ * 16 + random.nextInt(10) + 3;

                if ((x < worldLimit && x > -worldLimit) && (z < worldLimit && z > -worldLimit)) {
                    if (PaperLib.isPaper()) {
                        if (PaperLib.isChunkGenerated(world, chunkX, chunkZ)) {
                            growBush(e, x, z, berry, random, true);
                        } else {
                            PaperLib.getChunkAtAsync(world, chunkX, chunkZ).thenRun(() -> growBush(e, x, z, berry, random, true));
                        }
                    } else {
                        growBush(e, x, z, berry, random, false);
                    }
                }
            } else if (random.nextInt(100) < cfg.getInt("chances.TREE")) {
                Tree tree = ExoticGarden.getTrees().get(random.nextInt(ExoticGarden.getTrees().size()));

                int chunkX = e.getChunk().getX();
                int chunkZ = e.getChunk().getZ();

                // Tree size defaults (width/length)
                int tw = 7;
                int tl = 7;

                // Get the sizes of the tree being placed
                // Value is padded +2 blocks to avoid loading neighbouring chunks for block updates
                try {
                    tw = tree.getSchematic().getWidth() + 2;
                    tl = tree.getSchematic().getLength() + 2;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                // Ensure schematic fits inside the chunk
                int x = chunkX * 16 + random.nextInt(16 - tw) + (int) Math.floor((double) tw / 2);
                int z = chunkZ * 16 + random.nextInt(16 - tl) + (int) Math.floor((double) tl / 2);

                if ((x < worldLimit && x > -worldLimit) && (z < worldLimit && z > -worldLimit)) {
                    if (PaperLib.isPaper()) {
                        if (PaperLib.isChunkGenerated(world, chunkX, chunkZ)) {
                            pasteTree(e, x, z, tree);
                        } else {
                            PaperLib.getChunkAtAsync(world, chunkX, chunkZ).thenRun(() -> pasteTree(e, x, z, tree));
                        }
                    } else {
                        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> pasteTree(e, x, z, tree));
                    }
                }
            }
        }
    }

    
    @Async
    private int getWorldBorder(World world) {
        return (int) world.getWorldBorder().getSize();
    }

    @EventHandler
    @Async
    public void onFastGenerate(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        var hand = event.getItem();
        var sfitem = SlimefunItem.getByItem(hand);
        if (sfitem == null || !sfitem.getId().equals(ExoticItems.GoldKeLa.getItemId()) || sfitem.isDisabledIn(block.getWorld())) {
            return;
        }

        applyGoldKela(event, block, hand);
    }

    @Async
    private boolean applyGoldKela(PlayerInteractEvent event, Block block, ItemStack hand) {
        if (!(StorageCacheUtils.getSfItem(block.getLocation()) instanceof BonemealableItem bi)) {
            return false;
        }

        if (bi.isDisabledIn(block.getWorld()) || bi.isBonemealDisabled()) {
            return false;
        }

        var e = new StructureGrowEvent(block.getLocation(), TreeType.TREE, true, event.getPlayer(), List.of());
        if (growStructure0(e)) {
            e.setCancelled(true);
            hand.setAmount(hand.getAmount() - 1);
            return true;
        }

        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable);
            e.setCancelled(true);
            hand.setAmount(hand.getAmount() - 1);
            return true;
        }

        return false;
    }

    private void growStructure(StructureGrowEvent e) {
        growStructure0(e);
    }

    private boolean growStructure0(StructureGrowEvent e) {
    	BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
        SlimefunItem item = StorageCacheUtils.getSfItem(e.getLocation());
        

        try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(e.getWorld()))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build();
        	EditSession particleSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(e.getWorld()))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(false) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build()) {
        	BlockVector3 pos = BlockVector3.at(e.getLocation().getBlockX(), e.getLocation().getBlockY(), e.getLocation().getBlockZ());
        	Block blockAbove = e.getLocation().getBlock().getRelative(BlockFace.UP);
        	BlockVector3 posAbove = BlockVector3.at(blockAbove.getLocation().getBlockX(), blockAbove.getLocation().getBlockY(), blockAbove.getLocation().getBlockZ());
        	if (item != null) {
                e.setCancelled(true);
                for (Tree tree : ExoticGarden.getTrees()) {
                    if (item.getId().equalsIgnoreCase(tree.getSapling())) {
                    	controller.removeBlock(e.getLocation());
                        Schematic.pasteSchematic(e.getLocation(), tree, false);
                        return true;
                    }
                }

                for (Berry berry : ExoticGarden.getBerries()) {
                	Optional<SlimefunItem> slimefunItemOptional = Optional.ofNullable(SlimefunItem.getByItem(berry.getItem()));
                    if (item.getId().equalsIgnoreCase(berry.toBush())) {
                        switch (berry.getType()) {
                            case BUSH -> fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState());
                            case ORE_PLANT, DOUBLE_PLANT -> {
                                
                                
                                item = StorageCacheUtils.getSfItem(blockAbove.getLocation());
                                if (item != null) return false;
                                if (!Tag.SAPLINGS.isTagged(blockAbove.getType()) && !Tag.LEAVES.isTagged(blockAbove.getType())) {
                                    switch (blockAbove.getType()) {
                                        case AIR, CAVE_AIR, SNOW:
                                            break;
                                        default:
                                            return false;
                                    }
                                }
                                slimefunItemOptional.ifPresent(slimefunItem -> controller.createBlock(blockAbove.getLocation(), berry.getID()));
                                fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState());                                
                                Schematic.setRandomFacingHeadFromTexture(particleSession, posAbove, berry.getTexture());
                                
                            }
                            default -> {
                            	Schematic.setRandomFacingHeadFromTexture(particleSession, pos, berry.getTexture());
                                
                                
                            }
                        }

                        Slimefun.getDatabaseManager().getBlockDataController().removeBlock(e.getLocation());
                        try {
                        	slimefunItemOptional.ifPresent(slimefunItem -> controller.createBlock(e.getLocation(), berry.getID()));
                        }
                        catch (IllegalStateException illegalStateException) {
                            // ignore
                        }
                        
                        e.getWorld().playEffect(e.getLocation(), Effect.STEP_SOUND, Material.OAK_LEAVES);
                        break;
                    }
                }

                return true;
            }
        	fastSession.flushQueue();
            particleSession.flushQueue();
        } catch (Exception error) {
        	error.printStackTrace();
        }
        
        

        return false;
    }

    @Async
    private void pasteTree(ChunkPopulateEvent e, int x, int z, Tree tree) {
        for (int y = e.getWorld().getHighestBlockYAt(x, z) + 2; y > 30; y--) {
            Block current = e.getWorld().getBlockAt(x, y, z);
            if (current.getType() != Material.WATER && current.getType() != Material.SEAGRASS && current.getType() != Material.TALL_SEAGRASS && !current.getType().isSolid() && !(current.getBlockData() instanceof Waterlogged && ((Waterlogged) current.getBlockData()).isWaterlogged()) && tree.isSoil(current.getRelative(0, -1, 0).getType()) && isFlat(current)) {
                Schematic.pasteSchematic(e.getWorld(), x, y, z, tree, false);
                break;
            }
        }
    }

    @Async
    private void growBush(ChunkPopulateEvent e, int x, int z, Berry berry, Random random, boolean isPaper) {
    	BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
    	Optional<SlimefunItem> slimefunItemOptional = Optional.ofNullable(SlimefunItem.getByItem(berry.getItem()));
    	try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
    			.world(BukkitAdapter.adapt(e.getWorld()))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build();
        	EditSession particleSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(e.getWorld()))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(false) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build()) {
    		for (int y = e.getWorld().getHighestBlockYAt(x, z) + 2; y > 30; y--) {
                Block current = e.getWorld().getBlockAt(x, y, z);
                BlockVector3 pos = BlockVector3.at(x, y, z);
                if (current.getType() != Material.WATER && !current.getType().isSolid() && berry.isSoil(current.getRelative(BlockFace.DOWN).getType())) {
                	try {
                		slimefunItemOptional.ifPresent(slimefunItem -> controller.createBlock(current.getLocation(), berry.getID()));
                	} catch (IllegalStateException illegalStateException) {
                        // ignore
                    }
                	
                    switch (berry.getType()) {
                        case BUSH:
                            if (isPaper) {
                            	fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState());
                            } else {
                                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState()));
                            }
                            break;
                        case FRUIT, ORE_PLANT, DOUBLE_PLANT:
                        	Schematic.setRandomFacingHeadFromTexture(particleSession, pos, berry.getTexture());
                            break;
                        default:
                            break;
                    }
                    break;
                }
            }
    		fastSession.flushQueue();
            particleSession.flushQueue();
    	} catch (Exception error) {
        	error.printStackTrace();
        }
        	
        	
        
        
    }

    @Async
    private boolean isFlat(Block current) {
        for (int i = -2; i < 2; i++) {
            for (int j = -2; j < 2; j++) {
                for (int k = 0; k < 6; k++) {
                    Block block = current.getRelative(i, k, j);
                    if (block.getType().isSolid()
                            || Tag.LEAVES.isTagged(block.getType())
                            || !current.getRelative(i, -1, j).getType().isSolid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @Async
    public void onHarvest(BlockBreakEvent e) {
        if (Slimefun.getProtectionManager().hasPermission(e.getPlayer(), e.getBlock().getLocation(), Interaction.BREAK_BLOCK)) {
            if (e.getBlock().getType() == Material.PLAYER_HEAD || Tag.LEAVES.isTagged(e.getBlock().getType())) {
                dropFruitFromTree(e.getBlock());
            }

            if (e.getBlock().getType() == Material.SHORT_GRASS) {
                if (!ExoticGarden.getGrassDrops().isEmpty() && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
                    Random random = ThreadLocalRandom.current();

                    if (random.nextInt(100) < 6) {
                        ItemStack[] items = ExoticGarden.getGrassDrops().values().toArray(new ItemStack[0]);
                        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), items[random.nextInt(items.length)]);
                    }
                    if (random.nextInt(100) < 3) {
                        ItemStack grassSeeds = ExoticGarden.getGrassDrops().get("GRASS_SEEDS");
                        if (grassSeeds != null) {
                            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), grassSeeds);
                        }
                    }
                    
                    if (random.nextInt(100) < 2) {
                        ItemStack mysticSeed = ExoticGarden.getGrassDrops().get("MYSTIC_SEED");
                        if (mysticSeed != null) {
                            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), mysticSeed);
                        }
                    }
                }
            } else {
                ItemStack item = ExoticGarden.harvestPlant(e.getBlock());

                if (item != null) {
                    e.setCancelled(true);
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), item);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (!Slimefun.getWorldSettingsService().isWorldEnabled(e.getBlock().getWorld())) {
            return;
        }

        var item = StorageCacheUtils.getSfItem(e.getBlock().getLocation());

        if (item != null) {
            for (Berry berry : ExoticGarden.getBerries()) {
                if (item.getId().equalsIgnoreCase(berry.getID())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        dropFruitFromTree(e.getBlock());
        ItemStack drop = BlockStorage.retrieve(e.getBlock());

        if (drop != null) {
            e.setCancelled(true);
            Location l = e.getBlock().getLocation();
            try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
            		.world(BukkitAdapter.adapt(l.getWorld()))
                    .allowedRegionsEverywhere() // 允许任何区域
                    .limitUnlimited() // 解除限制
                    .changeSetNull() // 不记录变化
                    .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                    .build()) {
            	
            	BlockVector3 pos = BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            	fastSession.setBlock(pos, BlockTypes.AIR.getDefaultState());
            	fastSession.flushQueue();
        	} catch (Exception error) {
            	error.printStackTrace();
            }
            e.getBlock().getWorld().dropItemNaturally(l, drop);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (!Slimefun.getWorldSettingsService().isWorldEnabled(e.getBlock().getWorld())) {
            return;
        }

        String id = BlockStorage.checkID(e.getBlock());

        if (id != null) {
            for (Berry berry : ExoticGarden.getBerries()) {
                if (id.equalsIgnoreCase(berry.getID())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }


        dropFruitFromTree(e.getBlock());
        ItemStack item = BlockStorage.retrieve(e.getBlock());

        if (item != null) {
            e.setCancelled(true);
            Location l = e.getBlock().getLocation();
            try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
            		.world(BukkitAdapter.adapt(l.getWorld()))
            		.allowedRegionsEverywhere() // 允许任何区域
                    .limitUnlimited() // 解除限制
                    .changeSetNull() // 不记录变化
                    .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                    .build()) {
            	
            	BlockVector3 pos = BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            	fastSession.setBlock(pos, BlockTypes.AIR.getDefaultState());
            	fastSession.flushQueue();
        	} catch (Exception error) {
            	error.printStackTrace();
            }
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @Async
    public void onInteract(PlayerInteractEvent e) {
        Material mainHand = e.getPlayer().getInventory().getItemInMainHand().getType();
        Material offHand = e.getPlayer().getInventory().getItemInOffHand().getType();

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getPlayer().isSneaking()) return;
        for (SlimefunTag tag : valuesCache) {
            nameLookup.put(tag.name(), SlimefunTag.GRAVITY_AFFECTED_BLOCKS);
            if (tag.isTagged(mainHand) || tag.isTagged(offHand)) return;
        }

        if (Slimefun.getProtectionManager().hasPermission(e.getPlayer(), e.getClickedBlock().getLocation(), Interaction.BREAK_BLOCK)) {
            ItemStack item = ExoticGarden.harvestPlant(e.getClickedBlock());

            if (item != null) {
                e.getClickedBlock().getWorld().playEffect(e.getClickedBlock().getLocation(), Effect.STEP_SOUND, Material.OAK_LEAVES);
                e.getClickedBlock().getWorld().dropItemNaturally(e.getClickedBlock().getLocation(), item);
            } else {
                // The block wasn't a plant, we try harvesting a fruit instead
                ExoticGarden.getInstance().harvestFruit(e.getClickedBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @Async
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeAll(getAffectedBlocks(e.blockList()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @Async
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeAll(getAffectedBlocks(e.blockList()));
    }

    @EventHandler(ignoreCancelled = true)
    @Async
    public void onBonemealPlant(BlockFertilizeEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.OAK_SAPLING) {
            SlimefunItem item = StorageCacheUtils.getSfItem(b.getLocation());

            if (item instanceof BonemealableItem && ((BonemealableItem) item).isBonemealDisabled()) {
                e.setCancelled(true);
                b.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, b.getLocation().clone().add(0.5, 0, 0.5), 4);
                b.getWorld().playSound(b.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
        }
    }

    @Async
    private Set<Block> getAffectedBlocks(List<Block> blockList) {
        Set<Block> blocksToRemove = new HashSet<>();

        for (Block block : blockList) {
            ItemStack item = ExoticGarden.harvestPlant(block);

            if (item != null) {
                blocksToRemove.add(block);
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }

        return blocksToRemove;
    }

    private void dropFruitFromTree(Block block) {
    	try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
    			.world(BukkitAdapter.adapt(block.getLocation().getWorld()))
    			.allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build()) {
    		
    		for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    for (int z = -1; z < 2; z++) {
                        // inspect a cube at the reference
                        Block fruit = block.getRelative(x, y, z);
                        if (fruit.isEmpty()) continue;


                        Location loc = fruit.getLocation();
                        BlockVector3 pos = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                        SlimefunItem check = StorageCacheUtils.getSfItem(loc);
                        if (check == null) continue;
                        for (Tree tree : ExoticGarden.getTrees()) {
                            if (check.getId().equalsIgnoreCase(tree.getFruitID())) {
                                Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);
                                ItemStack fruits = check.getItem();
                                fruit.getWorld().playEffect(loc, Effect.STEP_SOUND, Material.OAK_LEAVES);
                            	fastSession.setBlock(pos, BlockTypes.AIR.getDefaultState());
                                fruit.getWorld().dropItemNaturally(loc, fruits);
                                
                                	
                                	
                                break;
                            }
                        }
                    }
                }
            }
    		
    		fastSession.flushQueue();
    		
    	} catch (Exception error) {
        	error.printStackTrace();
        }
    		
        
    }

    private void waterStructure(Location l, PlayerInteractEvent e, ItemStack wateringCan) {
    	BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
    	
    	
        SlimefunItem item = BlockStorage.check(l.getBlock());

        try (EditSession fastSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(l.getWorld()))
        		.allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build()) {
        	BlockVector3 pos = BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        	Block blockAbove = l.getBlock().getRelative(BlockFace.UP);
        	BlockVector3 posAbove = BlockVector3.at(blockAbove.getLocation().getBlockX(), blockAbove.getLocation().getBlockY(), blockAbove.getLocation().getBlockZ());

        	if (item != null) {
                final double random = ThreadLocalRandom.current().nextDouble();
                for (Tree tree : ExoticGarden.getTrees()) {
                    if (item.getId().equalsIgnoreCase(tree.getSapling())) {
                        l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l.add(0.5D, 0.5D, 0.5D), 15, 0.2F, 0.2F, 0.2F);
                        if (cfg.getDouble("watering-can.chance") >= random) {
                            BlockStorage.clearBlockInfo(l.getBlock());
                            Schematic.pasteSchematic(l, tree, false);
                            return;
                        }
                    }
                }

                for (Berry berry : ExoticGarden.getBerries()) {
                	Optional<SlimefunItem> slimefunItemOptional = Optional.ofNullable(SlimefunItem.getByItem(berry.getItem()));
                    if (item.getId().equalsIgnoreCase(berry.toBush())) {
                        l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l.add(0.5D, 0.5D, 0.5D), 15, 0.2F, 0.2F, 0.2F);
                        if (cfg.getDouble("watering-can.chance") >= random) {
                            switch (berry.getType()) {
                                case BUSH:
                                	fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState());
                                    break;
                                case ORE_PLANT:
                                case DOUBLE_PLANT:
                                    
                                    item = BlockStorage.check(blockAbove);
                                    if (item != null) return;

                                    if (!Tag.SAPLINGS.isTagged(blockAbove.getType()) && !Tag.LEAVES.isTagged(blockAbove.getType())) {
                                        switch (blockAbove.getType()) {
                                            case AIR:
                                            case CAVE_AIR:
                                            case SNOW:
                                                break;
                                            default:
                                                return;
                                        }
                                    }

                                    slimefunItemOptional.ifPresent(slimefunItem -> controller.createBlock(blockAbove.getLocation(), berry.getID()));
                                    fastSession.setBlock(pos, BlockTypes.OAK_LEAVES.getDefaultState());
                                    Schematic.setRandomFacingHeadFromTexture(fastSession, posAbove, berry.getTexture());
                                    
                                    
                                    break;
                                default:
                                	Schematic.setRandomFacingHeadFromTexture(fastSession, pos, berry.getTexture());
                                    break;
                            }

                            BlockStorage.deleteLocationInfoUnsafely(l, false);
                            slimefunItemOptional.ifPresent(slimefunItem -> controller.createBlock(l, berry.getID()));
                            l.getWorld().playEffect(l, Effect.STEP_SOUND, Material.OAK_LEAVES);
                            break;
                        }
                    }
                }
            }
        	fastSession.flushQueue();
    	} catch (Exception error) {
        	error.printStackTrace();
        }
       
    }
}
