package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Listener.Player.Inventories.SpecializationCraftItemEvent;
import com.minecraftcivilizations.specialization.Listener.Player.ReviveListener;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * The manager of the new Custom Item system
 * Think of this as the motherboard for Custom Items
 * It dispatches events to Custom Items
 * @author ⚡ alectriciti ⚡
 */
public class CustomItemManager implements Listener {

    static final NamespacedKey key_custom_item_id = new NamespacedKey("specialization", "custom_item_id");

    // primary registry of custom items, used by events
    private Map<String, CustomItem> custom_items_loaded = new HashMap<String, CustomItem>();

    // a simple list of all custom items, mostly for commands
    @Getter
    private List<CustomItem> customItems = new ArrayList<CustomItem>();

    // used by CustomItemCommand for Tab Completion
    @Getter
    private List<String> customItemIds = new ArrayList<String>();

    Specialization plugin;

    // items are defined and referenced here
    public DefineCustomItems definitions;

    public CustomItemManager(Specialization plugin ){
        this.plugin = plugin;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void initializeCustomItems(){
        custom_items_loaded = new HashMap<String, CustomItem>();
        customItemIds = new ArrayList<String>();
        definitions = new DefineCustomItems(plugin);
        for(CustomItem customItem : custom_items_loaded.values()){
            customItem.init();
        }
    }

    public static CustomItemManager getInstance() {
        return Specialization.getInstance().getCustomItemManager();
    }

    public static DefineCustomItems getDefinitions(){ return getInstance().definitions;}


    void registerItem(CustomItem custom_item) {
        Specialization.getInstance().getLogger().info("Registering Custom Item: "+custom_item.getId());
//        custom_items_to_register.add(customItem);
        custom_items_loaded.put(custom_item.getId(), custom_item); //used for event lookup
        customItems.add(custom_item); //used by commands (for item reference)
        customItemIds.add(custom_item.getId()); //used by commands (for tab completion)
    }



    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        CustomItem ci = getCustomItem(result);
        if (ci != null) {
//            Debug.broadcast("customitem", "prepare crafting custom item");
            Player player = (Player) event.getView().getPlayer();
            if (!ci.canPlayerCraft(player)) {
                // Hide the result
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCraftItem(CraftItemEvent event) {
        CustomItem ci = getCustomItem(event.getCurrentItem());
        if (ci != null) {
            Player player = (Player) event.getWhoClicked();
            if (!ci.canPlayerCraft(player)) {
                // Prevent crafting entirely
                event.setCancelled(true);
                player.sendMessage("§cYou cannot craft this item!");
            }
        }
    }


//    @EventHandler TODO merge with other branch
//    public void onLevelUpEvent(PlayerChangeLevelEvent event){
//      //display unlocked recipes
//    }

    /**
     * Called when disabling an item
     */
//    void unregisterItem(CustomItem customItem) {
//        custom_items_loaded.remove(customItem.getId());
//        customItemIds.remove(customItem.getId());
//    }


//    public void finalizeCustomItemRegistration() {
//        for(CustomItem item : custom_items_to_register){
//            if(item.isEnabled()){
//                custom_items_loaded.put(item.getId(), item);
//            }
//        }
//    }

    /**
     * The primary way to get a Custom Item via Interactions
     */
    public CustomItem getCustomItem(ItemStack item){
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if(meta!=null){
            String id = meta.getPersistentDataContainer().get(key_custom_item_id, PersistentDataType.STRING);
            if(id!=null){
                CustomItem ci = custom_items_loaded.get(id);
                return ci;
            }
        }
        return null;
    }

    /**
     * Gets Custom Item, used by commands
     */
    public CustomItem getCustomItem(String id){
        if(custom_items_loaded.containsKey(id)){
            return custom_items_loaded.get(id);
        }
        return null;
    }


    public boolean isCustomItem(ItemStack item_stack, String id){
        CustomItem custom_item = getCustomItem(item_stack);
        if(custom_item != null && custom_item.getId().equals(id)){
            return true;
        }
        return false;
    }

    public boolean isCustomItem(ItemStack item_stack, CustomItem comparing_custom_item){
        CustomItem custom_item = getCustomItem(item_stack);
        if(custom_item != null && custom_item == comparing_custom_item){
            return true;
        }
        return false;
    }


    public boolean disableItem(String id) {
        CustomItem item = custom_items_loaded.get(id);
        if (item == null) return false;
        item.setEnabled(false);
        return true;
    }

    /**
     * Returns if the item was null
     */
    public boolean enableItem(String id) {
        CustomItem item = custom_items_loaded.get(id);
        if(item!=null){
            if(item.isEnabled()) return false;
            item.setEnabled(true);
        }
        return false;
    }

    public void reloadItem(String id) {
        CustomItem item = custom_items_loaded.get(id);
        if (item == null) return;

        item.init();

        // Invalidate or rebuild ItemStack model data if needed.
        Specialization.getInstance().getLogger().info("Refreshed custom item: " + id);
    }

    @EventHandler
    public void DispatchOnItemCreation(CustomItemCreationEvent event){
        String by = "";
        Player player = event.getPlayer();
        if(player != null) {
            if(!event.getCustomItemClass().isEnabled()){
                if(!player.isOp()) {
                    event.setCancelled(true);
                }
            }
            by = "by "+event.getPlayer().getName();
        }
           Debug.broadcast("customitem",
                   "Custom Item "+event.getCustomItemClass().getId()+" created "+by+(event.isCancelled()?ChatColor.RED+"[Cancelled]":""));
    }

    @EventHandler
    public void DispatchOnInteract(PlayerInteractEvent event){
        ItemStack itemstack = event.getItem();
        if(itemstack != null){
            CustomItem custom_item = getCustomItem(itemstack);
            if(custom_item!=null) {
                if (custom_item.isEnabled() || event.getPlayer().isOp()) {
                    custom_item.onInteract(event, itemstack);
                }
            }
        }
    }

    @EventHandler
    public void DipatchOnPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack itemstack : event.getDrops()){
            CustomItem ci = getCustomItem(itemstack);
            if(ci!=null){
                ci.onPlayerDeath(event, itemstack);
            }
        }

    }

    @EventHandler
    public void  DispatchOnItemConsume(PlayerItemConsumeEvent event) {
        ItemStack itemstack = event.getItem();
        CustomItem custom_item = getCustomItem(itemstack);
        if(custom_item!=null) {
            if (custom_item.isEnabled() || event.getPlayer().isOp()) {
//                if(custom_item.usesCooldownComponent()) {
//                    event.setCancelled(true);
//                    itemstack.setAmount(itemstack.getAmount()-1);
//                }
                custom_item.onPlayerItemConsume(event);
            }
        }
    }

    // listen to the cooldown being applied (fired when an item would go on cooldown)
    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemCooldown(PlayerItemCooldownEvent event) {
        Player player = event.getPlayer();
//        if(event.getCooldownGroup()){
//
//        }
//        Material material = event.getType(); // the material receiving the cooldown
//        if(event.setCancelled(true)){
//
//        }
    }

    // listen to the cooldown being applied (fired when an item would go on cooldown)
    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemGroupCooldown(PlayerItemGroupCooldownEvent event) {
        Player player = event.getPlayer();
    }


    @EventHandler
    public void DispatchOnItemHeld(PlayerItemHeldEvent event) {
        ItemStack old_item = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        ItemStack new_item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (new_item != null) {
            CustomItem custom = getCustomItem(new_item);
            if (custom != null) {
                custom.onItemSwitchTo(event, old_item, new_item);
            }
        }
        if (old_item != null) {
            CustomItem custom = getCustomItem(old_item);
            if (custom != null) {
                custom.onItemSwitchAway(event, old_item, new_item);
            }
        }
    }

    @EventHandler
    public void DispatchOnInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack is = event.getPlayer().getEquipment().getItem(event.getHand());
        CustomItem custom_item = getCustomItem(is);
        if (custom_item != null) {
            if(custom_item.isEnabled() || event.getPlayer().isOp()) {
                custom_item.onInteractEntity(event, is);
            }
        }
    }



