package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.SmartEntity.SmartEntity;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import static com.minecraftcivilizations.specialization.util.MathUtils.*;

import java.util.*;

/**
 * @author JFrogy
 */

public class XpTransferBookListener implements Listener {


    private static final float VOLUME = 1;
    private static final float PITCH = 1;
    private static final float PITCH_VARIANCE = 0.1f;

    private static final Map<UUID, Long> lastSignTime = new HashMap<>();
    private static final NamespacedKey XP_BLESSED_KEY    = new NamespacedKey(Specialization.getInstance(), "xp_blessed_book");
    private static final NamespacedKey XP_AMOUNT_KEY     = new NamespacedKey(Specialization.getInstance(), "xp_amount");
    private static final NamespacedKey XP_MAX_LEVELS_KEY = new NamespacedKey(Specialization.getInstance(), "xp_max_levels");

    private void applyBookInstructions(BookMeta book_meta, int max_levels) {
        book_meta.setPages(List.of("""
                §l   -XP Transfer-
                §8     Sign To Confirm
                §3    (Max of %d Levels)



                §5§l§nEnter Levels:§r """.formatted(max_levels)));
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneakRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;


        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();


        // --- Craft XP transfer book ---
        if (player.isSneaking() && item.getType() == Material.WRITABLE_BOOK) {
            event.setCancelled(true);

            if (item.hasItemMeta() && item.getItemMeta() instanceof BookMeta m) {
                if (m.getPersistentDataContainer().has(XP_BLESSED_KEY)) {
                    PlayerUtil.message(player, "This book has already been crafted.");
                    return;
                }
            }

            CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
            if (cp.getSkillLevel(SkillType.LIBRARIAN) < 3) {
                PlayerUtil.message(player, "You must be a Librarian level 3 to craft XP Transfer Books.");
                return;
            }

            BookMeta book_meta = (BookMeta) item.getItemMeta();
            if (book_meta == null) return;

            if (book_meta.hasPages()) {
                return; // Prevents wiping existing books
            }

            int librarianLevel = cp.getSkillLevel(SkillType.LIBRARIAN);
            int maxLevels = 3 * librarianLevel;

            book_meta.displayName(Component.text("XP Transfer Book").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            book_meta.lore(List.of(
                    Component.text("Crafted by " + player.getName()).color(NamedTextColor.GRAY),
                    Component.text("Write an amount and sign to store XP.").color(NamedTextColor.DARK_AQUA),
                    Component.text("Max of " + maxLevels + " levels").color(NamedTextColor.DARK_RED)
            ));
            applyBookInstructions(book_meta, maxLevels);
            book_meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            book_meta.setEnchantmentGlintOverride(true);
            book_meta.getPersistentDataContainer().set(XP_BLESSED_KEY, PersistentDataType.INTEGER, 1);
            book_meta.getPersistentDataContainer().set(XP_MAX_LEVELS_KEY, PersistentDataType.INTEGER, maxLevels);
            item.setItemMeta(book_meta);

            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.AMBIENT, VOLUME, PITCH + random(-PITCH_VARIANCE, PITCH_VARIANCE));

            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> player.closeInventory());
            return;
        }

        // --- Redeem stored XP ---
        if (player.isSneaking() && item.getType() == Material.BOOK) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            Integer xp = meta.getPersistentDataContainer().get(XP_AMOUNT_KEY, PersistentDataType.INTEGER);
            if (xp == null || xp <= 0) return;

            event.setCancelled(true);
            player.giveExp(xp);
            ItemStack is = player.getInventory().getItemInMainHand();
            is.setAmount(is.getAmount()-1);

