# Civconomy / Specialization — Session Transcript

A chronological summary of the multi-day work session covering bug investigation,
diamond-gear feature work, the blacksmith-extraction refactor, and the architectural
discussion that followed.

---

## 1. Bred-horse taming bug investigation

**Bug report (user):** "horses that are bred are unable to get tamed."

**Investigation:**
- Surveyed every plugin file that touches horse breeding, taming, or related events.
- Located the only direct event handlers in
  `main/java/com/minecraftcivilizations/specialization/Listener/Player/Interactions/PlayerInteractEntityListener.java`:
  - `onPlayerBreed(EntityBreedEvent)` (lines 38–46) — cancels breeding if `FARMER`
    skill is below `FARMER_BREED_LEVEL_<MOB_TYPE>`. Default for `HORSE` is 2 (JOURNEYMAN).
  - `onPlayerTame(EntityTameEvent)` (lines 48–57) — cancels taming if `FARMER` skill
    is below the configured threshold. Default for `TAME_HORSE` is 0 (NOVICE), so any
    player passes.
- Verified `MobManager.onCreatureSpawn` short-circuits for HORSE because
  `setDefaultRuleSetChance(100, …, HORSE, …)` is set with no override variations
  — `rollMobOverrideRule(HORSE)` returns `null`, so `convertEntityToVariation` /
  `applyStatsToEntity` never run on bred or wild horses.
- Confirmed the plugin does not call `setTamed`/`setOwner`, doesn't write any PDC
  to horse entities, and doesn't cancel `VehicleEnterEvent`/`PlayerInteractEntityEvent`
  for horses (only `DOLPHIN` is cancelled in `MobManager.onMobMount`).

**User clarification:** "wild horses are tameable."

