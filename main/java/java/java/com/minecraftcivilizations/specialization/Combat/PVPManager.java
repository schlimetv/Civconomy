package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Listener.Player.PlayerDownedListener;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Jfrogy
 */

/**
 * This class handles detecting combat with visual indicators informing the player and handling combat logging in a persistent manner
 * <p>
 * I utilize an invisible armor stand to store players inventory and health/state on logout.
 * The visual representation of this is a zombie which I call a mannequin.
 * <p>
 * Mannequin acts as a listener for damage/death and then relays that info to the armorstand each damage event.
 * If the mannequin is not killed it returns the inventory to the player on relog  with the corresponding health the zombie has remaining.
 * If the mannequin dies the inventory which is stored on the armor stand is fetched and deserilized and dropped.
 * This is only restored if the player is not marked for death on rejoin.
 * The armorstand is always stored on combat log in the world forever. It is also always destroyed after the data fetch regardless of death/life on join.
 * <p>
 * This utilizes the PlayerDownListener for down state checks.
 */

public class PVPManager implements Listener, CommandExecutor {

    private static final long COMBAT_COOLDOWN = 30_000L;
    private static final long ZOMBIE_LIFETIME = 15_000L; // 15s
    private final JavaPlugin plugin;
    public final Map<UUID, Long> combatMap = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> zombieMap = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> zombieTimers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> combatBars = new HashMap<>();
    private final NamespacedKey OWNER_KEY;
    private final NamespacedKey MARKER_KEY;
    private final NamespacedKey INVENTORY_KEY;
    private final NamespacedKey ARMOR_KEY;
    private final NamespacedKey HEALTH_KEY;
    private final NamespacedKey DEAD_KEY;
    private final PlayerDownedListener playerDownedListener;
    private boolean combatTaskRunning = false;
    private int combatTaskId = -1;

    public boolean isInCombat(Player player){
        return combatMap.containsKey(player.getUniqueId());
    }

    public PVPManager(PlayerDownedListener playerDownedListener, JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDownedListener = playerDownedListener;
        this.OWNER_KEY = new NamespacedKey(plugin, "owner");
        this.MARKER_KEY = new NamespacedKey(plugin, "pvp_marker");
        this.INVENTORY_KEY = new NamespacedKey(plugin, "inv");
        this.ARMOR_KEY = new NamespacedKey(plugin, "armor");
        this.HEALTH_KEY = new NamespacedKey(plugin, "health");
        this.DEAD_KEY = new NamespacedKey(plugin, "dead");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("simulatehit")).setExecutor(this);
    }

    // --- Detect combat ---
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player damager = getDamager(event.getDamager());
        if (damager == null || damager.equals(victim)) return;

        long now = System.currentTimeMillis();

        // Check global combat state for both players
        boolean victimAlreadyTagged = combatMap.containsKey(victim.getUniqueId());
        boolean damagerAlreadyTagged = combatMap.containsKey(damager.getUniqueId());

        // Tag both
        combatMap.put(victim.getUniqueId(), now);
        combatMap.put(damager.getUniqueId(), now);
        addCombatBar(victim, null);
        addCombatBar(damager, victim);
        startCombatTaskIfNeeded();

        // Only send messages if BOTH were NOT tagged before
        if (!victimAlreadyTagged) {
//            victim.sendMessage("§0[§0§6CivLabs§0]§8 » §7You have been tagged for §ccombat §7for §b"
//                    + (COMBAT_COOLDOWN / 1000) + " §7seconds by: §c" + damager.getName());
            Specialization.message(victim ,"§cCombat§7 logging leaves your items on a §ckillable §aMannequin§7 for §c15s§7 before logging out safely.");

        }
        if (!damagerAlreadyTagged) {
//            damager.sendMessage("§0[§0§6CivLabs§0]§8 » §7You are tagged for §ccombat §7for §b" + (COMBAT_COOLDOWN / 1000) + " §7seconds");
            Specialization.message(damager, "§cCombat§7 logging leaves your items on a §ckillable §aMannequin§7 for §c15s§7 before logging out safely.");
        }


