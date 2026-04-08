package com.minecraftcivilizations.specialization.Combat;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Combat.Mobs.MobVariation;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Combat.Mobs.MobManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.minecraftcivilizations.specialization.util.MathUtils.random;
import static org.bukkit.entity.EntityType.*;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;
/**
 * The parent manager for everything related to combat, including mob damage
 * Routes damage listeners
 * @author Alectriciti ⚡
 */
public class CombatManager implements Listener {


    public static NamespacedKey ARROW_DAMAGE_KEY;
    public static NamespacedKey CRIT_BONUS_KEY;

    @Getter
    private final GuardsmanDamage guardsmanDamage;
//    private final DynamicArmor dynamicArmor; DLC feature by Alectriciti

    @Getter
    private final ArmorDamageReduction armorDamageReduction; // Handles MOB -> PLAYER damage

    @Getter
    private final MobManager mobManager;

    @Getter
    private final ArmorEquipAttributes armorEquip;

    @Getter
    private final Berserk berserk; // Berserk Manager

    @Getter
    private final ExplosionDamage explosionDamage;

    @Getter
    private final ArmorBreakSystem armorBreakSystem;

    @Getter
    final Specialization plugin;

    public CombatManager(Specialization specialization) {
        this.plugin = specialization;
        specialization.getServer().getPluginManager().registerEvents(this, specialization);
        CRIT_BONUS_KEY = new NamespacedKey(specialization, "COMBAT_CRIT_BONUS");
        ARROW_DAMAGE_KEY = new NamespacedKey(specialization, "ARROW_DAMAGE");

        guardsmanDamage = new GuardsmanDamage(this);
        mobManager = new MobManager(this);
        armorBreakSystem = new ArmorBreakSystem(this);
//        dynamicArmor = new DynamicArmor(this);
        armorEquip = new ArmorEquipAttributes(this);
        armorDamageReduction = new ArmorDamageReduction(this);
        berserk = new Berserk(this);
        explosionDamage = new ExplosionDamage(this);
    }

    public void initialize(){
        mobManager.populateEntityMappings();
    }

    public static CombatManager getInstance() {
        return Specialization.getInstance().getCombatManager();
    }

    @EventHandler
    public void ShootBowListener(ProjectileLaunchEvent event){
        Projectile projectile = event.getEntity();
        if(projectile.getType()==ARROW || projectile.getType()==SPECTRAL_ARROW){
        }else{
            return;
        }

        double multiplier = 1.0;

        ProjectileSource source = projectile.getShooter();
        //TODO firework check
        if(source instanceof LivingEntity shooter){
            ItemStack weapon = shooter.getEquipment().getItemInMainHand();
            switch(weapon.getType()){
                case BOW:
                    if(shooter instanceof Player ps){
                        multiplier = 1.0;
                        ps.setCooldown(Material.CROSSBOW, 16);
                    }else{
                        //skeleton or mob
                        multiplier = 0.7;
                        if(mobManager.isMobVariation(shooter)) {
                            MobVariation mobVariation = mobManager.getMobVariation(shooter);
                            if (shooter.getWorld().getEnvironment() == World.Environment.NORMAL) {
                                multiplier *= shooter.getWorld().isDayTime() ? mobVariation.getDamageMultiplierDay() : mobVariation.getDamageMultiplierNight();
                            } else {
                                multiplier *= mobVariation.getDamageMultiplierNether();}
                        }
                    }
                    break;
                case CROSSBOW:
                    multiplier = 1.5;
                    if(shooter instanceof Player ps){
                        ps.setCooldown(Material.CROSSBOW, 24);
                    }
                    break;
            }
        }else if (source == null) {
            //dispenser
            // Shooter is a dispenser
            multiplier = 2.0;
        }
        projectile.getPersistentDataContainer().set(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, multiplier);
    }

    public static final double standard_crit_base_multiplier = 0.25; // All crits multiply by base weapon damage
    public static final double standard_crit_guardsman_multiplier = 0.25; // Multiplier per level of guardsman to add to base crit
    public static final double opening_crit_baseline = 0.5; //All opening crits add this much as a base

    /**
     * Use this to get the crit bonus on any item*
     */
    public static double getCustomWeaponCrit(ItemStack weapon){
        if(weapon.hasItemMeta()) {
            if (weapon.getItemMeta().getPersistentDataContainer().has(CRIT_BONUS_KEY)) {
                return weapon.getItemMeta().getPersistentDataContainer().get(CRIT_BONUS_KEY, PersistentDataType.DOUBLE);
            }
        }
        return 0;
    }


//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onasdfjkl(EntityDamageEvent event){
//
//        if(event instanceof  EntityDamageByEntityEvent entity_event){
//
//            entity_event.getFina
//        }
//
//    }

