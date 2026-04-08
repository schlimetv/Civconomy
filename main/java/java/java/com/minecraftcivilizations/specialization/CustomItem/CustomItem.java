package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Listener.Player.Inventories.SpecializationCraftItemEvent;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 *
 * @author  alectriciti, jfrogy
 *
 */
public abstract class CustomItem {

    protected final String id;
    protected String displayName;
    protected Material material;
    protected String customModelData; // null = not used
    protected int maxStackSize;
    protected boolean enabled;

    @Setter
    public NamespacedKey cooldownKey;


    boolean usesCooldownComponent;
//    public UseCooldownComponent cooldownComponent;

    protected String permission;

    @Getter
    protected boolean craftable;

    // Per-player cooldown tracking
    private final Map<UUID, Long> lastUse = new HashMap<>();

    /**
     * Primaray Construct (always called)
     */
    public CustomItem(String id,
                      String displayName,
                      Material material,
                      String customModelData,
                      int maxStackSize,
                      boolean enabled, boolean uses_cooldown) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.customModelData = customModelData;
        this.maxStackSize = maxStackSize;
        this.enabled = enabled;
        this.cooldownKey = new NamespacedKey("civlabs", "cooldown_" + id);
        this.usesCooldownComponent = uses_cooldown;
        Specialization.getInstance().getCustomItemManager().registerItem(this);
    }

    /**
     * A good place to declare custsom recipes and other class initialization
     */
    public void init(){

    }



    /**
     * Returns if the player can craft or not
     */
    public boolean canPlayerCraft(Player player){
        return true;
    }

    // === Constructor variations ===
    public CustomItem(String id, String displayName, Material material) {
        this(id, displayName, material, id, -1, true, false);
    }

    // === Constructor variations ===
    public CustomItem(String id, String displayName, Material material, boolean uses_cooldown) {
        this(id, displayName, material, id, -1, true, uses_cooldown);
    }

    public CustomItem(String id, String displayName, Material material, String customModelData, boolean uses_cooldown) {
        this(id, displayName, material, customModelData, -1, true, uses_cooldown);
    }

    public CustomItem(String id, String displayName, Material material, boolean enabled, boolean uses_cooldown) {
        this(id, displayName, material, id, -1, enabled, uses_cooldown);
    }

    public boolean isCustomItem(ItemStack stack){
        return getManager().isCustomItem(stack, id);
    }

    /**
     * Creates a typless Custom Item, to allow for variants such as swords
     */
    public CustomItem(String id) {
        this(id, null, null, id, 0, true, true);
    }

    // === Accessors ===
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getCustomModelData() {
        return customModelData;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean usesCooldownComponent() { return usesCooldownComponent; }

    public ItemStack createItemStack(){
        return createItemStack(1, null);
    }

    public ItemStack createItemStack(int amount){
        return createItemStack(amount, null);
    }

    public ItemStack createItemStack(Material mat){
        return createItemStack(1, mat, null);
    }

    /**
     * Call this to actually create an ItemStack
     */
    public ItemStack createItemStack(int amount, Player player) {
        return createItemStack(amount, material, player);
    }

    /**
     * Call this to actually create an ItemStack
     */
    public ItemStack createItemStack(int amount, Material mat_override, Player player) {
        ItemStack item_stack = new ItemStack(mat_override, amount);
        return wrapItemStack(item_stack, player);
    }

    /**
     * Wraps the ItemStack to convert it into this Custom Item
     */
    public ItemStack wrapItemStack(ItemStack item_stack, Player player) {
        ItemMeta meta = item_stack.getItemMeta();
        if(meta.getPersistentDataContainer().has(CustomItemManager.key_custom_item_id)){
            Debug.broadcast("customitem", "tried re-wrapping "+ item_stack.getType()+" as "+getId()+"!!!");
            return item_stack;
        }
        meta.getPersistentDataContainer().set(CustomItemManager.key_custom_item_id, PersistentDataType.STRING, getId());

        if(displayName!=null) {
            meta.setDisplayName(displayName);
        }

        if(maxStackSize>0) {
            meta.setMaxStackSize(maxStackSize);
        }

        if (customModelData != null) {
            CustomModelDataComponent c = meta.getCustomModelDataComponent();
            List<String> strings = new ArrayList<String>();
            strings.add(customModelData);
            c.setStrings(strings);
            meta.setCustomModelDataComponent(c);
        }

        if(usesCooldownComponent) {
            if (cooldownKey != null) {
//            Debug.broadcast("customitem", "cooldown created");
                UseCooldownComponent cd = meta.getUseCooldown();
                cd.setCooldownGroup(cooldownKey);
                meta.setUseCooldown(cd);
            }
        }

        item_stack.setItemMeta(meta);
        onCreateItem(item_stack, meta, player);

        CustomItemCreationEvent event = new CustomItemCreationEvent(this, item_stack, player);
        Bukkit.getPluginManager().callEvent(event);

        if(event.isCancelled()){
            return null;
        }
        return item_stack;
    }

    ;

    public void applyCooldown(Player player, int cooldown){
        player.setCooldown(cooldownKey, cooldown);
    }

    public boolean isOnCooldown(Player player){
        return player.getCooldown(cooldownKey)>0;
    }

    public int getCooldown(Player player){
        return player.getCooldown(cooldownKey);
    }


    /**
     * Extra Logic provided by Custom Item Classes
     * You must manually assign meta to item_stack if you wish to modify the meta
     */
    public abstract void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted);


    /*
     * The Following are Overridable methods.
     * These are called by CustomItemManager's listeners
     * and should remain empty bodies in this class
     */
    public void onInteract(PlayerInteractEvent event, ItemStack itemStack){}
    public void onInteractEntity(PlayerInteractEntityEvent event, ItemStack itemStack){}
    public void onItemSwitchTo(PlayerItemHeldEvent event, ItemStack oldItem, ItemStack newCustomItem) {}
    public void onItemSwitchAway(PlayerItemHeldEvent event, ItemStack oldCustomItem, ItemStack newItem) {}
    // When a player dies and this item is dropped on the ground
    public void onPlayerDeath(PlayerDeathEvent event, ItemStack item_stack) {}
    // Called when the item is dropped
    public void onDropItemByPlayer(PlayerDropItemEvent event) {}

    public void onShootBow(EntityShootBowEvent event) {}
    // Load Crossbow
    public void onLoadCrossbow(EntityLoadCrossbowEvent event) {}
    // Called when the player damages a block (e.g., mining)
    public void onBlockBreak(BlockBreakEvent event) {}


    public void onInventoryClick(InventoryClickEvent event, ItemStack itemStack) {}


    public void onCustomCraft(SpecializationCraftItemEvent event, ItemStack itemstack) {}




    // Called when the player right or left clicks with the item on an entity
//    public void onInteractEntity(ItemStack item_stack, PlayerInteractEntityEvent event, boolean main_hand) {}

    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {}


    public final void setEnabled(boolean b) {
        this.enabled = b;
        Specialization.getInstance().getLogger().info("Custom Item: "+id+ " has been "+ (b?"ENABLED":"DISABLED"));
    }


    public static CustomItemManager getManager(){
        return Specialization.getInstance().getCustomItemManager();
    }

    protected void applyManualCooldownOverride(ItemStack item, int ticks) {
        ItemMeta meta = item.getItemMeta();
        UseCooldownComponent cd = meta.getUseCooldown();

        float ftick = ((float)ticks)/20;
        cd.setCooldownSeconds(ftick);
//        Debug.broadcast("customitem", "cooldown override: "+ftick);
        meta.setUseCooldown(cd);
        item.setItemMeta(meta);

    }

}