//        plugin.getLogger().info("<grey>[Combat] " + damager.getName() + " hit " + victim.getName());

        // Reset zombie timer if hit
        UUID victimId = victim.getUniqueId();
        UUID zombieId = zombieMap.get(victimId);
        if (zombieId != null) {
            BukkitRunnable timer = zombieTimers.get(victimId);
            if (timer != null) {
                timer.cancel();
                startZombieTimer(victimId, Bukkit.getEntity(zombieId));
            }
        }
    }

    private void addCombatBar(Player p, Player pvp_target) {
        BossBar bar = combatBars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar("§7Combat-Log Timer", BarColor.RED, BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            if(pvp_target!=null) {
                Debug.broadcast("combat", p.getName() + "<gray> entered combat with</gray> "+pvp_target.getName());
            }
            combatBars.put(p.getUniqueId(), bar);
        }
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        bar.setVisible(true);
    }

    private void startCombatTaskIfNeeded() {
        if (combatTaskRunning) return;
        if (combatMap.isEmpty()) return;

        combatTaskRunning = true;

        combatTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, Long>> it = combatMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID uuid = entry.getKey();
                long start = entry.getValue();
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;

                long elapsed = now - start;
                long remaining = COMBAT_COOLDOWN - elapsed;

                if (remaining <= 0) {
                    it.remove();
                    Specialization.message(p,"You may §bsafely§7 log out");
                    BossBar bar = combatBars.remove(uuid);
                    if (bar != null) bar.removeAll();

                    continue;
                }

                BossBar bar = combatBars.get(uuid);
                if (bar != null) {
                    double progress = Math.max(0, (double) remaining / COMBAT_COOLDOWN);
                    bar.setProgress(progress);
                }
            }

            // Stop if empty
            if (combatMap.isEmpty()) {
                Bukkit.getScheduler().cancelTask(combatTaskId);
                combatTaskRunning = false;
            }

        }, 1L, 1L);
    }

    private Player getDamager(Entity source) {
        if (source instanceof Player p) return p;
        if (source instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        combatMap.remove(id);
        PlayerUtil.message(event.getPlayer(),"You may §bsafely§7 log out");
        BossBar bar = combatBars.remove(id);
        if (bar != null) bar.removeAll();
    }

    // --- Player logout ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        Long lastHit = combatMap.get(id);
        if (lastHit == null || System.currentTimeMillis() - lastHit > COMBAT_COOLDOWN) return;

        Debug.broadcast("combatlog", "<grey>[Logout] " + player.getName() + " logged out in combat!");
        Location loc = player.getLocation().clone().add(0, 1, 0);
        // Create marker armor stand
        ArmorStand marker = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setMarker(false);
            as.setGravity(true);
            as.setPersistent(true);
            as.setInvulnerable(true);
            as.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, id.toString());
            as.getPersistentDataContainer().set(DEAD_KEY, PersistentDataType.INTEGER, 0);
            as.getAttribute(Attribute.SCALE).setBaseValue(0.01);

            // Serialize only main inventory (slots 0-35)
            ItemStack[] mainInv = new ItemStack[36];
            System.arraycopy(player.getInventory().getContents(), 0, mainInv, 0, 36);
            as.getPersistentDataContainer().set(INVENTORY_KEY, PersistentDataType.BYTE_ARRAY, ItemSerialization.toBytes(mainInv));

            // Serialize armor separately
            as.getPersistentDataContainer().set(ARMOR_KEY, PersistentDataType.BYTE_ARRAY, ItemSerialization.toBytes(player.getInventory().getArmorContents()));

            // Serialize health
            as.getPersistentDataContainer().set(HEALTH_KEY, PersistentDataType.DOUBLE, player.getHealth());
        });

        // Spawn zombie with marker reference
        Zombie zombie = spawnCombatZombie(player, marker);

        zombieMap.put(id, zombie.getUniqueId());
        zombie.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, id.toString());
        zombie.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        combatBars.remove(id);
        Debug.broadcast("combatlog", "<grey>[Logout] Spawned zombie for " + player.getName());

        player.getInventory().clear();
        startZombieTimer(id, zombie);
        if (playerDownedListener.isDowned(player)) {
            marker.addPassenger(zombie);
        }
    }

    private void startZombieTimer(UUID playerId, Entity zombie) {
        if (zombie == null || !zombie.isValid()) {
            System.out.println("<grey>[ZombieTimer] Zombie is null or invalid for player " + playerId);
            return;
        }

        System.out.println("<grey>[ZombieTimer] Scheduling despawn for zombie " + zombie.getUniqueId() + " of player " + playerId);

        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                if (zombie == null || !zombie.isValid()) {
                    System.out.println("<grey>[ZombieTimer] Zombie already invalid when timer ran for player " + playerId);
                } else {
                    System.out.println("<grey>[ZombieTimer] Removing zombie " + zombie.getUniqueId() + " for player " + playerId);
                    zombie.remove();
                }

                zombieMap.remove(playerId);
                zombieTimers.remove(playerId);
                System.out.println("<grey>[ZombieTimer] Timer cleaned up for player " + playerId);
            }
        };

        timer.runTaskLater(plugin, ZOMBIE_LIFETIME / 50L);
        zombieTimers.put(playerId, timer);
    }


    private ArmorStand getMarkerByPlayer(UUID playerId, Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (e instanceof ArmorStand as) {
                String owner = as.getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.STRING);
                if (owner != null && owner.equals(playerId.toString())) return as;
            }
        }
        return null;
    }


    // --- Player join ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        // --- Handle marker first ---
        Chunk spawnChunk = player.getLocation().getChunk();
        ArmorStand marker = getMarkerByPlayer(id, spawnChunk);
        if (marker == null) {
            Debug.broadcast("combatlog", "<grey>[Login] No marker found for " + player.getName());
            return;
        }

        Debug.broadcast("combatlog", "<grey>[Login] Marker found: " + marker.getName() + " for " + player.getName());

        int deadFlag = marker.getPersistentDataContainer().getOrDefault(DEAD_KEY, PersistentDataType.INTEGER, 0);
        byte[] invBytes = marker.getPersistentDataContainer().get(INVENTORY_KEY, PersistentDataType.BYTE_ARRAY);
        byte[] armorBytes = marker.getPersistentDataContainer().get(ARMOR_KEY, PersistentDataType.BYTE_ARRAY);

        if (deadFlag == 1) {
            //Death
            Debug.broadcast("combatlog", "<grey>[Login] Player " + player.getName() + " died while logged out in combat!");
            player.getInventory().clear();
            if (invBytes != null)
                for (ItemStack item : ItemSerialization.fromBytes(invBytes))
                    if (item != null) player.getWorld().dropItemNaturally(player.getLocation(), item);
            if (armorBytes != null)
                for (ItemStack item : ItemSerialization.fromBytes(armorBytes))
                    if (item != null) player.getWorld().dropItemNaturally(player.getLocation(), item);

            player.setHealth(0);
            PlayerUtil.message(player, "You §ccombat logged§7, and your §cmannequin§7 was §ckilled§7 before it could safely logout");
        } else {
            //Life
            if (invBytes != null) player.getInventory().setContents(ItemSerialization.fromBytes(invBytes));
            if (armorBytes != null) player.getInventory().setArmorContents(ItemSerialization.fromBytes(armorBytes));
            double health = marker.getPersistentDataContainer().getOrDefault(HEALTH_KEY, PersistentDataType.DOUBLE, player.getMaxHealth());
            player.setHealth(Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
            PlayerUtil.message(player,"You §ccombat-logged§7, but your mannequin §asurvived");
            Debug.broadcast("combatlog", "<grey>[Login] Restored inventory and health(" + health + ") for " + player.getName());
        }

//         --- Remove Mannequin Regardless ---
        UUID zombieId = zombieMap.remove(id);
        Entity zombie = (zombieId != null) ? Bukkit.getEntity(zombieId) : null;
        BukkitRunnable timer = zombieTimers.remove(id);
        if (timer != null) timer.cancel();

        //only scans if zombie cant be found in map (crash/servershutdown or some sort of weird issue)
        if (zombie == null && marker.isValid()) {
            // Only scan the chunk where the marker is
            Chunk chunk = marker.getLocation().getChunk();
            for (Entity e : chunk.getEntities()) {
                if (e instanceof Zombie z) {
                    String ownerStr = z.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
                    if (ownerStr != null && ownerStr.equals(id.toString())) {
                        zombie = z;
                        break;
                    }
                }
            }
        }

        Long lastHit = combatMap.get(id);
        if (lastHit != null && System.currentTimeMillis() - lastHit < COMBAT_COOLDOWN) {
            // Temporarily remove and re-add to ensure the bar updates
            combatMap.remove(id);
            combatMap.put(id, lastHit);

            addCombatBar(player, null);
            startCombatTaskIfNeeded();

            PlayerUtil.message(player,"You are still in §ccombat §7for §b"
                    + ((COMBAT_COOLDOWN - (System.currentTimeMillis() - lastHit)) / 1000) + " §7seconds");
        }

        if (zombie != null && zombie.isValid()) {
            zombie.remove();
            Debug.broadcast("combatlog", ("<grey>[Login] Removed leftover zombie for " + player.getName()));
        }


        marker.remove();
    }


    // --- Zombie damage updates marker ---
    @EventHandler
    public void onZombieDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Zombie zombie)) return;

        // Check if zombie is tracked
        String ownerStr = zombie.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (ownerStr == null) return; // Not a combat zombie

        UUID ownerId = UUID.fromString(ownerStr);

        // If zombie is not in map, remove it immediately
        if (!zombieMap.containsKey(ownerId)) {
            Debug.broadcast("combatlog", "<grey>[ZombieDamage] Untracked zombie detected for player " + ownerId + ", removing it");
            event.setCancelled(true);
            zombie.remove();
            return;
        }

        // Reset the zombie despawn timer
        BukkitRunnable oldTimer = zombieTimers.get(ownerId);
        if (oldTimer != null) oldTimer.cancel();
        startZombieTimer(ownerId, zombie);

        // Otherwise, update marker
        String playerUUIDStr = zombie.getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.STRING);
        if (playerUUIDStr == null) return;

        ArmorStand marker = getMarkerByPlayer(UUID.fromString(playerUUIDStr), zombie.getChunk());
        if (marker == null) return;

        double currentHealth = zombie.getHealth();
        marker.getPersistentDataContainer().set(HEALTH_KEY, PersistentDataType.DOUBLE, currentHealth);

        zombie.getEquipment();
        marker.getPersistentDataContainer().set(ARMOR_KEY, PersistentDataType.BYTE_ARRAY,
                ItemSerialization.toBytes(zombie.getEquipment().getArmorContents()));
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
        Debug.broadcast("combatlog", "<grey>[ZombieDamage] Zombie " + zombie.getCustomName() +
                " took damage, health updated to <red>" + Debug.formatDecimal(currentHealth) + "</red> in marker");
    }


    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;

        PersistentDataContainer pdc = zombie.getPersistentDataContainer();
        if (!pdc.has(OWNER_KEY)) return;

        // Early exit: zombie has no owner => clear drops and return
        if (!pdc.has(OWNER_KEY, PersistentDataType.STRING)) {
            zombie.getEquipment().clear();
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        String raw = pdc.get(OWNER_KEY, PersistentDataType.STRING);
        UUID ownerId;

        // PDC contains invalid UUID => treat as unbound zombie (no drops)
        try {
            ownerId = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            zombie.getEquipment().clear();
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        ArmorStand marker = getMarkerByPlayer(ownerId, zombie.getChunk());

        // If no marker exists => NO DROPS
        if (marker == null) {
            zombie.getEquipment().clear();
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        // --- VALID MARKER FOUND: handle full logic ---
        event.getDrops().clear();
        zombie.getEquipment().clear();
        event.setDroppedExp(0);
        marker.getPersistentDataContainer().set(DEAD_KEY, PersistentDataType.INTEGER, 1);

        // Drop inventory
        byte[] invBytes = marker.getPersistentDataContainer().get(INVENTORY_KEY, PersistentDataType.BYTE_ARRAY);
        if (invBytes != null) {
            Debug.broadcast("combatlog", "<grey>[Mannequin Death] Inventory Detected...");
            for (ItemStack item : ItemSerialization.fromBytes(invBytes)) {
                if (item != null) {
                    zombie.getWorld().dropItemNaturally(zombie.getLocation(), item);
                    Debug.broadcast("combatlog", "<grey>[Mannequin Item]: " + item.getItemMeta().displayName());
                }
            }
            marker.getPersistentDataContainer().remove(INVENTORY_KEY);
        }

        // Drop armor
        byte[] armorBytes = marker.getPersistentDataContainer().get(ARMOR_KEY, PersistentDataType.BYTE_ARRAY);
        if (armorBytes != null) {
            for (ItemStack item : ItemSerialization.fromBytes(armorBytes)) {
                if (item != null) zombie.getWorld().dropItemNaturally(zombie.getLocation(), item);
            }
            marker.getPersistentDataContainer().remove(ARMOR_KEY);
        }

        zombieMap.remove(ownerId);
        BukkitRunnable timer = zombieTimers.remove(ownerId);
        if (timer != null) timer.cancel();

        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1f, 1f);
    }


    // --- Spawn combat zombie ---
    private Zombie spawnCombatZombie(Player player, ArmorStand marker) {
        if (marker == null) return null;

        // Deserialize stored inventory and armor
        byte[] invBytes = marker.getPersistentDataContainer().get(INVENTORY_KEY, PersistentDataType.BYTE_ARRAY);
        byte[] armorBytes = marker.getPersistentDataContainer().get(ARMOR_KEY, PersistentDataType.BYTE_ARRAY);
        double storedHealth = marker.getPersistentDataContainer().getOrDefault(HEALTH_KEY, PersistentDataType.DOUBLE, player.getHealth());

        Zombie zombie = (Zombie) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        zombie.setCustomName(player.getName());
        zombie.setCustomNameVisible(true);
        zombie.setPersistent(false);
        zombie.setAI(false);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(true);
        zombie.setShouldBurnInDay(false);
        zombie.setAge(0); //0 = adult | -100 = ticks until adult
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(player.getMaxHealth());
        zombie.setHealth(Math.min(storedHealth, zombie.getAttribute(Attribute.MAX_HEALTH).getValue()));

        // Set main-hand and off-hand from marker inventory
        if (invBytes != null) {
            ItemStack[] inv = ItemSerialization.fromBytes(invBytes);
            zombie.getEquipment().setItemInMainHand(player.getEquipment().getItemInMainHand());
            zombie.getEquipment().setItemInOffHand(player.getEquipment().getItemInOffHand());
        }

        // Set armor accurately from stored armor
        if (armorBytes != null) {
            ItemStack[] armor = ItemSerialization.fromBytes(armorBytes);
            zombie.getEquipment().setArmorContents(armor);
        } else {
            zombie.getEquipment().setArmorContents(player.getInventory().getArmorContents());
        }

        Debug.broadcast("combatlog", "<grey>[SpawnZombie] Spawned zombie for " + player.getName() +
                " | Health: " + zombie.getHealth() + "/" + zombie.getAttribute(Attribute.MAX_HEALTH).getValue());

        return zombie;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        combatMap.put(p.getUniqueId(), System.currentTimeMillis());
        Debug.broadcast("combatlog", "<grey>[Command] /simulatehit executed for " + p.getName());
//        p.sendMessage("§0[§0§6CivLabs§0]§8 » §7You are tagged for §ccombat §7for §b" + (COMBAT_COOLDOWN / 1000) + " §7seconds");
        PlayerUtil.message(p,"§cCombat§7 logging leaves your items on a §ckillable §aMannequin§7 for §c15s§7 before logging out safely.");
        addCombatBar(p, null);
        startCombatTaskIfNeeded();
        return true;
    }


    // --- Item serialization helper ---
    public static class ItemSerialization {

        public static byte[] toBytes(ItemStack[] items) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) dataOutput.writeObject(item);
                return outputStream.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public static ItemStack[] fromBytes(byte[] bytes) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                int length = dataInput.readInt();
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) items[i] = (ItemStack) dataInput.readObject();
                return items;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return new ItemStack[0];
            }
        }
    }
}