    @EventHandler(priority = EventPriority.LOW)
    public void GlobalDamageListener(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof LivingEntity))return; //things like itemframes
        double original_base = event.getDamage(BASE);
        boolean fully_charged = false;
        double charge_amount = -1.0;

        /*
            CRIT SUPPRESSION (this allows us to override with our own crit system)
         */
        if(event.isCritical()) {
            double crit_suppression = event.getDamage()*0.6666; //inverse of 1.5x, extra 6 for safe measure <_<
            event.setDamage(crit_suppression);
            original_base = crit_suppression;
        }


//        Debug.broadcast("damage", " ");

//        Debug.broadcast("damage", "<gray> ------- <white>"+event.getDamager().getName()+"</white> -> <white>"+event.getEntity().getName()+"</white> ------- </gray>");

        Entity damager = event.getDamager();
        CustomPlayer customPlayer = CoreUtil.getPlayer(damager.getUniqueId());
        String extramsg = "";



        if(damager instanceof Projectile projectile){
            if(projectile.getPersistentDataContainer().has(ARROW_DAMAGE_KEY)) {
                double multiplier = projectile.getPersistentDataContainer().get(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE);
                event.setDamage(BASE, original_base * multiplier);

                String modifiers = "";

                if (event.isApplicable(ARMOR)) {
                    double armor_resist = event.getDamage(ARMOR);
                    event.setDamage(ARMOR, armor_resist * multiplier);
                }
                if (Debug.isAnyoneListening("damage", false)) {
                    for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
//            if(event.getDamage(m)!=0)
                        modifiers += "\n<gray>" + m.name() + "</gray>: " + Debug.formatDecimal(event.getDamage(m));
                    }
                    if (event.getEntity() instanceof Player p) {
                        Debug.message(p, "damage", "Arrow Damage: <red>" + Debug.formatDecimal(original_base) + (event.isCritical() ? "<yellow>[CRIT]</yellow>" : "") +
                                " <gold>[<gray>🏹</gray>x" + Debug.formatDecimal(multiplier) + "]</gold>" + "</red> Final: <red>" + Debug.formatDecimal(event.getFinalDamage()), modifiers);
                    }
                }
            }
        }





        /**
         * Calculate Crit Modifier
         */
        double weapon_bonus_crit = 0.0;
        if(event.isCritical()){
            if(customPlayer!=null) {
                if(damager instanceof Player dmger) {
                    ItemStack item = dmger.getEquipment().getItemInMainHand();
                    PlayerUtil u = PlayerUtil.getPlayerUtil(dmger);
                    int lvl = customPlayer.getSkillLevel(SkillType.GUARDSMAN);
                    weapon_bonus_crit = getCustomWeaponCrit(item) + opening_crit_baseline; // TODO refactor name, for opening crit ONLY
                    if (u.isOnCooldown("crit_bonus") || dmger.getCooldown(item)>0) {
                        weapon_bonus_crit = 0;
                    }
                    int cd = 120 - (lvl*10);
                    PlayerUtil.getPlayerUtil(dmger).setCooldown("crit_bonus", cd);
                    double guardsman_bonus_crit = (standard_crit_guardsman_multiplier * (double)(lvl+1));
                    double base = event.getDamage(BASE);
                    double crit_base_multiplier = (base * standard_crit_base_multiplier);


                    //                crit_add = Math.min(1.5, 0.2 + Math.pow(1.055, lvl)); //slight exponent boost to crit
                    double crit_add = crit_base_multiplier + guardsman_bonus_crit + weapon_bonus_crit ;
                    double new_base = base + crit_add;
//                    extramsg += "<green> [✨+"+Debug.formatDecimal(crit_add)+"]</green>";
                    //            new_damage *= (crit_multiplier); //apply custom crit
                    //            crit_msg = GOLD+" ("+GRAY+"✨ "+GOLD+(Debug.formatDecimal(crit_multiplier) +"x)");
                    event.setDamage(BASE, new_base);
//                    dmger.setCooldown(item, cd);
                    Debug.message(dmger,
                            "damage",
                            //WHITE+victim.getName()+" "+*
                            "<dark_red>Crit: </dark_red><red>" +Debug.formatDecimal(base)+
//                            (WHITE+" ["+BLUE+"🅱: "+Debug.formatDecimal(original_armor)+"]")+
                                    " <yellow>[✨: +"+Debug.formatDecimal(crit_base_multiplier)+"]</yellow>"+
                                    " <aqua>[⚔: +"+Debug.formatDecimal(guardsman_bonus_crit)+"]</aqua>"+
                                    " <green>[⚒: +"+Debug.formatDecimal(weapon_bonus_crit)+"]</green>"+
//                            (event.isCritical()? GREEN+" (CRIT!)":"")+
                                    " [❤ "+Debug.formatDecimal(new_base)+"]</red>",
                            "<gray>Critical hits now work in a blend of scalar and additive.\nThey have two main components:\n" +
                                    "<aqua>- Guardsman Influence</aqua> which adds crit damage linearly\n"+
                                    "<green>- Opening Crit Influence</green> which has a baseline of "+opening_crit_baseline+"\n" +
                                    "blacksmiths can craft weapons with an opening crit bonus\n"+
                                    "An <green>Opening Crit</green> is utilized when a player has not attacked in awhile.\n"
                    );
                }
            }
        }


        if(event.getEntity() instanceof Player) {
            /**
             * Damage Compressor
             */
//            double threshold = 7;
//            double knee = 5.0;  // soft knee width
//            double ratio = 1.5; // compression above knee
//            double previous_base = event.getDamage(BASE);
//            double compressed = compress(previous_base, threshold, knee, ratio);
//            boolean was_compressed = false;
//            if (Math.abs(compressed - previous_base) > 0.0001) {
//                was_compressed = true;
//                event.setDamage(BASE, compressed);
//            }
//
//            String compression_msg = was_compressed ? ("<dark_red>Compressor:</dark_red> <red>" + Debug.formatDecimal(previous_base)
//                    + " <gray>-></gray> "
//                    + "[❤ " + Debug.formatDecimal(compressed) + "]</red>") : "<dark_gray>Uncompressed</dark_gray>";
//            Debug.broadcast(
//                    "damage",
//                    //WHITE+victim.getName()+" "+*
//                    compression_msg,
//                    "<gray>The compressor basically squashes the damage to prevent absurdly high hits." +
//                            " This helps with softening extreme damage modifiers such as Sharpness and Strength potions\n"
//                            + "<red>This modifier is PVP only</red>\n"
//                            + "threshold: <green>" + threshold + "</green>\n"
//                            + "ratio: <green>" + ratio + "</green>\n"
//                            + "knee: <green>" + knee + "</green>\n"
//            );
        }


        /**
         * Calculate Guardsman Modifier
         */
        if (damager instanceof Player player) {
            //Attacker is a player
            charge_amount = player.getAttackCooldown();
            fully_charged = charge_amount >= 1.0f;
            if (charge_amount < 0.225) {
                //prevents auto-click / excessive attack spam
                event.setCancelled(true);
                return;
            }
            guardsmanDamage.applyGuardsmanDamage(customPlayer, event);
//            dynamicArmor.applyRaytracedArmorHit(event);
//            Debug.broadcast("mob", "animal took damage :(");
        } else {
            //Attacker is a Mob
            // This should ONLY apply to mob damage, not PVP damage
            if(event.getEntity() instanceof Player player) {
                //increase damage of mobs to players
                mobManager.onMobAttack(player, event);

            }
        }


        /**
         * Absorption Damage
         */
        double absorption = event.getDamage(ABSORPTION);
        double absorption_to_remove = 0;
        if (absorption < 0) {
            if(event.getDamage(INVULNERABILITY_REDUCTION)==0) {
                if (event.getEntity() instanceof Damageable target) {
                    double absorption_hearts = target.getAbsorptionAmount();
                    if(charge_amount>0.5) {
                        target.setAbsorptionAmount(Math.max(0, absorption_hearts - 1));
                    }
                    event.setDamage(BASE, event.getDamage(BASE)*0);
                }
            }


//            absorption_to_remove = absorption / 2;
        }


        //Finally, apply GLOBAL armor reduction
        if(event.getEntity() instanceof LivingEntity le) {
            if(event.isApplicable(ARMOR)) {
                armorDamageReduction.applyArmorReduction(le, event);
            }
            if(armorBreakSystem != null) {
                extramsg += " <light_purple>" + armorBreakSystem.breakArmorWithItem(le, event) + "</light_purple>";
            }
            if(weapon_bonus_crit>0){
                le.getWorld().playSound(le.getLocation(), Sound.ITEM_WOLF_ARMOR_DAMAGE, 0.25f, 1);
            }
            if(event.isApplicable(BLOCKING)) {
                double blocking_damage = event.getDamage(BLOCKING);
                if (blocking_damage != 0) {
                    event.setDamage(BLOCKING, -event.getDamage(BASE));
                }
            }
        }



        /**
         * ZERO HIT MITIGATION SYSTEM
         * TODO consider making minimum hit 0 if barehanded punch
         */
        double DAMAGE_MINIMUM = 0;
        if(damager instanceof Player player) { //Mobs have a chance to hit zero with this cast
            DAMAGE_MINIMUM = (0.075 * original_base) * (event.isCritical()?1.5:1.0);
            double total_final = calculateTotalDamage(event);
//        Debug.broadcast("damage", "Pre-Minimu calculation: "+total_final);
            if (total_final <= DAMAGE_MINIMUM) {
                Entity entity = event.getEntity();


                for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
                    if (event.isApplicable(m)) {
                        if (m != BLOCKING)
                            event.setDamage(m, 0);
                    }
                }
                event.setDamage(BASE, DAMAGE_MINIMUM);
                extramsg += " <dark_gray>[Minimum]</dark_gray>";

                Sound sound = ArmorStats.getArmorSound(entity);
                if (sound != null) {
//                extramsg += " <dark_gray>[Sound]</dark_gray>";
                    entity.getWorld().playSound(entity.getLocation(), sound, SoundCategory.PLAYERS, 0.75f, ThreadLocalRandom.current().nextFloat(0.1f) + 0.75f);
                }
            }
        }

        //display player CHARGE - ENSURE damager is in survival for testing
        extramsg +=  "<gold> [⚡" + Debug.formatDecimal(charge_amount) + "]</gold>";
        String modifiers = "";

        for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
            modifiers += "\n<gray>"+m.name()+"</gray>: "+Debug.formatDecimal(event.getDamage(m));
        }

        if(damager instanceof Player dmger) {
            if (event.getEntity() instanceof LivingEntity victim) {
                if (!event.isCancelled()) {
                    if (!Specialization.getInstance().getPlayerDownedListener().isDowned(dmger)) {
                        mobManager.applyGuardsmanExp(event, customPlayer, dmger, victim); //Exp is acquired only after calculating final damage
                    }
                }
            }

            double dmg = calculateTotalDamage(event);
            String ss = "";
            if(event.getEntity() instanceof LivingEntity lv) {
                double maxhealth = lv.getAttribute(Attribute.MAX_HEALTH).getValue();
                ss = " Hits-To-Kill: <red>"+(Math.ceil(maxhealth/dmg));
            }
            Debug.message(dmger,
                    "damage",
                    "<dark_red>Final Damage: <red>"+Debug.formatDecimal(calculateTotalDamage(event))+extramsg
                    +ss,
                    "<red>Minimum Hit Required: </red>"+DAMAGE_MINIMUM+""+modifiers
            );
        }
        if(event.getEntity() instanceof Player victim){
            //display player CHARGE - ENSURE damager is in survival for testing
            double dmg = calculateTotalDamage(event);
            double maxhealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            Debug.message(victim,
                    "damage",
                    "<dark_red>📩 Damage: <red>"+Debug.formatDecimal(dmg)+extramsg
                    +" Hits-To-Die: <red>"+(Math.ceil(maxhealth/dmg)),
                    "<red>Minimum Hit Required: </red>"+DAMAGE_MINIMUM+""+modifiers
            );
        }

    }


    /**
     * Temporary max health for mobs
     */
    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event){
//        AttributeInstance attribute = event.getEntity().getAttribute(Attribute.MAX_HEALTH);
//        double max_health = attribute.getValue()*2;
//        attribute.setBaseValue(max_health);
//        event.getEntity().setHealth(max_health);
    }

    /**
     * Custom Mob Drops
     */
    @EventHandler
    public void addCustomMobDrops(EntityDeathEvent e){
        if (e.getEntity().getKiller() != null) {
            Player player = e.getEntity().getKiller();
            assert player != null;
//            CustomPlayer killer = CoreUtil.getPlayer(e.getEntity().getKiller().getUniqueId());
//            EntityType entity = e.getEntity().getType();
            List<NamespacedKey> items = SpecializationConfig.getMobDropsConfig().get(e.getEntityType(), new TypeToken<>() {});
            Material.matchMaterial(e.getEntityType().getKey().getKey());
        }
    }



    /**
     * Calculates the total resulting damage after all Paper/Bukkit modifiers are applied.
     */
    public static double calculateTotalDamage(EntityDamageByEntityEvent event) {
        double total = 0.0;
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            try {
                total += event.getDamage(modifier);
            } catch (IllegalArgumentException ignored) {
                // Modifier not applicable for this event
            }
        }
        return total;
    }



}