            Debug.broadcast("xpbook", "<white>"+player.getName()+" consumed an XP book of <green>"+xp+"<white> xp");
            new SmartEntitySpiral(player, xp);
        }
    }

    @EventHandler
    public void onPlayerSignXpBook(PlayerEditBookEvent event) {

        Player player = event.getPlayer();
        ItemStack book = player.getInventory().getItemInMainHand();
        if (book == null || book.getType() != Material.WRITABLE_BOOK) return;


        BookMeta old_book_meta = (BookMeta) book.getItemMeta();
        if (old_book_meta == null) return;
        Integer blessed = old_book_meta.getPersistentDataContainer().get(XP_BLESSED_KEY, PersistentDataType.INTEGER);
        if (blessed == null || blessed != 1) return;

        event.setCancelled(true); // --- Prevent default written book creation

        // Max levels locked in by the crafter's librarian level at craft time
        Integer stored_max = old_book_meta.getPersistentDataContainer().get(XP_MAX_LEVELS_KEY, PersistentDataType.INTEGER);
        int max_levels = stored_max != null ? stored_max : 3; // fallback for legacy books

        if (!event.isSigning()){
            applyBookInstructions(old_book_meta, max_levels);
            return;
         }

        long now = System.currentTimeMillis();
        if (now - lastSignTime.getOrDefault(player.getUniqueId(), 0L) < 1000L) {
            PlayerUtil.message(player, "<red>Wait a moment before signing another book.");
            return;
        }
        lastSignTime.put(player.getUniqueId(), now);

        BookMeta meta = event.getNewBookMeta();
        List<String> pages = meta.getPages();
        if (pages.isEmpty()) {
            PlayerUtil.message(player, "<red>bruv what are you doing?");
            return;
        }


        String[] page_split = pages.getFirst().split("Levels:");
        if(page_split.length<2){
            return;
        }
        String number_string = page_split[1].replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip();
        int requestedLevels;
        try{
            requestedLevels = Math.max(0, Integer.valueOf(number_string));
        }catch(NumberFormatException e){
            PlayerUtil.message(player, "<red>Invalid number entered: " + number_string);
            return;
        }

        if(requestedLevels > max_levels){
            requestedLevels = max_levels;
        }

        int playerLevel = player.getLevel();
        if (requestedLevels > playerLevel) requestedLevels = playerLevel;

        int currentXp = getTotalXpForLevel(playerLevel);
        int targetXp = getTotalXpForLevel(playerLevel - requestedLevels);
        int totalXp = currentXp - targetXp;

        //Check if any xp is being transfered
        if(totalXp<=0){
            PlayerUtil.message(player, "<red>You have no xp to transfer. Go play the game.");
            return;
        }

        // this temporarily stores the metadata to be transfered into the new book meta later
        meta.displayName(Component.text("Tome of Knowledge with " + totalXp + " XP").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        player.setTotalExperience(targetXp);
        player.setLevel(playerLevel - requestedLevels);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_INSERT, SoundCategory.AMBIENT, 0.6f, PITCH +random(-PITCH_VARIANCE, PITCH_VARIANCE));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 0.1f, PITCH +-0.4f +random(-PITCH_VARIANCE, PITCH_VARIANCE));
        // --- Replace writable book with enchanted XP book (one tick later) ---
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            finalizeBook(meta, totalXp, player);
        });
    }


    private static void  finalizeBook(BookMeta old_meta, int totalXp, Player player) {
        ItemStack xpBook = new ItemStack(Material.BOOK);
        ItemMeta xpMeta = xpBook.getItemMeta();

        Debug.broadcast("xpbook", "<white>Xp Book created by <yellow>"+player.getName()+"<yellow> with <green>"+totalXp+"<white> xp");

        if (player.getInventory().getItemInMainHand().getType() != Material.WRITABLE_BOOK){
            PlayerUtil.message(player, "<green>Stop trying to exploit nerd...");
            return;
        }


        xpMeta.displayName(old_meta.displayName());
        xpMeta.lore(List.of(
                Component.text("Shift-right-click to absorb.").color(NamedTextColor.GRAY),
                Component.text(old_meta.getTitle() != null ? old_meta.getTitle() : "").color(NamedTextColor.LIGHT_PURPLE),
                Component.text("Signed by: ").color(NamedTextColor.WHITE)
                        .append(Component.text(player.getName()).color(NamedTextColor.GOLD))
        ));
        // ChatColor.GOLD+event.getNewBookMeta().getTitle())
        xpMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        xpMeta.setEnchantmentGlintOverride(true);
        xpMeta.getPersistentDataContainer().set(XP_AMOUNT_KEY, PersistentDataType.INTEGER, totalXp);
        xpBook.setItemMeta(xpMeta);

        player.getInventory().setItemInMainHand(xpBook);
        player.updateInventory();
    }

    private static int getTotalXpForLevel(int level) {
        if (level <= 16) return (int) (Math.pow(level, 2) + 6 * level);
        if (level <= 31) return (int) (2.5 * Math.pow(level, 2) - 40.5 * level + 360);
        return (int) (4.5 * Math.pow(level, 2) - 162.5 * level + 2220);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastSignTime.remove(event.getPlayer().getUniqueId());
    }


    /**
     * Spiral effect
     * @author Alectriciti
     */
    class SmartEntitySpiral extends SmartEntity{

        double height = 0;
        double radius = 1;
        int experience;
        int scaled_exp;

        public SmartEntitySpiral(Entity owner, int exp){
            super(owner, owner.getLocation());
            this.experience = exp;
            this.scaled_exp = Math.min((experience/10)+10, 50);
        }

        @Override
        public void update() {
            Location old_location = location.clone();
            radius += -0.0125;
            height += 0.05;
            double x = Math.sin(height*32)*radius;
            double z = Math.cos(height*32)*radius;

            if(owner!=null) {
                location = owner.getLocation().add(x, height, z);
            }

            int particles = (int)(scaled_exp/2);
            location.getWorld().spawnParticle(Particle.ENCHANT, lerpLocationFast(old_location, location,0.5f), 3, 0,0,0);
            location.getWorld().spawnParticle(Particle.ENCHANT, location, 3, 0,0,0);

            if(tick % 4 == 0) {
                float pitch = 0.6f+((float)tick/40f);
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.1f, pitch);
            }
//            location.getWorld().spawnParticle
            if(tick > scaled_exp){
                destroy();
            }
        }
    }

}
