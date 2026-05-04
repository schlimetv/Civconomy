package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.minecraftcivilizations.specialization.Combat.CombatManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.WorldUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;
import static org.bukkit.entity.EntityType.*;
import static com.minecraftcivilizations.specialization.util.MathUtils.*;

/**
 * Allows for customization of mob spawning rules and stats
 * I have gone too far with this one
 * @author alectriciti ⚡
 * @see MobOverrideRuleSet for entity_type based mappings
 * @see MobOverrideRule for individual rules for converting a mob into a variant
 * @see MobVariation for specific entity profile settings
 */
public class MobManager implements Listener {

    public static final String TAKEDOWN_CHANCE = "takedown chance: ";
    private final NamespacedKey SPAWN_VARIATION_ID_KEY; //this determines if the mob was overrided
    private final NamespacedKey EXP_GAIN_OVERRIDE_KEY;

    private final NamespacedKey SCALE_KEY;
    private final NamespacedKey MOVE_SPEED_KEY;
    private final NamespacedKey WATER_SPEED_KEY;
    private final NamespacedKey STEP_HEIGHT_KEY;

    public static MobManager getInstance(){
        return CombatManager.getInstance().getMobManager();
    }

    public MobManager(CombatManager combatManager) {
        Specialization plugin = combatManager.getPlugin();
        this.SPAWN_VARIATION_ID_KEY = new NamespacedKey(plugin, "mob_spawn_id");
        this.EXP_GAIN_OVERRIDE_KEY = new NamespacedKey(plugin, "exp_gain_override");
        SCALE_KEY = new NamespacedKey(plugin, "custom_scale");
        MOVE_SPEED_KEY = new NamespacedKey(plugin, "custom_move_speed");
        WATER_SPEED_KEY = new NamespacedKey(plugin, "custom_water_speed");
        STEP_HEIGHT_KEY = new NamespacedKey(plugin, "custom_step_height");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        BukkitRunnable cleanupTask = new MobCleanupSystem(this).start();
    }



//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onMooshroom(PlayerInteractEntityEvent event) {
//        if (!(event.getRightClicked() instanceof MushroomCow mooshroom)) return;
//
//        Player player = event.getPlayer();
//        ItemStack item = player.getInventory().getItem(event.getHand());
//        if (item.getType() != Material.BOWL) return;
//
//        CustomPlayer pp = CoreUtil.getPlayer(player);
//        int lvl = pp.getSkillLevel(SkillType.FARMER);
//
//        // Minimum level to milk
//        if (lvl < 2) {
//            event.setCancelled(true);
//            PlayerUtil.message(player, "You need to be better at farming to do that");
//            return;
//        }
//
//        // Calculate cooldown scaling
//        // Example: lvl 1 = 60s, lvl 10+ = 10s
//        int maxCooldownTicks = 20 * 130; // 90 seconds
//        int minCooldownTicks = 20 * 45; // 45 seconds
//        int maxLevel = 5;
//
//        int scaledCooldown = maxCooldownTicks - ((lvl - 1) * (maxCooldownTicks - minCooldownTicks) / (maxLevel - 1));
//        if (scaledCooldown < minCooldownTicks) scaledCooldown = minCooldownTicks;
//
//        // Cooldown check
//        if (player.hasCooldown(Material.BOWL)) {
//            event.setCancelled(true);
//            PlayerUtil.message(player, "You're still tired from last milking.");
//            return;
//        }
//
//        // Grant XP
////        pp.addSkillXp(SkillType.FARMER, 10);
//
//        // Apply scaled cooldown
//        player.setCooldown(Material.BOWL, scaledCooldown);
//    }


    Map<EntityType, MobOverrideRuleSet> rule_mappings = new HashMap<>();
    Map<String, MobVariation> mob_variations = new HashMap<>();
    MobVariation default_mob_variation;


    void registerRule(MobOverrideRule rule) {
        for (EntityType type : rule.getReplaceTypes()) {
            MobOverrideRuleSet rule_set = rule_mappings.computeIfAbsent(type, k -> new MobOverrideRuleSet(type));
            rule_set.add(rule);
        }
    }

    void setDefaultRuleSetChance(int base_chance, EntityType...types){
        for(EntityType type : types) {
            MobOverrideRuleSet rule_set = rule_mappings.computeIfAbsent(type, k -> new MobOverrideRuleSet(type));
            rule_set.setBaseChance(base_chance);
        }
    }

    void registerMobVariation(MobVariation variation) {
        mob_variations.put(variation.getId(), variation);
    }

    public boolean isMobVariation(Entity entity){
        return entity.getPersistentDataContainer().has(SPAWN_VARIATION_ID_KEY);
    }

    //This simply flags the entity to be a variation
    void convertEntityToVariation(Entity entity, MobVariation variation){
        entity.getPersistentDataContainer().set(SPAWN_VARIATION_ID_KEY, PersistentDataType.STRING, variation.getId());
        if(entity instanceof LivingEntity le){
            applyStatsToEntity(le, variation);
        }
    }

    /**
     * @return A valid mob stat, the default as a fallback
     */
    public MobVariation getMobVariation(Entity e){
        if(e.getPersistentDataContainer().has(SPAWN_VARIATION_ID_KEY)){
            String id = e.getPersistentDataContainer().get(SPAWN_VARIATION_ID_KEY, PersistentDataType.STRING);
            if(mob_variations.containsKey(id)){
                return mob_variations.get(id);
            }
        }
//        Debug.broadcast("mobrule", "using default mob for "+e.getName());
        //TODO return default overrides
        return default_mob_variation;
    }

