# Better Leaderboards

![Static Badge](https://img.shields.io/badge/Curseforge?style=flat&logo=curseforge&link=https%3A%2F%2Fwww.curseforge.com%2Fminecraft%2Fmc-mods%2Fbetter-leaderboards-bl)


A Minecraft Forge mod for creating customizable holographic leaderboards that display player statistics in your world.

**Minecraft Version:** 1.20.1  
**Forge Version:** 47.4.13+

---

## Features

- 📊 **Holographic Leaderboards** - Floating armor stand-based leaderboards that display player rankings
- 🎨 **Custom Headers** - Add custom headers with Minecraft color codes support
- 🏆 **Medal Colors** - Top 3 players are highlighted with Gold, Silver, and Bronze colors
- 📈 **Multiple Statistics** - Track various player stats including kills, deaths, mob kills, K/D ratio, and time played
- 🔄 **Auto-Refresh** - Leaderboards automatically refresh every 5 seconds
- 💾 **Persistent Data** - All data is saved per-world and persists across restarts

---

## Commands

All commands use the `/bl` prefix. Leaderboard management commands require **operator permission level 2**.

### Leaderboard Management

#### Create a Leaderboard
```
/bl lb create <id> [statType] [topN] [npcEnabled] [header]
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | string | *required* | Unique identifier for the leaderboard (no spaces) |
| `statType` | string | `kills` | The statistic to display (see [Stat Types](#stat-types)) |
| `topN` | integer | `10` | Number of players to show (1-50) |
| `npcEnabled` | boolean | `false` | Reserved for future use |
| `header` | string | *auto* | Custom header text (supports color codes) |

**Examples:**
```
/bl lb create pvp
/bl lb create killboard kills 5
/bl lb create topkills kills 10 false &6&lTOP KILLERS
/bl lb create deathboard deaths 10 false &c☠ Most Deaths ☠
```

#### Delete a Leaderboard
```
/bl lb delete <id>
```
Removes a leaderboard. You must be in the same dimension and within range of the leaderboard.

**Example:**
```
/bl lb delete pvp
```

#### Move a Leaderboard to Your Position
```
/bl lb moveHere <id>
```
Moves the specified leaderboard to your current position (2.5 blocks above your feet).

**Example:**
```
/bl lb moveHere pvp
```

#### Move a Leaderboard to Coordinates
```
/bl lb moveTo <id> <x> <y> <z>
```
Moves the specified leaderboard to exact coordinates.

**Example:**
```
/bl lb moveTo pvp 100 70 -50
```

#### Refresh Leaderboards
```
/bl lb refresh [id]
```
Manually refreshes all leaderboards, or a specific one if an ID is provided.

**Examples:**
```
/bl lb refresh
/bl lb refresh pvp
```

#### List All Leaderboards
```
/bl lb list
```
Shows all existing leaderboards with their stat type and top-N count.

---

### Player Commands

#### Check Your Rank
```
/bl rank [statType]
```
Displays your current rank and stat value for the specified statistic. If no stat type is provided, defaults to kills. Available to all players.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `statType` | string | `kills` | The statistic to check rank for (see [Stat Types](#stat-types)) |

**Examples:**
```
/bl rank
/bl rank kills
/bl rank deaths
/bl rank kd
/bl rank time
```

---

## Stat Types

The following statistics can be tracked and displayed on leaderboards:

| Stat Type | Aliases | Description | Display Format |
|-----------|---------|-------------|----------------|
| `kills` | `playerkills`, `pk` | Player vs Player kills | Number (e.g., `42 kills`) |
| `deaths` | `playerdeaths`, `pd` | Times killed by other players | Number (e.g., `15 deaths`) |
| `mobs` | `mobkills`, `mk` | Hostile mob kills | Number (e.g., `128 mob kills`) |
| `time` | `timeplayed`, `playtime`, `tp` | Time spent playing | Formatted (e.g., `2h 30m`) |
| `kd` | `kdratio`, `ratio` | Kill/Death ratio | Decimal (e.g., `2.50`) |

---

## Color Codes

Custom headers support Minecraft color codes using the `&` symbol:

### Colors
| Code | Color |
|------|-------|
| `&0` | Black |
| `&1` | Dark Blue |
| `&2` | Dark Green |
| `&3` | Dark Aqua |
| `&4` | Dark Red |
| `&5` | Dark Purple |
| `&6` | Gold |
| `&7` | Gray |
| `&8` | Dark Gray |
| `&9` | Blue |
| `&a` | Green |
| `&b` | Aqua |
| `&c` | Red |
| `&d` | Light Purple |
| `&e` | Yellow |
| `&f` | White |

### Formatting
| Code | Style |
|------|-------|
| `&l` | **Bold** |
| `&o` | *Italic* |
| `&n` | Underline |
| `&m` | ~~Strikethrough~~ |
| `&k` | Obfuscated |
| `&r` | Reset |

**Examples:**
```
&6&lGolden Bold Header
&c&l☠ &f&lDeath Board &c&l☠
&a✦ &e&lTop Players &a✦
```

---

## Medal Colors

The top 3 players on each leaderboard are highlighted with special colors:

| Position | Color | Hex Code |
|----------|-------|----------|
| 🥇 1st Place | Gold | `#FFD700` |
| 🥈 2nd Place | Silver | `#C0C0C0` |
| 🥉 3rd Place | Bronze | `#CD7F32` |

---

## Configuration

The mod creates a configuration file at:
```
<world>/serverconfig/better_leaderboards-common.toml
```

---

## Data Storage

All player statistics and leaderboard configurations are automatically saved:

- **Player Stats**: Kills, deaths, mob kills, time played, K/D ratio
- **Leaderboard Data**: Position, dimension, stat type, header, display count

Data is stored per-world and will persist across server restarts.

---

## Technical Details

- **Refresh Rate**: Leaderboards update every 5 seconds (100 ticks)
- **Time Tracking**: Player time is tracked every 60 seconds to minimize server load
- **Actionbar Updates**: Position indicators update every second when near a leaderboard
- **Render Distance**: Leaderboard actionbar shows within 32 blocks

---

## Permissions

| Command | Required Permission |
|---------|---------------------|
| `/bl lb create` | Operator Level 2 |
| `/bl lb delete` | Operator Level 2 |
| `/bl lb moveHere` | Operator Level 2 |
| `/bl lb moveTo` | Operator Level 2 |
| `/bl lb refresh` | Operator Level 2 |
| `/bl lb list` | Operator Level 2 |
| `/bl rank` | None (all players) |

---

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for Minecraft 1.20.1
2. Download the `better_leaderboards-1.0-SNAPSHOT.jar` file
3. Place the JAR file in your `mods` folder
4. Start your Minecraft server or client

---

## Troubleshooting

### Leaderboard not appearing
- Ensure you have operator permissions
- Check if you're in the correct dimension
- Use `/bl lb list` to verify the leaderboard exists

### Statistics not updating
- Statistics update every 5 seconds automatically
- Use `/bl lb refresh` to force an immediate update

### Data not saving
- Ensure the server shuts down properly
- Check server logs for any save errors

---

## License

All Rights Reserved

---

## Author

Created by Austizz

