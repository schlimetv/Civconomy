package com.minecraftcivilizations.specialization.Player;

import com.google.common.collect.Queues;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Events.SkillLevelChangeEvent;
import com.minecraftcivilizations.specialization.Listener.Player.XpGainMonitor;
import com.minecraftcivilizations.specialization.Skill.Classless;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.LoreUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.minecraftcivilizations.specialization.Skill.Skill.getXPNeededForLevel;
import static com.minecraftcivilizations.specialization.Skill.Skill.mapValue;
import static com.minecraftcivilizations.specialization.Skill.SkillType.getDisplayName;

@Getter
public class CustomPlayer extends minecraftcivilizations.com.minecraftCivilizationsCore.Player.CustomPlayer {
    @Getter
    @Setter
    private SkillType preferredSkill = SkillType.values()[ThreadLocalRandom.current().nextInt(SkillType.values().length)];
    @Getter
    List<Skill> skills = new ArrayList<>(0);
    @Setter
    @Getter
    private double height = 0;
    @Getter
    @Setter
    private boolean isAdvancedClassesGUIEnabled = false;
    @Getter
    @Setter
    private boolean isSoundEnabled = true;
    @Getter
    @Setter
    private boolean isNewRecipeGUIIteration = false;
    @Getter
    @Setter
    private AnalyticPlayerData analyticPlayerData = new AnalyticPlayerData();
    @Getter
    private boolean isDowned = false;
    @Getter
    @Setter
    private long lastDowned = System.currentTimeMillis();
    @Getter
    @Setter
    private boolean wasDownedOnLogout = false;
    @Getter
    private final List<NamespacedKey> additionUnlockedRecipes = new ArrayList<>();
    @Getter
    private final HashSet<UUID> leashedOtherPlayers = new HashSet<>();
    @Setter
    private UUID leashedTo = null;
    private final Queue<Material> lastEatenFood = Queues.newConcurrentLinkedQueue();

    public CustomPlayer(UUID uuid) {
        super(uuid);
        loadPlayer();

    }

    private void loadPlayer() {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().load(this.getUuid());

        if (customPlayer != null) {
            //Loads existing player
            this.skills = customPlayer.skills;
            this.preferredSkill = customPlayer.preferredSkill;
            this.height = customPlayer.height;
            this.isAdvancedClassesGUIEnabled = customPlayer.isAdvancedClassesGUIEnabled;
            this.isSoundEnabled = customPlayer.isSoundEnabled;
            this.isNewRecipeGUIIteration = customPlayer.isNewRecipeGUIIteration;
            this.analyticPlayerData = customPlayer.analyticPlayerData;
            this.additionUnlockedRecipes.addAll(customPlayer.additionUnlockedRecipes);
            return;
        }

        // New player - initialize with default skills
        for (SkillType skill : SkillType.values()) {
            Skill skill1 = new Skill(skill, 0, System.currentTimeMillis());
            skill1.setSkillType(skill);
            this.skills.add(skill1);
        }

        Player player = Bukkit.getPlayer(getUuid());

        if (player == null) return;

        Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_BREAK_SPEED)).setBaseValue(0);
        Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_BREAK_SPEED)).setBaseValue(0);
    }


    /**
     * Classic straightforward add XP
     */
    public void addSkillXp(SkillType skillType, double xp) {
        addSkillXp(skillType, xp, null, false, false);
    }

    /**
     * Silent for Combat XP
     */
    public void addSkillXp(SkillType skillType, double xp, boolean silent) {

        addSkillXp(skillType, xp, null, false, silent);
    }

    /**
     * Add XP with physical location for sound
     */
    public void addSkillXp(SkillType skillType, double xp, Location soundlocation) {

        addSkillXp(skillType, xp, soundlocation, false, false);
    }

    /**
     * Add XP with all parameters
     */
    public void addSkillXp(SkillType skillType, double xp, Location soundLocation, boolean allowNegative, boolean silent) {
        Player player = Bukkit.getPlayer(getUuid());

        if (skillType == null || xp == 0) return;
        int previousLevel = this.getSkillLevel(skillType);
        Skill skill = getSkill(skillType);
        skill.applyXp(player, xp, allowNegative);

        boolean negative = xp<0;
        String color = (negative)?"red":"green";
        if(negative) {
            PlayerUtil.message(player,"XP LOSS: " + skillType.name() + ": " + skill.getXp() + " (+ " + ((xp > 0) ? ChatColor.GREEN : ChatColor.RED) + xp + ")");
        }
        Component simple_xp_msg = MiniMessage.miniMessage().deserialize(
                "<gray>"+skill.getXp()+"</gray> " +
                "<"+color+">(" +(negative?"":"+") +xp+")</"+color+"> " +
                "<gray>"+getDisplayName(skillType)+"</gray>");

        player.sendActionBar(simple_xp_msg);
        int currentLevel = this.getSkillLevel(skillType);
        if (!silent && this.isSoundEnabled) {
            float pitch = 0.8f + (float) (Math.random() * 0.4f); // random between 0.8–1.2
            if(soundLocation != null){
                player.playSound(soundLocation.add(0.5, 0.5, 0.5), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.UI, 0.02f, pitch);
            } else {
                player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.UI, 0.05f, pitch);
            }
        }
        XpGainMonitor.handleXpGain(player, skillType, xp);

        // Debug XP if applicable
        if (Debug.isListeningToChannel(player, "xp")){
            try {
                Debug.message(player, "xp",
                        MiniMessage.miniMessage().deserialize("xp: ")
                                .append(simple_xp_msg)
                                .append(Component.space()),
                        null
                );
            }catch(Exception e){
                e.printStackTrace();
                Specialization.getInstance().getLogger().info("BAD DEBUG in CustomPlayer.java");
            }
        }

        // Update team assignment based on highest skill