    /**
     *
     * @return might be null if nothing was found
     */
    public MobOverrideRule rollMobOverrideRule(EntityType type){
        if(rule_mappings.containsKey(type)) {
//            Debug.broadcast("mobrule", "<gray>mob rolling for <aqua>"+type+"</aqua>");
            return rule_mappings.get(type).rollRule();
        }
        return null;
    }

//    public final MobVariation zombie_stats = new MobVariation().hunts().breaks().damage(2.0).health(1.0).speed(1.5,2.0);
//    public final MobVariation skeleton_stats = new MobVariation().hunts().damage(1.25).speed(1.0, 1.5);


    /**
     * MobOverrideRule establishes the conditions and rules for which MobVariations get added to the game
     * MobVariations creates a new classification for Custom Mob Variants
     *
     * NOTE: If you add or modify these values, ensure the BASE chance for that mob override rule
     * For example: setDefaultRuleSetChance(10, RABBIT);  // this sets the chance of unaffected rabbits to weight 10
     *
     */
    public void populateEntityMappings(){
//        MobOverrideRule.setGlobalChance(100); // This sets the BASE weight chance for ALL entity types, which will avoid rolling for a MobOverrideRule
        //THESE EXIST PRIMARILY FOR REFRESHING
        rule_mappings = new HashMap<>();
        mob_variations = new HashMap<>();

        //TODO looks like this doesn't work right now, leaving it anyway
        default_mob_variation = new MobVariation("default_mob").damage(1.0, 1.5).health(2.0).speed(1.0, 2.0);


        //END OF PRIMARY REFRESH

        /**
         * These mobs will have a 100% chance of spawning
         */
        setDefaultRuleSetChance(0,
                ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER,
                SKELETON, BOGGED, STRAY,
                CREEPER, SPIDER, CAVE_SPIDER,
                SLIME, MAGMA_CUBE,
                BREEZE,
                GUARDIAN, ELDER_GUARDIAN,
                PILLAGER, RAVAGER, ILLUSIONER, VINDICATOR, WITCH,
                SILVERFISH, ENDERMITE, SHULKER,
                BLAZE, GHAST, PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN, WITHER_SKELETON, ZOMBIFIED_PIGLIN);

        /**
         * This covers miscelanious mobs
         */
        new MobOverrideRule(100,
                GUARDIAN, ELDER_GUARDIAN,
                PILLAGER, RAVAGER, ILLUSIONER, VINDICATOR,
                ENDERMITE, SHULKER)
                .addVariation(new MobVariation("generic_variation")
                        .damage(1.0, 2.5)
                        .speed(1.0, 1.5)
                        .hunts()
                        .breaks()
                );


        new MobOverrideRule(100, WITCH)
                .addVariation(new MobVariation("witch")
                        .health(1.0)
                        .damage(1.0)
                        .speed(1.0)
                        .xpScale(0.25)
                        .hunts(24)
                        .breaks()
                );

        new MobOverrideRule(100, DROWNED)
                .addVariation(new MobVariation("drowned_variation")
                                .health(2)
                                .damage(1.0, 1.35)
                                .speed(1.0, 1.75)
                                .waterspeed(1.0, 2)
                                .stepheight(0.5)
                                .hunts(54)
                                .breaks(1.25)
//                        .armorChance(Material.DIAMOND, 20, 0.95)
//                        .armorChance(Material.DIAMOND, 100, 0.25)

                );

        new MobOverrideRule(100, ZOMBIE, HUSK, ZOMBIE_VILLAGER)
                .addVariation(new MobVariation("zombie_variation")
                                .health(2)
                                .damage(1.0, 1.35)
                                .speed(1.0, 1.75)
                                .stepheight(0.5)
                                .hunts(54)
                                .breaks(1.5)
//                        .armorChance(Material.DIAMOND, 20, 0.95)
//                        .armorChance(Material.DIAMOND, 100, 0.25)
                , 100).addVariation(new MobVariation("armored_zombie")
                                .health(2)
                                .damage(1.0, 1.35)
                                .speed(1.0, 1.75)
                                .stepheight(0.5)
                                .hunts(54)
                                .breaks(1.5)
                                .armorChance(Material.IRON_INGOT, 200, 0.3)
                                .armorChance(Material.LEATHER, 75, 0.9)
                                .armorChance(Material.CHAIN, 30, 0.75)
                                .armorChance(Material.IRON_INGOT, 35, 0.825)
                                .armorChance(Material.DIAMOND, 20, 0.33)
                                .armorChance(Material.GOLD_INGOT, 15, 0.9)
                                .armorChance(Material.DIAMOND, 1, 0.925)
                                .disableItemPickup()
                                .drops(0)
//                        .armorChance(Material.DIAMOND, 20, 0.95)
//                        .armorChance(Material.DIAMOND, 100, 0.25)
                , 40);

        new MobOverrideRule(100, SKELETON)
                .addVariation(new MobVariation("skeleton_standard", SKELETON)
                        .health(2)
                        .damage(1.0, 1.3, 2.0)
                        .speed(1.0, 1.75)
                        .stepheight(0.5)
                        .hunts(36)
                        .breaks(0.75)
                        .disableItemPickup()
                                .armorChance(Material.AIR, 200, 1.0)
                                .armorChance(Material.CHAIN, 120, 0.9)
                                .armorChance(Material.IRON_INGOT, 35, 0.65)
                                .armorChance(Material.DIAMOND, 4, 0.35)
                                .replaceOriginalMob()
                , 200
                ).addVariation(new MobVariation("skeleton_bogged", BOGGED)
                        .health(1.5)
                        .damage(1.0, 0.8, 2.0)
                        .speed(1.0, 1.5)
                        .stepheight(0.5)
                        .hunts(32)
                        .breaks(0.5)
                        .removeDrop(Material.TIPPED_ARROW)
                        .xpScale(1.5)
                        .replaceOriginalMob()
                , 3)
                .addVariation(new MobVariation("skeleton_stray", STRAY)
                        .health(2)
                        .damage(1.0, 0.8, 2.0)
                        .speed(1.0, 1.5)
                        .stepheight(0.5)
                        .hunts(32)
                        .breaks(0.5)
                        .removeDrop(Material.TIPPED_ARROW)
                        .xpScale(1.5)
                        .replaceOriginalMob()
                , 3
                );

        /**
         * Example:
         * 95% of normal dolphin
         * 5% of special dolphin
         */
        setDefaultRuleSetChance(65, DOLPHIN);
        new MobOverrideRule(35, DOLPHIN)
                .spawnInPacks()
                .addVariation(new MobVariation("evil_dolphin", DOLPHIN)
                        .anger(true)
                        .damage(1.0, 0.5)
                        .health(1.5)
                        .size(1.25,1.5)
                        .speed(1.0, 1.35)
                        .hunts(24)
                        .xpScale(2.0)
                        .setGainsXpOverride(true)
                        .drops(0)
                );
        new MobOverrideRule(100,
                SLIME, MAGMA_CUBE)
                .addVariation(new MobVariation("slime_variation")
                        .damage(1.0, 1.25)
                        .speed(1.0, 1.5)
                        .xpScale(0.5)
                        .hunts()
                );
        new MobOverrideRule(100,
                SILVERFISH)
                .addVariation(new MobVariation("silverfish")
                        .damage(1.0, 1.5)
                        .speed(1.0, 2.0)
                        .hunts(16)
                        .xpScale(0.25)
                );



        new MobOverrideRule(100,
                BLAZE, GHAST, PIGLIN)
                .addVariation(new MobVariation("nether_variation")
                        .health(2)
                        .damage(2.0)
                        .speed(1.5)
                        .xpScale(1.5)
                        .hunts()
                        .breaks()
                );

        new MobOverrideRule(100,
                WITHER_SKELETON)
                .addVariation(new MobVariation("wither_skeleton")
                        .health(2)
                        .damage(1.5)
                        .speed(1.75)
                        .stepheight(0.5)
                        .xpScale(1.5)
                        .hunts()
                        .breaks()
                );

        new MobOverrideRule(100,
                PIGLIN_BRUTE)
                .addVariation(new MobVariation("piglin_brute")
                        .health(1.5)
                        .damage(    1.5)
                        .speed(1.75)
                        .xpScale(1.5)
                        .hunts(32)
                        .stepheight(0.25)
                        .breaks(1)
                        .removeDrop(Material.GOLDEN_AXE)
                );

        new MobOverrideRule(100,
                ZOMBIFIED_PIGLIN)
                .addVariation(new MobVariation("zombie_piglin")
                        .health(1.5)
                        .damage(    1.5)
                        .speed(1.8)
                        .xpScale(0.5)
                        .hunts(62)
                        .stepheight(0.5)
                        .breaks(1.5)
                        .removeDrop(Material.GOLDEN_SWORD)
                );

        new MobOverrideRule(100,
                HOGLIN, ZOGLIN)
                .addVariation(new MobVariation("hoglin")
                        .health(1.5)
                        .damage(1)
                        .speed(1.25)
                        .xpScale(0.5)
                        .hunts(32)
                        .breaks()
                );



//        new MobOverrideRule(25, ZOMBIE)
//                .addVariation(new MobVariation("armored_zombie")
//                        .health(2)
//                        .armorChance(Material.LEATHER, 100, 0.9)
//                        .armorChance(Material.IRON_INGOT, 50, 0.5)
//                        .armorChance(Material.DIAMOND, 25, 0.1)
//                        .damage(1.0, 1.35)
//                        .speed(1.5, 1.75)
//                        .hunts(54)
//                        .breaks(1.2)
//                        .drops(0)
//                );

        new MobOverrideRule(100, SPIDER)
                .addVariation(new MobVariation("spider")
                                .damage(1.0, 1.75)
                                .speed(1.0, 1.5)
                                .health(2.0)
                                .stepheight(1.0)
                                .waterspeed(1.0, 1.5)
                                .breaks(1.0)
                                .hunts()
                                .drops(1, 1)
                        , 100)
                .addVariation(new MobVariation("spider_small")
                                .health(0.25)
                                .damage(1.0, 0.75)
                                .stepheight(0.5)
                                .speed(1.0, 1.75)
                                .waterspeed(1.0, 4)
                                .size(0.66,0.66)
                                .spawnExtra(5)
                                .hunts()
                                .breaks(0.25)
                                .drops(1, 1)
                                .removeDrop(Material.STRING)
                        , 100);


        // DISABLED: evil black night wolves removed
//        MobVariation night_wolves = new MobVariation("night_wolf", WOLF)
//                .anger(true)
//                .damage(0.75, 1.0)
//                .health(1.5)
//                .speed(1.25, 1.5)
//                .setGainsXpOverride(true)
//                .hunts(32)
//                .breaks(0.75)
//                .stepheight(0.5)
//                .xpScale(2.5)
//                .spawnExtra(3)
//                .breeds("black", "black", "black") //,"chestnut", "woods", "striped")
//                .replaceOriginalMob()
//                .despawnFaraway();

//        new MobOverrideRule(8, CREEPER)
//                .addVariation(night_wolves, 10).spawnInPacks();

        new MobOverrideRule(100, CREEPER)
                .addVariation(new MobVariation("creeper")
                                .xpScale(1.25)
                                .health(2.0)
                                .damage(1.0, 1.5)
                                .speed(1.0, 1.75)
                                .hunts()
                                .xpScale(1.25)
                                .breaks(0.75)
                        , 100)
                ;


//                .addVariation(new MobVariation("spider_large", CAVE_SPIDER).health(4).damage(2.0).speed(0.5, 0.75).addImmunity(DamageType.ARROW).size(2.5,2.5).hunts(64).drops(1.0, 2.0).xpScale(1.5)
//                        , 100);

//        MobVariation creeper_variation = new MobVariation("creeper").damage(1.5).speed(1.0, 1.5).hunts();
//        new MobOverrideRule(100, CREEPER)
//                .addVariation(zombie_variation, 10000);
//        MobVariation chaos = new MobVariation("chaos", SHEEP, PIG, COW, WOLF,PIGLIN, PIGLIN_BRUTE).damage(1.0).health(1.5).speed(1.25, 1.25);

//                .addVariation(chaos, 20);w

        MobVariation killer_bees = new MobVariation("killer_bees", BEE)
                .deprecated()
                .anger(true)
                .damage(0.25)
                .health(0.125)
                .speed(2.0, 2.0)
                .size(0.5, 0.5)
                .hunts(32)
                .setGainsXpOverride(true)
                .spawnExtra(2);

//        MobVariation deprecated_wolf_pack = new MobVariation("wolf_pack", WOLF)
//                .deprecated();

        setDefaultRuleSetChance(100, PIG, SHEEP, HORSE, WOLF);

        // field spawn
//        new MobOverrideRule(1, HORSE)
//                .addVariation(killer_bees, 4);
//        new MobOverrideRule(3, PIG)
//                .addVariation(night_wolves, 10).spawnInPacks();
//        new MobOverrideRule(10, WOLF)
//                .addVariation(night_wolves, 10).spawnInPacks();


        setDefaultRuleSetChance(25, POLAR_BEAR);
        new MobOverrideRule(25, POLAR_BEAR)
                .addVariation(new MobVariation("mean_polar_bear", POLAR_BEAR).anger(true).speed(1.0, 1.2).health(2).hunts(48).breaks(1.5));


        setDefaultRuleSetChance(200, ENDERMAN);
        new MobOverrideRule(2, ENDERMAN)
                .addVariation(new MobVariation("creaker", CREAKING)
                        .anger(true)
                        .invisible()
                        .health(0.1)
                        .hunts(64)
                        .replaceOriginalMob());

        // bee DONT DO THIS
//        new MobOverrideRule(100, BEE).spawnInPacks()
//                .addVariation(killer_bees);



        // wolf
//        new MobOverrideRule(20, WOLF).spawnInPacks()
//                .addVariation(deprecated_wolf_pack);

        new MobOverrideRule(20, TURTLE).addVariation(new MobVariation("creepo", CREEPER).hunts(32).speed(1.0, 2), 100);










    }


