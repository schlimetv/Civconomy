# AnimalBreeding

A Minecraft 1.21 Paper plugin for enhanced animal breeding mechanics.

## Requirements
- Java 23
- Paper 1.21.x

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/AnimalBreeding-1.0.0.jar`

## Commands

| Command        | Description           | Permission             |
|----------------|-----------------------|------------------------|
| `/ab help`     | Show help             | `animalbreeding.use`   |
| `/ab reload`   | Reload config         | `animalbreeding.admin` |
| `/ab cooldown` | Check your cooldown   | `animalbreeding.use`   |

## Configuration

Edit `plugins/AnimalBreeding/config.yml` to adjust cooldowns, supported animals, and more.
