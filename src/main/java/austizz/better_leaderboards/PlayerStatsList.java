package austizz.better_leaderboards;

public class PlayerStatsList {
    int playerKills;
    int playerDeaths;
    int mobKills;
    int timePlayed; // Stored in seconds

    // Getters
    public int getPlayerKills() {
        return playerKills;
    }

    public int getPlayerDeaths() {
        return playerDeaths;
    }

    public int getMobKills() {
        return mobKills;
    }

    public int getTimePlayed() {
        return timePlayed;
    }

    /**
     * Calculate K/D ratio as an integer (multiplied by 100 for precision).
     * e.g., 2.5 K/D = 250
     */
    public int getKdRatio() {
        if (playerDeaths == 0) {
            return playerKills * 100; // If no deaths, K/D equals kills * 100
        }
        return (playerKills * 100) / playerDeaths;
    }

    /**
     * Get K/D ratio as a formatted string (e.g., "2.50")
     */
    public String getKdRatioFormatted() {
        int ratio = getKdRatio();
        return String.format("%.2f", ratio / 100.0);
    }

    public void addPlayerKill() {
        playerKills++;
        DataHandler.markDirty();
    }

    public void addPlayerDeath() {
        playerDeaths++;
        DataHandler.markDirty();
    }

    public void addMobKill() {
        mobKills++;
        DataHandler.markDirty();
    }

    /**
     * Add time played in seconds. Call this periodically (e.g., every minute)
     * to reduce server load instead of every tick.
     */
    public void addTimePlayed(int seconds) {
        timePlayed += seconds;
        DataHandler.markDirty();
    }

    public void setPlayerKills(int playerKills) {
        this.playerKills = playerKills;
        DataHandler.markDirty();
    }

    public void setPlayerDeaths(int playerDeaths) {
        this.playerDeaths = playerDeaths;
        DataHandler.markDirty();
    }

    public void setMobKills(int mobKills) {
        this.mobKills = mobKills;
        DataHandler.markDirty();
    }

    public void setTimePlayed(int timePlayed) {
        this.timePlayed = timePlayed;
        DataHandler.markDirty();
    }

    /**
     * Format time played as a readable string (e.g., "2h 30m" or "45m")
     */
    public String getTimePlayedFormatted() {
        int hours = timePlayed / 3600;
        int minutes = (timePlayed % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
}