    @EventHandler(priority = EventPriority.HIGH) //(ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        List<LivingEntity> list = new ArrayList<LivingEntity>();
        for(Entity e : event.getChunk().getEntities()){
            if(e instanceof LivingEntity livingEntity){
                list.add(livingEntity);
            }
        }
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> overrideMobs(list));
    }

    /**
     * This allows mob overrides to be spawned in clusters using natural chunk generation
     * note: this is essentially tagging along existing mob spawns
     */
    private void overrideMobs(List<LivingEntity> list) {
        // pre-determine which categories will be overridden (and to what)
        //get a ruleset for EACH entity type

        // gather unique entity types present in this chunk/list
        Set<EntityType> present_types = list.stream()
                .map(LivingEntity::getType)
                .collect(Collectors.toSet());

        // if the ruleset does not spawn in a pack, assign that here
        Map<EntityType, MobOverrideRule> replacement_map = new HashMap<>();
        // if the ruleset spawns in a pack, we'll assign that here
        Map<EntityType, MobVariation> spawn_in_pack = new HashMap<>();

        for (EntityType type : present_types) {
            //roll a new rule, apply it to
            MobOverrideRule rule = rollMobOverrideRule(type);
            if(rule!=null) {
                if (rule.doesSpawnInPacks()) {
                    spawn_in_pack.put(type, rule.rollVariation()); // This will cause ALL the entities of this type to roll as a specific variation
                } else {
                    replacement_map.put(type, rule); // This will cause the entities to roll a new variation per entity
                }
            }
        }

        // apply the  results to every mob in the list
        for (LivingEntity living : list) {
            EntityType type_original = living.getType();
            MobVariation variation = null;
            Location loc = living.getLocation();
            if(spawn_in_pack.containsKey(type_original)){
                variation = spawn_in_pack.get(type_original);
            }else if(replacement_map.containsKey(type_original)){
                variation = replacement_map.get(type_original).rollVariation();
            }
            if(variation!=null){
                EntityType new_type = variation.rollType();
                if(new_type == null){
                    //apply the stats now, as we do not override type
                    convertEntityToVariation(living, variation);
//                    Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<blue>[CHNK_NULL]</blue> <yellow>["+variation.getId()+"]</yellow> applying to <gray>"+living.getName()+"</gray> at ").append(Debug.formatLocationClickable(living.getLocation(),true)));
                }else{
                    if(variation.doesReplaceOriginalMob()) {
                        living.remove(); // this deletes the existing mob
                    }else{
                        loc = WorldUtils.createRandomLocationInChunk(loc);
                        loc = WorldUtils.getNextSafeVerticalPosition(loc).add(0, 1,0);
                    }

                    //Spawn a new mob with the variation settings
                    Entity e = loc.getWorld().spawnEntity(loc, new_type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                    if(Debug.isAnyoneListening("mob", false)){
                    Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<light_purple>🏔</light_purple>"+e.getName()+" <gray>applied: <green>"+variation.getId()+" ")
                            .append(Debug.formatLocationClickable(e.getLocation(), true)));
                    }
                    convertEntityToVariation(e, variation);

//                    Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<light_purple>[CHNK_VALID]</light_purple> <yellow>["+variation.getId()+"]</yellow> <gray>"+living.getName()+"</gray> to <gray>"+e.getName()+"</gray> at ").append(Debug.formatLocationClickable(e.getLocation(),true)));

                }
//                Debug.broadcast("mob", "Chunk Gen Override:<light_purple>" + type_original.name() +
//                        "</light_purple> -> <green>" + variation.getId() + "</green>");
            }
        }
    }


    @EventHandler
    public void onMob(EntityDismountEvent event){

    }

    @EventHandler
    public void onMobMount(VehicleEnterEvent event){
        if(event.getEntered().getType()==DOLPHIN){
            event.setCancelled(true);
        }
//        Debug.broadcast("mob", "dolphin entered");
    }


    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