This narrowed the bug: the plugin treats wild and bred horses identically through
identical code paths. No horse-specific divergence was found in the Specialization
source. Suspects flagged as remaining: the `MinecraftCivilizationsCore` dependency
(source not in repo), other server-side plugins, or vanilla Paper/Mojang behavior
(MC-221539 was identified as a related vanilla bug but doesn't fit the symptom).

**Output:** `horse_taming_bug_report.txt` written to project root with the full
audit, ruled-out hypotheses, remaining suspects, and suggested in-game debug steps.

---

## 2. CustomItem / Recipe / Blueprints / MetalworkingRecipes familiarization

Read four referenced files (the "CustomRecipe" reference resolved to
`Recipe/Recipes.java`). Findings:

- **Two parallel `CustomItem` abstractions exist.**
  - Local: `com.minecraftcivilizations.specialization.CustomItem.CustomItem` —
    used by `MetalworkingItems`, `DefineCustomItems`, `Bandage`, `CustomWeapon`,
    and every reference inside `MetalworkingRecipes.java`.
  - Core: `minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItem` —
    used only by `Blueprints.java`.
- `CustomItemManager` is the central event-dispatch hub; it routes Bukkit events
  to the right `CustomItem` subclass based on the
  `specialization:custom_item_id` PDC tag.
- `MetalworkingRecipes.java` (~1100 LOC) registers every plate/plate-set/component
  recipe and the `SmithingTransformRecipe` chain that drives final smithing-table
  assembly. Helpers `stack(id)` and `exact(id)` keep ExactChoice matching tied
  to specific custom items rather than vanilla materials.
- `Blueprints.java` auto-generates "learn-recipe" paper items via Core's
  `CustomItemRegistry`; right-clicking calls `player.discoverRecipe(key)`.
- `Recipes.java` is the orchestration entry point — it removes vanilla iron/gold/
  copper armor + tool recipes, registers replacement recipes (netherite upgrade,
  beacon, bell, cobweb, cat spawn egg, all 16 armor trims), then delegates to
  `MetalworkingRecipes.register()`.

---

## 3. Diamond gear feature

**User instruction (`instructions.txt`):** Add a diamond-gear progression mirroring
copper, with 3 diamonds → 2 plates, 3 plates → 2 plate sets (note the unusual 3→2
ratio for plate sets), plate sets → intermediate components, and final smithing
on a toughened-steel base.

After clarifying the ambiguous slot layout for tool smithing and confirming that
toughened-steel detection should use a new PDC key, the implementation landed:

- **`SmithingAssemblyListener.java`**
  - Added public constant `TOUGHENED_STEEL_KEY = specialization:toughened_steel`.
  - `applySteelStats` now stamps non-purple (blue/Toughened) results with
    `TOUGHENED_STEEL_KEY`.
  - `onPrepareSmithing` routes any `diamond_*_blueprint` template into a new
    `handleDiamondSmithing` that validates the base carries `TOUGHENED_STEEL_KEY`,
    matches blueprint + diamond component + iron base, sets a vanilla
    `DIAMOND_*` result, and transfers steel-base remaining-durability % onto it.
  - Added diamond entries to `SMITHING_XP`.

- **`MetalworkingItems.java`**
  - Added `initMetalIntermediates("diamond", Material.DIAMOND, "§b")` to register
    plates, plate sets, the four armor pieces, and five tool heads.
  - Extended `initBlueprints` to include `"diamond"`, with the hammer blueprint
    skipped (no diamond-tier hammer).

- **`MetalworkingRecipes.java`**
  - Added `registerDiamondPlates` (the 3→2 plate-set ratio).
  - Reused `registerArmorPieces("diamond", false, "", …)` and
    `registerToolHeads("diamond", false, "", …)` for component crafting.
  - Reused `registerBlueprints("diamond", …)` for blueprint crafting.
  - Added `registerDiamondArmorSmithing` and `registerDiamondToolSmithing`. Both
    use `MaterialChoice(IRON_*)` for the base — the listener does the toughened-
    steel validation and durability transfer at prepare time.

- **`Recipes.java`** — vanilla diamond armor + tool recipes unregistered to mirror
  the iron/gold/copper pattern.

---

## 4. Leather-armor durability transfer for metal smithing

**User instruction:** "For bronze, copper, iron, and steel armor, accept damaged
leather armor but apply the same durability transfer for the final recipe result."

Then the formula was clarified to: `result_damage% = leather_damage% / 2` (a
damaged leather chestplate yields a metal chestplate at half the damage —
pristine leather yields a pristine result; a fully broken leather caps the result
at 50% damage).

**Implementation:**
- `MetalworkingRecipes.registerArmorSmithing` for copper now uses
  `MaterialChoice(LEATHER_*)`. Gold keeps `ExactChoice` (gold was not in the
  user's scope).
- `MetalworkingRecipes.registerBlueprintArmorSmithing` for iron/bronze/steel
  switched to `MaterialChoice(LEATHER_*)`.
- Refactored `SmithingAssemblyListener.transferDurabilityPercentage` into a
  parameterized `transferDamagePercentage(base, result, damageMultiplier)`:
  - `1.0` for diamond (existing 1:1 transfer, unchanged behavior).
  - `0.5` for the new copper/bronze/iron/steel leather-base path.
- Added a new `@EventHandler(priority = HIGHEST)` named
  `onMetalArmorLeatherDurabilityTransfer` that fires after the existing blueprint
  flow and after vanilla's `SmithingTransformRecipe` produces a copper result. It
  walks a static `LEATHER_DURABILITY_TRANSFER_TARGETS` set of the 16 metal
  armor-piece custom-item ids and applies the half-damage transfer.

---

## 5. Diamond config gating

**User instruction:** Only level-5 (GRANDMASTER) `BLACKSMITH` can craft diamond
gear via the smithing-table flow; only level-5 `LIBRARIAN` can craft diamond
blueprints.

**Change:** `Specialization/unlockedRecipesConfig.properties` updated:
- `BLACKSMITH_GRANDMASTER` got 20 new keys (`armor_plate_diamond`,
  `diamond_armor_plateset`, the four armor components, the five tool heads, the
  four armor smithing recipes, and the five tool assembly recipes).
- `LIBRARIAN_GRANDMASTER` got 9 new keys (`diamond_{helmet|chestplate|leggings|
  boots|sword|axe|pickaxe|hoe|shovel}_blueprint`).

`CraftingListener.shouldBlockRecipe` already iterates every
`SkillType_SkillLevel` config and gates by `SkillLevel.ordinal() == 5`, so no
code changes were needed.

---

## 6. XP rewards for diamond crafting

- **Smithing table:** `SMITHING_XP` updated to grant `BLACKSMITH` XP equal to
  10 × the number of plate sets used in the addition component (e.g.
  `diamond_helmet_smithing` = 50, `diamond_chestplate_smithing` = 80, …,
  `diamond_shovel_assembly` = 10).
- **Diamond plate / plate set crafting:** Added two `armor_plate_diamond` /
  `diamond_armor_plateset` entries to `CraftingListener.CUSTOM_CRAFT_XP`, both
  granting 6 BLACKSMITH XP.
- **Diamond blueprints:** Extended `CraftingListener.onBlueprintCraft`'s ladder
  from `bronze=3, iron=5` to also include `diamond=20` LIBRARIAN XP.

---

## 7. error.txt root-cause fix (durability-transfer NPE)

`error.txt` showed dozens of:

```
java.lang.IllegalStateException: We don't have max_damage! Check hasMaxDamage first!
  at SmithingAssemblyListener.transferDamagePercentage(...:296)
  at SmithingAssemblyListener.handleDiamondSmithing(...:277)
```

`Damageable.getMaxDamage()` throws when an item doesn't carry an explicit
`max_damage` data-component override — which is the case for vanilla
`LEATHER_HELMET`, fresh `IRON_HELMET`, and fresh `DIAMOND_HELMET`. The throw
aborted `handleDiamondSmithing` before `event.setResult(diamond)` ran, so the
result slot kept whatever vanilla produced — a `DIAMOND_HELMET` with the steel
base's components copied on top (steel equippable model, custom max-damage,
`Toughened` lore). Visually it looked like a steel helmet.

**Fix in `SmithingAssemblyListener.java`:** introduced two helpers,
`effectiveMaxDamage(stack)` and `effectiveDamage(stack)`, that check
`hasMaxDamage()` / `hasDamage()` first and fall back to
`Material.getMaxDurability()`. `transferDamagePercentage` now uses these helpers
instead of calling `Damageable.getMaxDamage()` directly. Once the throw was gone,
`event.setResult(new ItemStack(DIAMOND_HELMET))` ran and the slot held a clean
diamond piece without inherited steel components.

---

## 8. Blacksmith / metalworking extraction — audit and refactor

User requested moving all smithing-related code into a separate plugin. Process:

### Audit pass

Produced a tier-by-tier file-move list:

- **Tier 1 — moves whole-cloth (8 files, ~5,576 LOC):** `MetalworkingItems`,
  `IronBloomSystem`, `MetalworkingRecipes`, `Blueprints`, `SmithingAssemblyListener`,
  `FurnaceListener`, `ForgeSmithingSoundListener`, `FurnaceSoundListener`.
- **Tier 2 — split:** `Recipes.java` (vanilla-recipe overrides + general non-
  metalworking recipes split out), `CraftingListener.java` (steel-blueprint and
  metalworking-craft handlers split out).
- **Tier 3 — stays untouched:** `HoeTillingListener`, `ReinforcementManager`,
  `RecipeBlocker`, `BlacksmithArmorTrim` (only string-based references to
  metalworking items).
- **Tier 4 — public API:** combat code's references to
  `SmithingAssemblyListener.PURPLE_STEEL_KEY` would route through a new
  `MetalworkingApi` class.
- **Tier 5 — `CustomItem` / `CustomItemManager` location:** three options
  presented (keep in Specialization, promote to Core, duplicate).

### User direction on the audit

- Vanilla recipe removals stay in Specialization (don't move).
- `rail_alt`'s `armor_plate_iron` reference resolved by importing the BO class.
- Wrought-iron logic stays in Specialization but consults a tag/registry
  exposed by BO.
- `ArmorEquipAttributes` uses a public API class.
- Tier 5: keep `CustomItem` / `CustomItemManager` in Specialization. Specialization
  owns all listener registrations and `init()` calls; BO simply hosts the classes.

### Implementation

**Two project folders established:**
- `Civconomy/blacksmith overhaul plugin/` — empty, populated from scratch
- `Civconomy/specialization stripped of blacksmith/` — fresh clone of the
  original, edited in place

**BlacksmithOverhaul project skeleton:**
- `build.gradle` — paperweight, `compileOnly` Core, `compileOnly` stripped-
  Specialization JAR, shadowJar, paperweight reobf.
- `settings.gradle`, gradle wrapper copied over.
- `main/resources/plugin.yml` — name `BlacksmithOverhaul`, depend `[Core]`,
  `softdepend: [Specialization]`.

**File moves (Python script):** copied 8 files into the new project under
packages
- `com.minecraftcivilizations.blacksmithoverhaul.item.*` (MetalworkingItems, IronBloomSystem)
- `com.minecraftcivilizations.blacksmithoverhaul.recipe.*` (MetalworkingRecipes, Blueprints)
- `com.minecraftcivilizations.blacksmithoverhaul.listener.*` (SmithingAssemblyListener, FurnaceListener, ForgeSmithingSoundListener, FurnaceSoundListener)

with package declarations rewritten and all internal cross-imports between
metalworking files updated to point at the new packages. Cross-package
references to `CustomItem` and `CustomItemManager` were added explicitly (those
classes were previously package-private neighbors).

**New BO files:**
- `BlacksmithOverhaul.java` — minimal `JavaPlugin` (no-op `onEnable` aside from
  setting `instance`).
- `api/MetalworkingApi.java` — public surface with:
  - `PURPLE_STEEL_KEY`, `TOUGHENED_STEEL_KEY` (both still in `specialization:`
    namespace so existing PDC tags continue to match).
  - `CUSTOM_ITEM_ID_KEY` mirror, so the API can read a stack's custom-item id
    without importing `CustomItemManager`.
  - `getCustomItemId(stack)` and `isWroughtIron(stack)` — the latter is the
    "tag/registry" the Specialization gate consults.
- `SmithingAssemblyListener` updated to reference `MetalworkingApi.PURPLE_STEEL_KEY`
  and `MetalworkingApi.TOUGHENED_STEEL_KEY` rather than declaring them itself.

**Stripped Specialization:**
- Deleted the 8 moved files.
- `Specialization.java` — removed metalworking imports, the five
  `registerEvents(new …)` calls, and the `Blueprints.init()` call. Re-added
  them as imports of the new BO packages once BO was building.
- `DefineCustomItems.java` — `MetalworkingItems.init()` call temporarily
  removed for bootstrap, then re-added as an import from BO.
- `Recipes.java` — `MetalworkingRecipes.register(failed)` call removed during
  bootstrap, restored afterward; `rail_alt` recipe wrapped in a null guard so it
  silently skips when `armor_plate_iron` isn't registered.
- `RecipeRefreshCommand.java` — `Blueprints` reference temporarily removed,
  later restored as a BO import.
- `Combat/ArmorEquipAttributes.java` and `Combat/ArmorDamageReduction.java` —
  references rewritten from
  `com.minecraftcivilizations.specialization.Listener.Player.Inventories.SmithingAssemblyListener.PURPLE_STEEL_KEY`
  to `MetalworkingApi.PURPLE_STEEL_KEY`.
- `CraftingListener.onPrepareItemCraft` — wrought-iron branch's hardcoded
  `"wrought_iron_ingot".equals(custom.getId())` rewritten to
  `MetalworkingApi.isWroughtIron(ingredient)`.

**Bootstrap dance:** Stripped Specialization built first (without any BO
imports), then BO compiled against that JAR, then Specialization rebuilt with
the BO imports added. After bootstrap, both jars build clean against each
other's previously-built jars. Final state: two jars, ~1.99 MB Specialization
and ~1.15 MB BlacksmithOverhaul.

---

## 9. Discussion: how does BO reach into Specialization?

User asked for the mechanics. Explanation given:

**Compile-time:** `compileOnly files("../specialization stripped of blacksmith/build/libs/Specialization-0.0.0.1.jar")`
puts Specialization classes on BO's compile classpath. They're not bundled into
BO's shadowJar — javac sees them, the runtime jar doesn't contain them.

**Runtime:** Each Bukkit plugin gets its own `PluginClassLoader`. Class lookups
chain through `depend:` and `softdepend:` declarations in `plugin.yml`. BO's
`softdepend: [Specialization]` tells Paper to forward unresolved class lookups
to Specialization's class loader, which is how `extends CustomItem`,
`Specialization.getInstance()`, and `customPlayer.addSkillXp(SkillType.BLACKSMITH, n)`
resolve at runtime.

A circular hard `depend` was caught and softened: BO's `plugin.yml` was changed
from `depend: [Specialization]` to `softdepend: [Specialization]`. Specialization
keeps its hard `depend: [BlacksmithOverhaul]` because its `onEnable` directly
calls into BO classes. Resulting load order: Core → BO → Specialization.

---

## 10. Discussion: could BO genuinely have no dependency on Specialization?

User reframed: "BO simply hosts the classes; BO should not depend on
Specialization." Followup: "I want BO code to NOT live inside Specialization's
jar. Explain how a library will achieve this."

The constraint laid out:

1. "Library" in Java has two meanings.
   - Shaded into consumer's jar: BO's classes physically live in Specialization's
     jar. **Not what the user wants.**
   - Separate jar referenced via `compileOnly`: BO stays in its own jar but
     needs some runtime mechanism for cross-plugin class visibility.
2. Paper's plugin class loader is isolated — without `depend:`/`softdepend:` in
   `plugin.yml`, BO classes can't see Specialization classes at runtime.
3. Therefore "BO does not depend on Specialization" has three real
   interpretations:
   - **Pragmatic:** softdepend is fine, status quo holds.
   - **Strict:** redesign every BO class to remove Specialization references —
     a real refactor (BO becomes pure data + Bukkit-API-only listeners with
     callbacks back into Specialization for any skill/XP/CustomItem interaction).
   - **Move shared types to Core:** reverses an earlier "keep in Specialization"
     decision but is the only way to genuinely remove the dependency.

User's choice between these three is pending.

---

## Outputs in the project root

- `horse_taming_bug_report.txt` — section 1 audit document.
- `error.txt` — server log used in section 7 (user-supplied input).
- `instructions.txt` — diamond gear instructions (user-supplied input).
- `blacksmith overhaul plugin/` — new plugin project, sections 8–10.
- `specialization stripped of blacksmith/` — refactored Specialization mirror,
  sections 8–10.
- `Specialization/unlockedRecipesConfig.properties` — diamond gating from
  section 5.
- `session_transcript.md` — this document.
