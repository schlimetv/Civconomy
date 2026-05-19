# AnimalBreeding — Trait & Behavior Reference

Every supported animal (Cow, Pig, Chicken, Sheep) has:

- **0–N regular traits**, where N is the configurable cap (`/abmaxtraits`,
  default **3**). Regular traits are inherited 50/50 from each parent, with
  a small chance of random mutation per breed.
- **Exactly one behavior** (Docile, Skittish, or Aggressive). Behaviors are
  tracked separately, do NOT count against the trait cap, and are inherited
  50/50 from a parent (random if neither parent has one).

Naturally-spawned animals roll for 0–2 starting traits (30 % none / 50 % one /
20 % two) and are assigned a random behavior.

Trait values shown below come from `GeneticsManager` and `TraitListener`.

---

## Regular traits

### Thick Skinned   `§a`
- **+4 max HP** (additive attribute modifier).
- **+1 extra drop stack** on death (clones the first drop, amount 1).

### Chunky   `§6`
- **+2 to any meat drop stacks** on death (beef, pork, chicken, mutton — raw or cooked).

### Rapid Growth   `§b`
- **Babies age 20 ticks faster every server tick** (~2× growth speed).
- Only affects animals that are not yet adult.

### Fertile   `§d`
- **Reduced breeding cooldown.** Applied in `BreedingManager#setCooldown`.

### Giant   `§c`
- **+6 max HP**
- **–20 % movement speed** (multiplicative penalty)
- **+15 % scale** (a bit larger)
- **×1.25 drops** on death (rounded up)
- Conflicts with **Dwarf** — only one survives inheritance.

### Dwarf   `§e`
- **+20 % movement speed**
- **–10 % scale** (a bit smaller)
- **×0.75 drops** on death (min 1)
- Conflicts with **Giant** — only one survives inheritance.

### Nimble   `§3`
- **+30 % movement speed** (stacks with size traits).

---

## Behaviors (exactly one per animal)

### Docile   `§7`
- Vanilla AI; no extra effects.

### Skittish   `§f`
- Pathfinds away from any **non-sneaking player within ~8 blocks**.
- Sneaking (crouching) players are treated as safe and can approach freely
  — useful for feeding a skittish animal.
- Re-targets once per second using real mob pathfinding (no velocity hacks).

### Aggressive   `§4`
- Once per second, ~5 % chance to attack a nearby unfed target.
- Range: 2.5 × 1.5 × 2.5 blocks.
- Eligible targets: non-sneaking players and monsters (`Monster` interface).
- Deals **2 damage** per hit.
- Animals fed within the last 30 minutes won't attack at all.

---

## Visibility (per-viewer)

Labels above animals are gated by **Specialization Farmer level** and a
personal toggle (`/animallabel on|off`):

| Farmer level | Tier | Shown |
|---|---|---|
| 0–1 | — | Nothing |
| 2 (Journeyman)  | 1 | Animal type + gender symbol |
| 3 (Expert)      | 2 | + feeding progress and partner status |
| 4 (Master)      | 3 | + breeding cooldown timer |
| 5 (Grandmaster) | 4 | + behavior name and full trait list |

Each tier is rendered by its own client-side TextDisplay, so two farmers at
different levels standing near the same animal each see content suited to
THEIR level.

---

## Admin commands

- `/abmutation <0-100>` — set the mutation chance (percent).
- `/abmaxtraits <n>` — set the trait cap per animal (0–10).
- `/animallabel <on|off>` — per-player label toggle (Farmer level 2+ only).

Both admin commands persist their value to `config.yml`.