//        TeamManager.setTeam(Bukkit.getPlayer(getUuid()));


        if (previousLevel != currentLevel) {
            SkillLevelChangeEvent level_change_event = new SkillLevelChangeEvent(this, player, skillType, previousLevel, currentLevel, xp);
            Bukkit.getPluginManager().callEvent(level_change_event);
            applyEffects(); //disabled for testing new combat
            String skill_name = SkillType.getDisplayName(skillType);
            if (previousLevel < currentLevel) {
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 100, 1);
                PlayerUtil.message(player, LoreUtils.createLoreLine("You have leveled up " + skill_name + ", you are now " + SkillType.getDisplayName(skillType) + " " + SkillLevel.getDisplayName(currentLevel), NamedTextColor.WHITE));
                Debug.broadcast("levelup", player.getName()+" <gray>leveled up <yellow>"+skill_name+ "</yellow> to level <green>"+currentLevel);
            } else {
                player.playSound(player, Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, 100F, 1.5F);
                PlayerUtil.message(player, LoreUtils.createLoreLine("Your " + skill_name + "ing ability has deteriorated, you are now " + SkillType.getDisplayName(skillType) + " " + SkillLevel.getDisplayName(currentLevel), NamedTextColor.WHITE));
            }
            while (currentLevel > 0) {
                Set<NamespacedKey> recipes =
                        SpecializationConfig.getUnlockedRecipesConfig().get(skillType.name() + "_" + SkillLevel.getSkillLevelFromInt(currentLevel), new TypeToken<>(){});
                for (NamespacedKey entry : recipes) {
                    player.discoverRecipe(entry);
                }
                currentLevel--;
            }

        }
    }


    public void applyEffects(){
        Player player = Bukkit.getPlayer(getUuid());
        if (player == null) return;

        // First: remove all infinite-duration potion effects (these are the ones we manage)
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() == -1) {
                player.removePotionEffect(effect.getType());
            }
        }

        // Then: re-apply effects only for skills above Novice (tier 0)
        Arrays.stream(SkillType.values()).forEach(skill -> {
            if (getSkillLevel(skill) <= 0) return;

            List<Pair<NamespacedKey, Integer>> potions = SpecializationConfig.getClassSkillEffectsConfig().get(skill + "_" + getSkillLevelEnum(skill), new TypeToken<>(){});
            for(Pair<NamespacedKey, Integer> dataEffect : potions) {
                if(dataEffect.secondValue() < 0) continue;
                NamespacedKey safeKey = NamespacedKey.fromString(dataEffect.firstValue().toString().toLowerCase());
                PotionEffectType potionEffectType = Registry.EFFECT.get(safeKey);
                if(potionEffectType == null) throw new IllegalStateException("invalid potion effect type in config: " + dataEffect.firstValue());
                // Only apply if no existing effect of this type has a higher amplifier
                if(player.getActivePotionEffects().stream().noneMatch(effect -> effect.getType().equals(potionEffectType) && effect.getAmplifier() > dataEffect.secondValue())){
                    player.removePotionEffect(potionEffectType);
                    player.addPotionEffect(new PotionEffect(potionEffectType,-1, dataEffect.secondValue(), false, false, true));
                }
            }
        });
    }

    public SkillLevel getSkillLevelEnumByXpOnly(SkillType skillType) {
        double xp = getSkill(skillType).getXp();
        int level = 0;
        while (level < SkillLevel.values().length && xp >= Skill.getXPNeededForLevel(level + 1)) {
            level++;
        }
        return SkillLevel.getSkillLevelFromInt(level);
    }


    public int getSkillLevel(SkillType skillType) {
        if(skillType==null)return 0;

        /**
         * This code is a patch that is highly optimized, but allows multi-classing
         * -Alec
         */
//        Skill skill = getSkill(skillType);
//        double xp = skill.getXp();
//
//        double[] cached_levels = Skill.CACHED_LEVELS;
//        int last_level = cached_levels.length - 1;
//
//        for (int lvl = 0; lvl < last_level; lvl++) {
//            if (xp < cached_levels[lvl + 1]) return lvl;
//        }
//
//        return last_level; // max level

        //Shhhh, there... it's all over now... Just close your eyes and rest 💀💀💀

        int level;
        // So, so sorry if you have to read this, it was fixed about 10 times and I forgot to call it, so now it looks like this :sad:
        level = 0;
        while (level < SkillLevel.values().length && !isMissingXpForLevel(skillType, level+1) && !isMissingPercentForLevel(skillType, level+1)) {
            level++;
        }

        return Math.min(5, level); // prevents levels above 5
    }


    public SkillLevel getSkillLevelEnum(SkillType skillType) {
        return SkillLevel.getSkillLevelFromInt(getSkillLevel(skillType));
    }

    /**
     * Returns true if this player is "Classless" — all skills at Novice (tier 0).
     * This is the case for new players and after death (which resets all XP to 0).
     */
    public boolean isClassless() {
        return Classless.isClassless(this);
    }

    public double getTotalXp() {
        double totalXp = 0;
        for (Skill skill : this.skills) {
            totalXp += skill.getXp();
        }
        return totalXp;
    }

    private boolean isMissingXpForLevel(SkillType skillType, int level) {
        return getSkill(skillType).getXp() < getXPNeededForLevel(level);
    }

    private boolean isMissingPercentForLevel(SkillType skillType, int level) {
        return getPercentOfTotal(skillType) < SpecializationConfig.getSkillRequirementsConfig().get(skillType + "_" + SkillLevel.getSkillLevelFromInt(level) + "_REQUIREMENT", Double.class);
    }

    public double getGUIDistributionOfTotalSkills(SkillType skillType) {
        return mapValue(getSkill(skillType).getXp(), 0, getTotalXp(), 0, 3);
    }

    public double getGUIDistributionOfTotalLevels(SkillType skillType) {
        int level = getSkillLevel(skillType);
        double XPMin = level == 0 ? 0 : getXPNeededForLevel(level);
        double XPMax = getXPNeededForLevel(level + 1);

        Skill skill = getSkill(skillType);
        double percentageNeededMin = SpecializationConfig.getSkillRequirementsConfig().get(skill.getSkillType() + "_" + SkillLevel.getSkillLevelFromInt(level) + "_REQUIREMENT", Double.class);
        double percentageNeededMax = SpecializationConfig.getSkillRequirementsConfig().get(skill.getSkillType() + "_" + SkillLevel.getSkillLevelFromInt(level + 1) + "_REQUIREMENT", Double.class);
        double currentPercentage = getPercentOfTotal(skillType);

        double XPProgressAsPercentage;
        if(skill.getXp() <= XPMax) {
            XPProgressAsPercentage = mapValue(skill.getXp() - XPMin, 0, XPMax - XPMin, 0, 100);
        }else XPProgressAsPercentage = 100.0;

        double percentageProgressAsPercentage;

        if(currentPercentage <= percentageNeededMax) {
            percentageProgressAsPercentage = mapValue(currentPercentage - percentageNeededMin, 0, percentageNeededMax - percentageNeededMin,0,100);
        }else if(getTotalXp() == 0){
            percentageProgressAsPercentage = 0;
        } else percentageProgressAsPercentage = 100.0;
        return Math.round(XPProgressAsPercentage * percentageProgressAsPercentage * .01);
    }

    public boolean isDownedTimeout() {
        if (isDowned) {
            return lastDowned + 80 <= System.currentTimeMillis();
        }
        return false;
    }

    public double getPercentOfTotal(SkillType skillType) {
        return mapValue(getSkill(skillType).getXp(), 0, getTotalXp(), 0, 100);
    }

    public void setDowned(boolean downed) {
        if (this.isDowned != downed) {
            this.isDowned = downed;
            if (!downed){
                Player player = Bukkit.getPlayer(CustomPlayer.this.getUuid());
                player.leaveVehicle();
                return;
            }
            lastDowned = System.currentTimeMillis();
            new BukkitRunnable() {
                final double totalTime = SpecializationConfig.getDownedConfig().get("TIME_TO_DEATH_IN_TICKS", Double.class);
                double currentTime = 0;
                @Override
                public void run() {
                    if (!isDowned) {
                        this.cancel();
                        return;
                    }
                    if (currentTime >= totalTime && CustomPlayer.this.isDowned()) {
                        Bukkit.getPlayer(CustomPlayer.this.getUuid()).setHealth(0);
                    }
                    currentTime ++;
                }
            }.runTaskTimer(MinecraftCivilizationsCore.getInstance(), 0, 1);
        }
    }

    public Skill getSkill(SkillType skillType) {
        for (Skill skill : this.skills) {
            if (skill.getSkillType() == skillType) {
                return skill;
            }
        }
        throw new IllegalStateException("Couldn't get skill " + skillType.toString());
    }


    /**
     * TODO PDC-xp-hotfix for later if we need it
     *
    public void reloadSkillsXp(@Nullable Player player) {
        if(player.){
           if player doesn't have PDC, do nothing, but store existing values
        }
        for(SkillType type : SkillType.values()){
            if(type.){

            }
        }
    }
    */

    @Data
    public static class AnalyticPlayerData {
        int deaths; // Total cumulative deaths (kept for backward compatibility)
        int deathsThisPeriod; // Deaths in current 5-minute period
        int complexItemsCraftedThisPeriod; // Complex items crafted in current 5-minute period
        // Map of Item Material -> Count
        Map<String, Integer> complexItemsCraftedDetailsThisPeriod = new HashMap<>();

        public void incrementDeathsThisPeriod() {
            this.deathsThisPeriod++;
            this.deaths++; // Also increment total for backward compatibility
        }

        public void incrementComplexItemsCrafted(String materialName) {
            this.complexItemsCraftedThisPeriod++;
            this.complexItemsCraftedDetailsThisPeriod.merge(materialName, 1, Integer::sum);
        }
        
        @Deprecated
        public void incrementComplexItemsCrafted() {
            this.complexItemsCraftedThisPeriod++;
        }

        public void resetDeathsForPeriod() {
            this.deathsThisPeriod = 0;
        }

        public void resetComplexItemsForPeriod() {
            this.complexItemsCraftedThisPeriod = 0;
            this.complexItemsCraftedDetailsThisPeriod.clear();
        }
    }

    /**
        returns true if the food hasn't been eaten in the last 5 foods, false otherwise
     */
    public boolean eatFood(Material food){
        boolean result = !lastEatenFood.contains(food);
        lastEatenFood.add(food);
        if(lastEatenFood.size() > 5){
            lastEatenFood.poll();
        }
        return result;
    }


    public static CustomPlayer getCustomPlayer(Player player){
        return CoreUtil.getPlayer(player);
    }

}
