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

import static com.minecraftcivilizations.specialization.util.MathUtils.*;
import static org.bukkit.ChatColor.*;

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

            book_meta.setDisplayName(ChatColor.AQUA + "XP Transfer Book");
            book_meta.setLore(List.of(
                    ChatColor.GRAY + "Crafted by " + player.getName(),
                    ChatColor.DARK_AQUA + "Write an amount and sign to store XP.",
                    ChatColor.DARK_RED + "Max of " + maxLevels + " levels"
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

            Debug.broadcast("xpbook", WHITE+player.getName()+" consumed an XP book of "+GREEN+xp+WHITE+" xp");
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
            PlayerUtil.message(player,ChatColor.RED + "Wait a moment before signing another book.");
            return;
        }
        lastSignTime.put(player.getUniqueId(), now);

        BookMeta meta = event.getNewBookMeta();
        List<String> pages = meta.getPages();
        if (pages.isEmpty()) {
            PlayerUtil.message(player,ChatColor.RED + "bruv what are you doing?");
            return;
        }


        String[] page_split = pages.getFirst().split("Levels:");
        if(page_split.length<2){
            return;
        }
        String number_string = ChatColor.stripColor(page_split[1]).strip();
        int requestedLevels;
        try{
            requestedLevels = Math.max(0, Integer.valueOf(number_string));
        }catch(NumberFormatException e){
            PlayerUtil.message(player,ChatColor.RED + "Invalid number entered: "+number_string);
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
            PlayerUtil.message(player,ChatColor.RED+"You have no xp to transfer. Go play the game.");
            return;
        }

        // this temporarily stores the metadata to be transfered into the new book meta later
        meta.setDisplayName(ChatColor.RESET+""+ChatColor.AQUA+"Tome of Knowledge with " + totalXp + " XP");

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

        Debug.broadcast("xpbook", WHITE+"Xp Book created by "+YELLOW+player.getName()+YELLOW+" with "+GREEN+totalXp+WHITE+" xp");

        if (player.getInventory().getItemInMainHand().getType() != Material.WRITABLE_BOOK){
            PlayerUtil.message(player,GREEN + "Stop trying to exploit nerd...");
            return;
        }


        xpMeta.setDisplayName(old_meta.getDisplayName());
//            xpMeta.setDisplayNae(ChatColor.LIGHT_PURPLE + "Stored XP Book");
        xpMeta.setLore(List.of(
                        GRAY + "Shift-right-click to absorb.",
                    LIGHT_PURPLE+old_meta.getTitle(),
    //                DARK_PURPLE + "Contains " + totalXp + " XP",
                    WHITE + "Signed by: " + GOLD+player.getName())
        );
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
