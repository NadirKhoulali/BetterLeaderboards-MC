package austizz.better_leaderboards;

import java.util.Locale;

public enum StatType {
    PLAYER_KILLS("Player Kills", "kills"),
    PLAYER_DEATHS("Player Deaths", "deaths"),
    MOB_KILLS("Mob Kills", "mob kills"),
    TIME_PLAYED("Time Played", ""),
    KD_RATIO("K/D Ratio", "");

    private final String displayName;
    private final String suffix;

    StatType(String displayName, String suffix) {
        this.displayName = displayName;
        this.suffix = suffix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSuffix() {
        return suffix;
    }

    /**
     * Get the stat value for a player
     */
    public int getValue(PlayerStatsList stats) {
        if (stats == null) return 0;
        return switch (this) {
            case PLAYER_KILLS -> stats.getPlayerKills();
            case PLAYER_DEATHS -> stats.getPlayerDeaths();
            case MOB_KILLS -> stats.getMobKills();
            case TIME_PLAYED -> stats.getTimePlayed();
            case KD_RATIO -> stats.getKdRatio();
        };
    }

    /**
     * Get the formatted display value for a player
     */
    public String getFormattedValue(PlayerStatsList stats) {
        if (stats == null) return "0";
        return switch (this) {
            case PLAYER_KILLS -> String.valueOf(stats.getPlayerKills());
            case PLAYER_DEATHS -> String.valueOf(stats.getPlayerDeaths());
            case MOB_KILLS -> String.valueOf(stats.getMobKills());
            case TIME_PLAYED -> stats.getTimePlayedFormatted();
            case KD_RATIO -> stats.getKdRatioFormatted();
        };
    }

    /**
     * Parse a stat type from a string (case-insensitive)
     */
    public static StatType fromString(String name) {
        if (name == null || name.isEmpty()) {
            return PLAYER_KILLS; // Default
        }

        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");

        return switch (normalized) {
            case "playerkills", "kills", "pk" -> PLAYER_KILLS;
            case "playerdeaths", "deaths", "pd" -> PLAYER_DEATHS;
            case "mobkills", "mobs", "mk" -> MOB_KILLS;
            case "timeplayed", "time", "playtime", "tp" -> TIME_PLAYED;
            case "kdratio", "kd", "ratio" -> KD_RATIO;
            default -> PLAYER_KILLS;
        };
    }

    /**
     * Get all valid stat type names for command suggestions
     */
    public static String[] getNames() {
        return new String[]{"kills", "deaths", "mobs", "time", "kd"};
    }
}