//        Debug.broadcast("mobspawn", "Attempting <green>"+event.getEntity().getType().name()+"</green> spawn at "+event.getLocation().getBlock().getBiome().toString());
        if(event.isCancelled())return;

        if (Debug.isAnyoneListening("mob", false) || Debug.isAnyoneListening("mobrule", false)) {
            populateEntityMappings();
//                Debug.broadcast("mob", "repopulating mob stats!");
        }
        LivingEntity entity = event.getEntity();
        EntityType type = entity.getType();
//        Debug.broadcast("mob","summoning "+event.getSpawnReason().name());


        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            //THIS IS A NATURAL GAME SPAWN
            if(isMobVariation(event.getEntity())){
                return;
            }
            MobOverrideRule rule = rollMobOverrideRule(type);
            if (rule != null) {
                MobVariation variation = rule.rollVariation();
                if (variation != null) {
                    EntityType new_type = variation.rollType();
                    Location loc = entity.getLocation();
                    if (new_type == null) {
                        //apply the stats now, as we do not override type
                        if(Debug.isAnyoneListening("mob", false)) {
                            Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<green>🔌</green> " + "<white>" + entity.getName() + " <gray>applied: <green>" + variation.getId() + " ")
                                    .append(Debug.formatLocationClickable(entity.getLocation(), true)));
                        }
                        convertEntityToVariation(entity, variation);
//                        Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<DARK_GREEN>[CS_NULL]</DARK_GREEN> <yellow>[" + variation.getId() + "]</yellow> to <gray>" + entity.getName() + "</gray> at ").append(Debug.formatLocationClickable(entity.getLocation(), true)));
                    } else {
                        if (variation.doesReplaceOriginalMob()) {
                            event.setCancelled(true);
                        } else {
                            loc = WorldUtils.createRandomLocationInChunk(loc);
                            loc = WorldUtils.getNextSafeVerticalPosition(loc);
                        }
                        //Spawn a new mob with the variation settings
                        Entity e = loc.getWorld().spawnEntity(loc, new_type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                        if (e instanceof LivingEntity le) {
                            convertEntityToVariation(le, variation);
                            if(Debug.isAnyoneListening("mob", false)) {
                                Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<green>📲</green> " + "<white>" + entity.getName() + " <gray>converted: <green>" + variation.getId() + " ")
                                        .append(Debug.formatLocationClickable(entity.getLocation(), true)));
                            }
//                            Debug.broadcast("mob", MiniMessage.miniMessage().deserialize("<green>[CS_VALID]</green> <yellow>[" + variation.getId() + "]</yellow> <gray>" + entity.getName() + "</gray> to <gray>" + e.getName() + "</gray> at ").append(Debug.formatLocationClickable(entity.getLocation(), true)));
                            MobVariation mount_variation = variation.getMount();
                            if (mount_variation != null) {
                                if (variation.getMountChance() > ThreadLocalRandom.current().nextDouble()) {
                                    Entity ee = loc.getWorld().spawnEntity(loc, mount_variation.rollType(), CreatureSpawnEvent.SpawnReason.NATURAL);
                                    convertEntityToVariation(ee, mount_variation);
                                    le.addPassenger(ee);
                                }
                            }
                            if(variation.getSpawnExtra()>0){
                                for(int i = 0; i < variation.getSpawnExtra(); i++){
                                    Entity ee = loc.getWorld().spawnEntity(loc, new_type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                                    convertEntityToVariation(ee, variation);
                                }
                            }
                        }
                    }
                }
            }
        }else{

        }
    }


        /**
         * TESTING ZONE
         */

    /**
     * Explicitly adds the stats to an entity
     */
    public void applyStatsToEntity(LivingEntity entity, MobVariation stats) {
        entity.setPersistent(false);
        boolean is_day_time = entity.getWorld().isDayTime();

        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max_health = attribute.getBaseValue() * stats.getHealthMultiplier();
        attribute.setBaseValue(max_health);
        entity.setHealth(max_health);

        double applyspeed_modifier = 1.0;
        if(entity instanceof Ageable ageable){
            //TODO set baby override
            if(!ageable.isAdult()){
                applyspeed_modifier = 0.75;
            }
        }
        //DO NOT USE THIS. USE ENTITY DAMAGE EVNET INSTEAD
//        AttributeInstance damage_attribute = entity.getAttribute(Attribute.ATTACK_DAMAGE);
//        if(damage_attribute != null) {
//            damage_attribute.setBaseValue(damage_attribute.getBaseValue() * (is_day_time?stats.getDamageMultiplierDay():stats.getDamageMultiplierNight()));
//        }
        // SCALE
        AttributeInstance scale_attr = entity.getAttribute(Attribute.SCALE);
        if (scale_attr != null) {
            double mult = stats.getScale();          // e.g. 1.3
            double amount = mult - 1.0;              // ADD_SCALAR expects +0.3

            scale_attr.removeModifier(SCALE_KEY);
            scale_attr.addModifier(new AttributeModifier(
                    SCALE_KEY,
                    amount,
                    AttributeModifier.Operation.ADD_SCALAR
            ));
        }

        // MOVEMENT SPEED
        AttributeInstance speed_attr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed_attr != null) {
            double mult = applyspeed_modifier *
                    (is_day_time ? stats.getSpeedMultiplierDay() : stats.getSpeedMultiplierNight());
            double amount = mult - 1.0;

            speed_attr.removeModifier(MOVE_SPEED_KEY);
            speed_attr.addModifier(new AttributeModifier(
                    MOVE_SPEED_KEY,
                    amount,
                    AttributeModifier.Operation.ADD_SCALAR
            ));
        }

        // WATER SPEED
        AttributeInstance water_attr = entity.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
        if (water_attr != null) {
            double mult = applyspeed_modifier *
                    (is_day_time ? stats.getWaterSpeedMultiplierDay() : stats.getWaterSpeedMultiplierNight());
            double amount = mult - 1.0;

            water_attr.removeModifier(WATER_SPEED_KEY);
            water_attr.addModifier(new AttributeModifier(
                    WATER_SPEED_KEY,
                    amount,
                    AttributeModifier.Operation.ADD_SCALAR
            ));
        }


        // MOVEMENT SPEED
        AttributeInstance step_attr = entity.getAttribute(Attribute.STEP_HEIGHT);
        if (step_attr != null) {
//            double mult = applyspeed_modifier *
//                    (is_day_time ? stats.getSpeedMultiplierDay() : stats.getSpeedMultiplierNight());
            double amount = stats.getStepHeight();

            step_attr.removeModifier(STEP_HEIGHT_KEY);
            step_attr.addModifier(new AttributeModifier(
                    STEP_HEIGHT_KEY,
                    amount,
                    AttributeModifier.Operation.ADD_NUMBER
            ));
        }

        if(stats.isInvisible()){
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        }
        if(stats.potionEffect!=null) {
            entity.addPotionEffect(stats.potionEffect);
        }
        if(stats.getBreed()!=null && !stats.getBreed().isEmpty()){
            applyBreedSettings(entity, stats);
        }
        stats.applyRandomArmor(entity);

        if (stats.isDisableItemPickup()){
            entity.setCanPickupItems(false);
        }

        applyLogicToMob(entity, stats);
    }

    private static void applyBreedSettings(LivingEntity entity, MobVariation stats) {
        int x = ThreadLocalRandom.current().nextInt(stats.getBreed().size());
        String breedname = stats.getBreed().get(x);
        switch(entity.getType()) {
            case EntityType.WOLF -> {
                Wolf wolf = (Wolf) entity;
                Wolf.Variant v = null;
                switch (breedname.toLowerCase().trim()) {
                    case "ashen":
                        v = Wolf.Variant.ASHEN;
                        break;
                    case "black":
                        v = Wolf.Variant.BLACK;
                        break;
                    case "chestnut":
                        v = Wolf.Variant.CHESTNUT;
                        break;
                    case "pale":
                        v = Wolf.Variant.PALE;
                        break;
                    case "rusty":
                        v = Wolf.Variant.RUSTY;
                        break;
                    case "snowy":
                        v = Wolf.Variant.SNOWY;
                        break;
                    case "spotted":
                        v = Wolf.Variant.SPOTTED;
                        break;
                    case "striped":
                        v = Wolf.Variant.STRIPED;
                        break;
                    case "woods":
                        v = Wolf.Variant.WOODS;
                        break;
                    default:
                        v = Wolf.Variant.WOODS; // safe fallback
                        break;
                }
                if (v != null) {
                    wolf.setVariant(v);
                }
            }
        }
    }


    /**
     * Called on mob creation AND on mob load to reapply logic
     */
    private static void applyLogicToMob(LivingEntity entity, MobVariation stats) {
        if(stats.deprecated){
            Debug.broadcast("mob", "<red>removed "+entity.getName()+" by deprecation");
            entity.remove();
        }else{
        }
        if(stats.isAngry()) {
            if (entity instanceof Bee bee) {
                bee.setAnger(1000000);
            }else if(entity instanceof Wolf wolf) {
                wolf.setAngry(true);
            }else if(entity instanceof PolarBear bear){
                bear.setAggressive(true);
                bear.setStanding(true);
            }
            if(entity instanceof Mob mob){
                //includes dolphins etc
                mob.setAggressive(true);
            }
        }


        if(stats.isDespawnFaraway()){
            entity.setRemoveWhenFarAway(true);
            entity.setPersistent(false);
            Debug.broadcast("mob", "despawning when faraway");
        }

        /**
         * This logic is a hybrid of target acquisition and block breaking. The break logic is better.
         * If we need old Mob Goals back, Comment out this logic and uncomment the two originals below
         * - Alec
         */
        if(stats.doesHunting()) {
            if (entity instanceof Mob mob) {
                Bukkit.getMobGoals().addGoal(mob, 0, new HuntPlayerMobGoal(mob, stats.getFollowRange(), stats.doesBreaking(), stats.getBreakScalar()));
            }
        }
//        if(stats.doesBreaking()) {
//            if (entity instanceof Monster monster) {
//                Bukkit.getMobGoals().addGoal(monster, 3, new BreakBlockMobGoal(monster));
//            }
//        }
//        if(stats.doesHunting()) {
//            if (entity instanceof Monster mob) {
//                Bukkit.getMobGoals().addGoal(mob, 0, new TargetPlayerMobGoal(mob));
//            }
//        }
    }

    @EventHandler
    public void onEntityLoad(EntitiesLoadEvent event){
        List<Entity> entities = event.getEntities();
        for(Entity e : entities){
            if(isMobVariation(e)){
                if(e instanceof LivingEntity le) {
//                    Specialization.getInstance().getLogger().info("applying logic to "+e.getName());
                    applyLogicToMob(le, getMobVariation(le));
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event){
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if(isMobVariation(victim)){
            MobVariation variation = getMobVariation(victim);
            DamageType event_type = event.getDamageSource().getDamageType();
            for(DamageType immunity : variation.immunity_types) {
                if (event_type.equals(immunity)){
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if(damager instanceof Player px){
            if(victim instanceof Mob mob){
                if(mob.getTarget()!=null){
                    if(!mob.getTarget().equals(px)) {
                        if(ThreadLocalRandom.current().nextDouble() < 0.90) {
                            mob.setTarget(px);
                        }
                    }
                }
            }
        }

        // MOB TAKE DAMAGE
        switch(victim.getType()){
            case CREAKING:
                if(isMobVariation(victim)){
                    double takedown_chance=0.25;
                    CustomPlayer player = CoreUtil.getPlayer(event.getDamager().getUniqueId());
                    if(player!=null){
                        takedown_chance = 0.25 + (((double)player.getSkillLevel(SkillType.GUARDSMAN))/10);
                    }
//                    Debug.broadcast("mob", TAKEDOWN_CHANCE+takedown_chance);
                    if(ThreadLocalRandom.current().nextDouble() > takedown_chance){
                        event.setCancelled(true);
                        Location teleport;
                        Vector v = getDirectionVector(victim.getYaw(), victim.getPitch()).normalize();
                        v = v.add(randomVectorCentered(1));
                        teleport = victim.getLocation().add(v.multiply(random(10, 20)));
                        Block block = teleport.getBlock();
                        while(block.isSolid()){
                            block = block.getRelative(BlockFace.UP);
                        }
                        teleport = block.getRelative(BlockFace.UP).getLocation().add(0.5,0.5,0.5);
                        victim.teleport(teleport);
                        victim.setFallDistance(-100);
                        victim.getWorld().playSound(teleport, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                    }
                }
                break;
        }
        switch(damager.getType()){
            case BEE:
                if(isMobVariation(damager)) {
                    if(ThreadLocalRandom.current().nextBoolean()) {
                        ((Bee) damager).damage(10);
                    }
                    if(event.getDamageSource().getDamageType()!=DamageType.SPIT) {
                        Bee bee = (Bee) event.getDamager();
                        bee.setHasStung(false);
                        bee.getServer().getScheduler().scheduleSyncDelayedTask(Specialization.getInstance(), new Runnable() {
                            @Override
                            public void run() {
                                unsetBee(bee);
                            }
                        });
                        event.setCancelled(true);
                        if (victim instanceof LivingEntity le) {
                            le.damage(0.5, DamageSource.builder(DamageType.SPIT).build());
//                        Debug.broadcast("bee", ""+event.getDamageSource().getDamageType());
                        }
                    }
                }
                break;
        }
    }

//    public void on(EntityPotionEffectEvent le){
//    }

    private void unsetBee(Bee bee) {
//        Debug.broadcast("mob", "Be has stung = false");
        bee.setHasStung(false);
    }

    /**
     * Amplifies mob damage
     * called from CombatManger
     */
    public void onMobAttack(Player player, EntityDamageByEntityEvent event){
        if(!(event.getDamager() instanceof LivingEntity entity)) return;

        MobVariation stats = getMobVariation(entity);
        if(stats == default_mob_variation) return;


        /**
         * Scales mob damage based on their day/night settings
         */
        double newDamage = event.getDamage(BASE);
        double mob_damage_multiplier;


        if(entity.getWorld().getEnvironment()== World.Environment.NORMAL){
            mob_damage_multiplier = entity.getWorld().isDayTime()?stats.getDamageMultiplierDay():stats.getDamageMultiplierNight();
        }else{
            mob_damage_multiplier = stats.getDamageMultiplierNether();
        }
        double mob_damage_base_increase = entity.getWorld().isDayTime()?stats.getDamageBaseDay():stats.getDamageBaseNight();
        event.setDamage(BASE, (newDamage * mob_damage_multiplier) + mob_damage_base_increase);

//      BACKUP PLAN FOR MOB DAMAGE:
//        Ensure this method is called after CombatManager's ArmorReduction.
//        double newDamage = event.getDamage();
//        event.setDamage(newDamage*2.0);

//        event.setDamage(EntityDamageEvent.DamageModifier.BASE, newDamage*2);


//        event.setDamage(EntityDamageEvent.DamageModifier.BASE, newDamage*2);
//        if(player.getWorld().isDayTime()){
//            double dayMobDamageMultiplier = SpecializationConfig.getMobConfig().get("DAYTIME_MOB_DAMAGE_MULTIPLIER", Double.class);
//            newDamage = event.getDamage() * dayMobDamageMultiplier;
//        }else{
//            double nightMobDamageMultiplier = SpecializationConfig.getMobConfig().get("NIGHTTIME_MOB_DAMAGE_MULTIPLIER", Double.class);
//            newDamage = event.getDamage() * nightMobDamageMultiplier;
//            if(CoreUtil.getPlayer(player).getSkillLevel(SkillType.GUARDSMAN) >= 1){
//                double guardsmanReductionAmount = SpecializationConfig.getMobConfig().get("NIGHT_GUARDSMAN_MOB_DAMAGE_PERCENT_REDUCTION", Double.class);
//                newDamage *= 1 - (guardsmanReductionAmount/100d);
//            }
//        }
//        event.setDamage(newDamage);
    }


    @EventHandler
    public void onDeath(EntityDeathEvent event){
        LivingEntity entity = event.getEntity();
        if(isMobVariation(entity)){
            MobVariation variation = getMobVariation(entity);
            if(variation.dropChance != -1){
                if(variation.dropChance > ThreadLocalRandom.current().nextDouble()) {
                    //get the drop
                    if (!variation.getRemovedDrops().isEmpty() && !event.getDrops().isEmpty()) {
                        List<ItemStack> drops_copy = new ArrayList<>(event.getDrops());
                        for (ItemStack is : drops_copy) {
                            if (variation.getRemovedDrops().contains(is.getType())) {
                                event.getDrops().remove(is);
                                Debug.broadcast("mobdrop", "removed " + is.getType().name() + " from " + variation.getId() + "'s drops");
                            }
                        }
                    }
                }else{
                    event.getDrops().clear();
                    return;
                }
            }
            if(variation.dropAmountScale!=1.0){
                for (ItemStack item : event.getDrops()) {
                    int scaledAmount = Math.max(1, (int) Math.round(item.getAmount() * variation.dropAmountScale));
                    item.setAmount(scaledAmount);
                }
            }
        }
    }


    /**
     * Grants guardsman exp to the player
     */
    public void applyGuardsmanExp(EntityDamageByEntityEvent event, CustomPlayer customPlayer, Player dmger, LivingEntity victim) {
        if(event.getDamage()<0.1)return;
        if (victim instanceof Player px){
            return;
        }
        if(victim instanceof ArmorStand){
            return;
        }
        int lvl = customPlayer.getSkillLevel(SkillType.GUARDSMAN);
        EntityEquipment equipment = dmger.getEquipment();
        Material type = equipment.getItemInMainHand().getType();
        if(lvl == 0 && !isValidWeapon(type)){
            return;
        }
        double xp_scale = 1.0;

        if(!(victim instanceof Enemy)) {
            //Passive Mob XP Reduction
            if(type.name().contains("_AXE") && lvl == 0){
                xp_scale = 0;
            }else {
                switch (lvl) {
                    case 0:
                        xp_scale = 0.75;
                        break;
                    case 1:
                        xp_scale = 0.5;
                        break;
                    case 2:
                        xp_scale = 0.25;
                        break;
                    default:
                        xp_scale = 0;
                        break; //No xp to grant on passive mobs
                }
            }
        }

        MobVariation mobStats = getMobVariation(victim);
        if(victim.getPersistentDataContainer().has(EXP_GAIN_OVERRIDE_KEY)){
            if(victim.getPersistentDataContainer().get(EXP_GAIN_OVERRIDE_KEY, PersistentDataType.BOOLEAN)){
                xp_scale = 1.0;
            }
        }
        if(xp_scale <= 0){
            return;
        }
//            does_grant_exp = true;
//            if(mobStats.isXpGainOverrideActive()) {
//                does_grant_exp = mobStats.getXpGainOverrideState();
//             }else if(victim instanceof Enemy){
//                does_grant_exp = true;
//            }else{
//                does_grant_exp = false;
//            }
//        }

        // if entity does not grant exp, exit
//        if(!does_grant_exp){
//            return;
//        }

        double xp_multiplier = mobStats.getXpScale() * xp_scale;
        if (xp_multiplier>0) {
            LivingEntity le = (LivingEntity) victim;
            double xp = event.getFinalDamage();
            if (xp > le.getHealth()) {
                xp = Math.max(1, le.getHealth());
            }

//            Debug.broadcast("xp", "XP formula 1: "+CombatManager.calculateTotalDamage(event));
//            Debug.broadcast("xp", "XP formula 2: "+event.getDamage());
//            Debug.broadcast("xp", "XP formula 3: "+event.getFinalDamage());
//            customPlayer.addSkillXp(SkillType.GUARDSMAN, (int) Math.max(1, (xp * xp_multiplier)), true); // USE THIS if we want xp gained on each low hit (account for sweeping edge)
            customPlayer.addSkillXp(SkillType.GUARDSMAN, (int) (xp * xp_multiplier), true);
        }
    }

    private boolean isValidWeapon(Material type) {
        switch(type){
            case WOODEN_SWORD:
            case STONE_SWORD:
            case IRON_SWORD:
            case GOLDEN_SWORD:
            case DIAMOND_SWORD:
            case NETHERITE_SWORD:
            case WOODEN_AXE:
            case STONE_AXE:
            case IRON_AXE:
            case GOLDEN_AXE:
            case DIAMOND_AXE:
            case NETHERITE_AXE:
            case TRIDENT:
            case MACE:
                return true;
            default: return false;
        }
    }

    /**
     * This will force a specific entity to grant Guardsman Exp when damaged
     * This takes highest priority, by overriding the MobVariation rulesset
     * @param entity
     */
    public static void setExpGainOverride(Entity entity, boolean gives_exp){
        NamespacedKey preventExpGainKey = getInstance().EXP_GAIN_OVERRIDE_KEY;
        entity.getPersistentDataContainer().set(preventExpGainKey, PersistentDataType.BOOLEAN, gives_exp);
    }



}
