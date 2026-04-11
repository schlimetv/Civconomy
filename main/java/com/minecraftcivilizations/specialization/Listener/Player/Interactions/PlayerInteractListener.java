package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.minecraftcivilizations.specialization.Specialization;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerInteractListener implements Listener {

    private final Set<UUID> cascadingSugarcane = new HashSet<>();

    private static final NamespacedKey BLESSED_ITEM_KEY =
            new NamespacedKey(Specialization.getInstance(), "blessed_item");
    private static final Random BLESSING_RANDOM = new Random();

    @EventHandler
    public void onOpenBlockInventory(InventoryOpenEvent e) {
        if (e.getPlayer().isOp()) return;

        InventoryType type = e.getInventory().getType();
        List<InventoryType> defaultAllow = SpecializationConfig.getCanUseBlockConfig().get("default", new TypeToken<>() {});
        if (defaultAllow.contains(type)) return;

        CustomPlayer player = CoreUtil.getPlayer(e.getPlayer());
        for (Skill skill : player.getSkills()) {
            SkillType skillType = skill.getSkillType();
            int playerSkillLevel = player.getSkillLevel(skillType);

            for (SkillLevel skillLevel : SkillLevel.values()) {
                if (skillLevel.getLevel() <= playerSkillLevel) {
                    String configKey = skillType + "_" + skillLevel;
                    List<InventoryType> types = SpecializationConfig.getCanUseBlockConfig().get(configKey, new TypeToken<>() {});
                    if (types != null && types.contains(type)) {
                        return;
                    }
                }
            }
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onWaterSmushCrop(BlockFromToEvent e) {
        if (e.getToBlock().getBlockData() instanceof Ageable) {
            e.getToBlock().setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDestoryFarmland(BlockBreakEvent e) {
        if (e.getBlock().getType().equals(Material.FARMLAND)) {
            e.getBlock().getRelative(BlockFace.UP).setType(Material.AIR);
            e.getBlock().setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSmushCrop(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.FARMLAND) return;

        e.setCancelled(true);

        // ── Delay the block change by 2 ticks ──
        // The vanilla trample event fires on the SAME tick as the
        // collision.  If we change farmland→dirt immediately, the
        // player's position hasn't been resolved yet and they clip
        // into the new full-height block.
        //
        // A 2-tick delay (1/10th of a second — imperceptible) gives
        // the physics engine time to resolve the player's position
        // and let them begin their next movement step.  Players who
        // are sprinting will have moved off the block by then, so
        // the swap happens harmlessly beneath/behind them.
        //
        // We capture the block coordinates (not the Block reference)
        // because Block objects can become stale across ticks.
        final int bx = clicked.getX();
        final int by = clicked.getY();
        final int bz = clicked.getZ();
        final World world = clicked.getWorld();

        Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
            Block block = world.getBlockAt(bx, by, bz);
            if (block.getType() != Material.FARMLAND) return;

            block.setType(Material.DIRT);
            block.getRelative(BlockFace.UP).setType(Material.AIR);

            // ── Safety net for stationary players (1 tick after swap) ──
            // The 2-tick delay lets sprinting players clear the block,
            // but a player jumping straight up with no horizontal
            // velocity lands right back on the same spot.  Their feet
            // end up at Y+0.9375 (old farmland surface), which is now
            // INSIDE the full-height dirt block.
            //
            // We check 1 tick after the swap — if any player's feet
            // are inside the block, we nudge them up.  This only fires
            // for stationary players (moving ones already cleared it),
            // so the teleport is fine — there's no momentum to lose.
            final double fullBlockTopY = by + 1.0;
            Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
                Block check = world.getBlockAt(bx, by, bz);
                if (!check.getType().isSolid()) return;

                for (Player nearby : world.getBlockAt(bx, by, bz)
                        .getLocation().getNearbyPlayers(1.5)) {
                    Location pLoc = nearby.getLocation();
                    if (pLoc.getBlockX() != bx || pLoc.getBlockZ() != bz) continue;

                    double feetY = pLoc.getY();
                    if (feetY >= (double) by && feetY < fullBlockTopY) {
                        Location corrected = pLoc.clone();
                        corrected.setY(fullBlockTopY);
                        nearby.teleport(corrected);
                    }
                }
            }, 1L);
        }, 2L);
    }

    @EventHandler
    public void onLibrarianEnchantItem(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        if (!player.isSneaking()) return;

        CustomPlayer customPlayer = CoreUtil.getPlayer(player);
        if (customPlayer == null) return;

        int librarianLevel = customPlayer.getSkillLevel(SkillType.LIBRARIAN);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Water bottle → experience bottle (EXPERT+)
        if (isBlessWaterBottle(mainHand)) {
            if (librarianLevel < SkillLevel.EXPERT.getLevel()) {
                PlayerUtil.message(player, ChatColor.RED + "You need to be Expert Librarian to bless water bottles.", 1);
                e.setCancelled(true);
                return;
            }
            if (player.getFoodLevel() < 6) {
                PlayerUtil.message(player, ChatColor.RED + "You're too hungry to bless this bottle.", 1);
                e.setCancelled(true);
                return;
            }
            e.setCancelled(true);
            mainHand.setAmount(mainHand.getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE));
            player.setFoodLevel(player.getFoodLevel() - 6);
            customPlayer.addSkillXp(SkillType.LIBRARIAN, 12);
            return;
        }

        // Gear blessing: enchantable item in main hand + BOOK in off hand
        if (offHand.getType() != Material.BOOK) return;
        if (!isBlessableGear(mainHand)) return;

        if (librarianLevel < SkillLevel.JOURNEYMAN.getLevel()) {
            PlayerUtil.message(player, ChatColor.RED + "You need to be Journeyman Librarian to bless gear.", 1);
            e.setCancelled(true);
            return;
        }

        if (mainHand.hasItemMeta() && mainHand.getItemMeta()
                .getPersistentDataContainer().has(BLESSED_ITEM_KEY, PersistentDataType.BOOLEAN)) {
            PlayerUtil.message(player, ChatColor.RED + "This item has already been blessed.", 1);
            e.setCancelled(true);
            return;
        }

        int levelCost = librarianLevel;
        if (player.getLevel() < levelCost) {
            PlayerUtil.message(player, ChatColor.RED + "You need at least " + levelCost + " Minecraft levels to bless gear.", 1);
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        List<Enchantment> pool = getBlessEnchantPool(librarianLevel).stream()
                .filter(enchant -> enchant.canEnchantItem(mainHand))
                .collect(Collectors.toList());
        if (pool.isEmpty()) return;

        int bookCount = offHand.getAmount();
        player.getInventory().setItemInOffHand(
                bookCount > 1 ? new ItemStack(Material.BOOK, bookCount - 1) : new ItemStack(Material.AIR));

        player.setLevel(player.getLevel() - levelCost);

        Enchantment chosen = pool.get(BLESSING_RANDOM.nextInt(pool.size()));
        int enchantLevel = getBlessEnchantLevel(chosen, librarianLevel);
        String playerName = player.getName();

        ItemStack fresh = player.getInventory().getItemInMainHand();
        fresh.editMeta(m -> {
            m.addEnchant(chosen, enchantLevel, true);
            m.getPersistentDataContainer().set(BLESSED_ITEM_KEY, PersistentDataType.BOOLEAN, true);
            List<String> lore = new ArrayList<>(m.hasLore() && m.getLore() != null ? m.getLore() : List.of());
            lore.add(ChatColor.LIGHT_PURPLE + "Blessed by " + ChatColor.GOLD + playerName);
            m.setLore(lore);
        });

        customPlayer.addSkillXp(SkillType.LIBRARIAN, 20);

        if (chosen == Enchantment.BINDING_CURSE) {
            autoBlessEquip(player, fresh);
        }
    }

    private boolean isBlessWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (!item.hasItemMeta()) return true;
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType type = meta.getBasePotionType();
        return type == null || type == PotionType.WATER;
    }

    private boolean isBlessableGear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_SWORD") || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || item.getType() == Material.BOW
                || item.getType() == Material.CROSSBOW
                || item.getType() == Material.TRIDENT
                || item.getType() == Material.SHIELD;
    }

    private List<Enchantment> getBlessEnchantPool(int level) {
        List<Enchantment> pool = new ArrayList<>();
        pool.add(Enchantment.PROTECTION);
        pool.add(Enchantment.FEATHER_FALLING);
        pool.add(Enchantment.EFFICIENCY);
        pool.add(Enchantment.SHARPNESS);
        pool.add(Enchantment.POWER);
        pool.add(Enchantment.RESPIRATION);
        pool.add(Enchantment.AQUA_AFFINITY);
        pool.add(Enchantment.BINDING_CURSE);

        if (level >= SkillLevel.EXPERT.getLevel()) {
            pool.add(Enchantment.FIRE_PROTECTION);
            pool.add(Enchantment.BLAST_PROTECTION);
            pool.add(Enchantment.PROJECTILE_PROTECTION);
            pool.add(Enchantment.FIRE_ASPECT);
            pool.add(Enchantment.KNOCKBACK);
            pool.add(Enchantment.SMITE);
            pool.add(Enchantment.BANE_OF_ARTHROPODS);
            pool.add(Enchantment.SILK_TOUCH);
            pool.add(Enchantment.FORTUNE);
            pool.add(Enchantment.FLAME);
            pool.add(Enchantment.PUNCH);
        }

        if (level >= SkillLevel.MASTER.getLevel()) {
            pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.LOOTING);
            pool.add(Enchantment.INFINITY);
            pool.add(Enchantment.DEPTH_STRIDER);
            pool.add(Enchantment.FROST_WALKER);
            pool.add(Enchantment.THORNS);
            pool.add(Enchantment.SWEEPING_EDGE);
        }

        if (level >= SkillLevel.GRANDMASTER.getLevel()) {
            pool.add(Enchantment.MENDING);
            pool.add(Enchantment.SOUL_SPEED);
            pool.add(Enchantment.SWIFT_SNEAK);
        }

        return pool;
    }

    private int getBlessEnchantLevel(Enchantment enchant, int librarianLevel) {
        int maxVanilla = enchant.getMaxLevel();
        int maxAllowed = switch (librarianLevel) {
            case 2 -> 1;
            case 3 -> Math.min(maxVanilla, 2);
            case 4 -> Math.min(maxVanilla, 3);
            default -> maxVanilla;
        };
        return BLESSING_RANDOM.nextInt(maxAllowed) + 1;
    }

    private void autoBlessEquip(Player player, ItemStack item) {
        String name = item.getType().name();
        if (name.endsWith("_HELMET") && player.getInventory().getHelmet() == null) {
            player.getInventory().setHelmet(item.clone());
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else if (name.endsWith("_CHESTPLATE") && player.getInventory().getChestplate() == null) {
            player.getInventory().setChestplate(item.clone());
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else if (name.endsWith("_LEGGINGS") && player.getInventory().getLeggings() == null) {
            player.getInventory().setLeggings(item.clone());
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else if (name.endsWith("_BOOTS") && player.getInventory().getBoots() == null) {
            player.getInventory().setBoots(item.clone());
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSweetBerryHarvest(PlayerHarvestBlockEvent e) {
        if (e.getHarvestedBlock().getType() != Material.SWEET_BERRY_BUSH) return;
        boolean producedBerries = e.getItemsHarvested().stream()
                .anyMatch(item -> item.getType() == Material.SWEET_BERRIES);
        if (!producedBerries) return;

        Player player = e.getPlayer();
        CustomPlayer cp = CoreUtil.getPlayer(player);
        if (cp != null) {
            cp.addSkillXp(SkillType.FARMER, 3);
        }
    }

    @EventHandler
    public void onSugarcaneBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.SUGAR_CANE) return;

        CustomPlayer cp = CoreUtil.getPlayer(e.getPlayer());
        if (cp == null) return;

        Pair<SkillType, Double> pair =
                SpecializationConfig.getXpGainFromBreakingConfig()
                        .get(Material.SUGAR_CANE, new TypeToken<Pair<SkillType, Double>>() {});
        double xpPer = pair != null && pair.secondValue() != null ? pair.secondValue() : 0d;

        if (cascadingSugarcane.contains(e.getPlayer().getUniqueId())) {
            if (xpPer > 0) cp.addSkillXp(SkillType.FARMER, xpPer);
            return;
        }

        cascadingSugarcane.add(e.getPlayer().getUniqueId());
        try {
            if (xpPer > 0) cp.addSkillXp(SkillType.FARMER, xpPer);

            List<Block> stack = new ArrayList<>();
            Block b = e.getBlock().getRelative(BlockFace.UP);
            while (b.getType() == Material.SUGAR_CANE) {
                stack.add(b);
                b = b.getRelative(BlockFace.UP);
            }

            Collections.reverse(stack);
            for (Block cane : stack) {
                e.getPlayer().breakBlock(cane);
            }
        } finally {
            cascadingSugarcane.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onSugarcanePhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() != Material.SUGAR_CANE) return;

        Block base = e.getBlock();
        Block below = base.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.SUGAR_CANE) return;
        if (hasAdjacentWaterOrWaterlogged(below)) return;

        Block b = base;
        while (b.getType() == Material.SUGAR_CANE) {
            b.setType(Material.AIR, false);
            b = b.getRelative(BlockFace.UP);
        }

        e.setCancelled(true);
    }

    private boolean hasAdjacentWaterOrWaterlogged(Block block) {
        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block adj = block.getRelative(face);
            if (adj.getType() == Material.WATER) return true;
            org.bukkit.block.data.BlockData data = adj.getBlockData();
            if (data instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
        }
        return false;
    }

    @EventHandler
    public void onHarvestGlowBerries(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick() || e.getHand() == EquipmentSlot.OFF_HAND) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Player player = e.getPlayer();

        Material type = clicked.getType();
        if (type != Material.CAVE_VINES && type != Material.CAVE_VINES_PLANT) return;

        String data = clicked.getBlockData().getAsString();
        if (!data.contains("berries=true")) return;
        if(player.isSneaking()){
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player);
        if (cp != null) {
            cp.addSkillXp(SkillType.FARMER, 1);
        }
    }

    @EventHandler
    public void onMilk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.MILK_BUCKET) return;

        Bukkit.getScheduler().runTaskLater(
                com.minecraftcivilizations.specialization.Specialization.getInstance(),
                () -> {
                    CustomPlayer cp = CoreUtil.getPlayer(e.getPlayer());
                    if (cp != null) {
//                        cp.applyEffects();
                    }
                },
                1L
        );
    }

    @EventHandler
    public void onAnvilFinish(InventoryClickEvent e) {
        if (e.getView() instanceof AnvilView view) {
            String renameText = view.getRenameText();
            if (renameText != null && renameText.matches("^\\[lore [0-9]].*")) {
                CustomPlayer player = CoreUtil.getPlayer(e.getWhoClicked());
                int level = SpecializationConfig.getLibrarianConfig().get("ITEM_LORE_LIBRARIAN_LEVEL", Integer.class);
                if (player.getSkillLevel(SkillType.LIBRARIAN) < level) return;

                int number = Integer.parseInt(String.valueOf(renameText.charAt(6)));
                ItemStack result = view.getTopInventory().getResult();
                if (result == null) return;
                ItemStack oldItem = view.getTopInventory().getFirstItem();
                if (oldItem.hasData(DataComponentTypes.CUSTOM_NAME))
                    result.setData(DataComponentTypes.CUSTOM_NAME, view.getTopInventory().getFirstItem().getData(DataComponentTypes.CUSTOM_NAME));
                else {
                    result.unsetData(DataComponentTypes.CUSTOM_NAME);
                }
                ArrayList<Component> lines = new ArrayList<>(result.getData(DataComponentTypes.LORE).lines());
                if (lines.size() < number) {
                    for (int i = 0; i < number - lines.size() + 1; i++) lines.add(Component.empty());
                }
                lines.set(number - 1, Component.text(renameText.substring(8).trim()));

                result.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
            }
        }
    }

    @EventHandler
    public void anvilRenameEvent(InventoryClickEvent e) {
        if (e.getView() instanceof AnvilView view) {
            String renameText = view.getRenameText();
            if (renameText != null && renameText.matches("^\\[lore [0-9]]")) {
                int number = renameText.charAt(7);
                ItemStack result = view.getTopInventory().getResult();
                result.unsetData(DataComponentTypes.CUSTOM_NAME);
                result.getData(DataComponentTypes.LORE).lines().add(number + 1, Component.text(renameText.substring(8)));
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBucket().equals(Material.LAVA_BUCKET)) {
            CustomPlayer player = CoreUtil.getPlayer(e);
            if (player.getSkillLevel(SkillType.BLACKSMITH) < SkillLevel.EXPERT.getLevel()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (e.getBucket().equals(Material.LAVA_BUCKET)) {
            CustomPlayer player = CoreUtil.getPlayer(e);
            if (player.getSkillLevel(SkillType.BLACKSMITH) < SkillLevel.EXPERT.getLevel()) e.setCancelled(true);
        }
    }
}