    @EventHandler
    public void DispatchOnBlockBreak(BlockBreakEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        CustomItem custom = getCustomItem(item);
        if (custom != null) {
            custom.onBlockBreak(event);
        }
    }

    @EventHandler
    public void DispatchOnItemDropPlayer(PlayerDropItemEvent event) {
        Item i = event.getItemDrop();
        ItemStack item_stack = event.getItemDrop().getItemStack();
        CustomItem custom = getCustomItem(item_stack);
        if (custom != null) {
            /**
             * Apply ID to the Item itself this helps with ItemRemoveEvent
             */
            ItemStack is = i.getItemStack();
            i.getPersistentDataContainer().set(key_custom_item_id, PersistentDataType.STRING, custom.getId());
//            custom.onDropItemAny(item_stack);
            custom.onDropItemByPlayer(event);
        }
    }



    @EventHandler
    public void DispatchOnInventoryClick(InventoryClickEvent event) {
        ItemStack item_stack = event.getCurrentItem();
        CustomItem custom = getCustomItem(item_stack);
        if (custom != null) {
            custom.onInventoryClick(event, item_stack);
        }
    }

    @EventHandler
    public void DispatchOnShootProjectile(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        CustomItem ci = getCustomItem(bow);
        if (ci != null) {
            ci.onShootBow(event);
        }
    }

    @EventHandler
    public void DispatchOnLoadCrossbow(EntityLoadCrossbowEvent event) {
        ItemStack bow = event.getCrossbow();
        CustomItem ci = getCustomItem(bow);
        if (ci != null) {
            ci.onLoadCrossbow(event);
        }
    }


    @EventHandler
    public void onCustomCraft(SpecializationCraftItemEvent event){
        ItemStack itemstack = event.getEvent().getCurrentItem();
        CustomItem custom = getCustomItem(itemstack);
        if (custom != null) {
            custom.onCustomCraft(event, itemstack);
        }
    }














}
