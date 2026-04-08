package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassXpBookListener implements Listener {

    private static NamespacedKey CLASS_XP_BOOK_CAPACITY_KEY;
    private static NamespacedKey CLASS_XP_BOOK_STORED_XP_KEY;
    private static NamespacedKey CLASS_XP_BOOK_SKILL_KEY;

    // recipe key → capacity (e.g. class_xp_book_500 → 500)
    private static final Map<NamespacedKey, Integer> RECIPE_CAPACITIES = new LinkedHashMap<>();

    public static void init() {
        Specialization plugin = Specialization.getInstance();
        CLASS_XP_BOOK_CAPACITY_KEY  = new NamespacedKey(plugin, "class_xp_book_capacity");
        CLASS_XP_BOOK_STORED_XP_KEY = new NamespacedKey(plugin, "class_xp_book_stored_xp");
        CLASS_XP_BOOK_SKILL_KEY     = new NamespacedKey(plugin, "class_xp_book_skill");

        int[] capacities = {500, 1000, 2000, 4000};
        for (int i = 0; i < capacities.length; i++) {
            int cap = capacities[i];
            NamespacedKey key = new NamespacedKey(plugin, "class_xp_book_" + cap);
            RECIPE_CAPACITIES.put(key, cap);

            ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(Material.WRITABLE_BOOK));
            recipe.addIngredient(Material.WRITABLE_BOOK);
            for (int j = 0; j <= i; j++) {
                recipe.addIngredient(Material.EMERALD);
            }
            Bukkit.addRecipe(recipe);
        }
    }

    // Fires early so CraftingListener (NORMAL) can still block it if skill requirement unmet
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe sr)) return;
        Integer capacity = RECIPE_CAPACITIES.get(sr.getKey());
        if (capacity == null) return;

        ItemStack result = new ItemStack(Material.WRITABLE_BOOK);
        result.editMeta(BookMeta.class, m -> {
            m.setDisplayName(ChatColor.AQUA + "Class XP Book");
            m.setLore(List.of(
                    ChatColor.GRAY + "Capacity: " + capacity + " CXP",
                    ChatColor.DARK_AQUA + "Write a class name and sign to seal CXP.",
                    ChatColor.DARK_RED + "Irreversible!"
            ));
            m.addPage(buildBookTemplate(capacity));
            m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(CLASS_XP_BOOK_CAPACITY_KEY, PersistentDataType.INTEGER, capacity);
        });
        event.getInventory().setResult(result);
    }

    @EventHandler
    public void onPlayerSignClassXpBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        ItemStack book = player.getInventory().getItemInMainHand();
        if (book.getType() != Material.WRITABLE_BOOK || !book.hasItemMeta()) return;

        Integer capacity = book.getItemMeta().getPersistentDataContainer()
                .get(CLASS_XP_BOOK_CAPACITY_KEY, PersistentDataType.INTEGER);
        if (capacity == null) return;

        event.setCancelled(true); // prevent vanilla written_book creation
        if (!event.isSigning()) return;

        // Parse class name from signed page
        List<String> pages = event.getNewBookMeta().getPages();
        if (pages.isEmpty()) {
            PlayerUtil.message(player, ChatColor.RED + "The book is empty.");
            return;
        }

        String[] split = pages.getFirst().split("Class:");
        if (split.length < 2) {
            PlayerUtil.message(player, ChatColor.RED + "No class found. Write a class name after 'Class:'");
            return;
        }

        String classNameRaw = ChatColor.stripColor(split[1]).strip().toUpperCase();

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(classNameRaw);
        } catch (IllegalArgumentException e) {
            PlayerUtil.message(player, ChatColor.RED + "Unknown class: '" + classNameRaw
                    + "'. Valid: FARMER, BUILDER, MINER, HEALER, LIBRARIAN, GUARDSMAN, BLACKSMITH");
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
        if (cp == null) return;

        double currentXp = cp.getSkill(skillType).getXp();
        if (currentXp < capacity) {
            PlayerUtil.message(player, ChatColor.RED + "Not enough " + SkillType.getDisplayName(skillType)
                    + " XP. You have " + (int) currentXp + " but need " + capacity + ".");
            return;
        }

        // Deduct CXP and seal the book
        cp.addSkillXp(skillType, -capacity, null, true, false);

        final SkillType finalSkill = skillType;
        final int finalCapacity = capacity;
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.WRITABLE_BOOK) return;

            ItemStack filledBook = new ItemStack(Material.BOOK);
            filledBook.editMeta(m -> {
                m.setDisplayName(ChatColor.LIGHT_PURPLE + "Class XP: "
                        + SkillType.getDisplayName(finalSkill) + " (" + finalCapacity + ")");
                m.setLore(List.of(
                        ChatColor.GRAY + "Class: " + SkillType.getDisplayName(finalSkill),
                        ChatColor.DARK_AQUA + "Contains " + finalCapacity + " CXP",
                        ChatColor.YELLOW + "Shift-right-click to absorb.",
                        ChatColor.WHITE + "Signed by: " + ChatColor.GOLD + player.getName()
                ));
                m.setEnchantmentGlintOverride(true);
                m.getPersistentDataContainer().set(CLASS_XP_BOOK_STORED_XP_KEY,
                        PersistentDataType.INTEGER, finalCapacity);
                m.getPersistentDataContainer().set(CLASS_XP_BOOK_SKILL_KEY,
                        PersistentDataType.STRING, finalSkill.name());
            });

            player.getInventory().setItemInMainHand(filledBook);
            player.updateInventory();
        });
    }

    @EventHandler
    public void onAbsorbClassXpBook(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.BOOK || !item.hasItemMeta()) return;

        var pdc = item.getItemMeta().getPersistentDataContainer();
        Integer storedXp = pdc.get(CLASS_XP_BOOK_STORED_XP_KEY, PersistentDataType.INTEGER);
        String skillName = pdc.get(CLASS_XP_BOOK_SKILL_KEY, PersistentDataType.STRING);
        if (storedXp == null || skillName == null) return;

        event.setCancelled(true);

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(skillName);
        } catch (IllegalArgumentException e) {
            PlayerUtil.message(player, ChatColor.RED + "This book contains invalid data.");
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
        if (cp == null) return;

        cp.addSkillXp(skillType, storedXp);
        item.setAmount(item.getAmount() - 1);
    }

    private static String buildBookTemplate(int capacity) {
        return "§l  -Class XP Book-\n§8  Sign To Confirm\n§3 Capacity: " + capacity + " CXP\n\n\n\n§5§l§nEnter Class:§r ";
    }
}
