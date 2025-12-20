package austizz.better_leaderboards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LeaderboardData extends SavedData {

    private static final String DATA_NAME = "BetterLeaderboards_Leaderboards";
    private static final String LEADERBOARDS_KEY = "leaderboards";
    private static final String ID_KEY = "id";
    private static final String DIM_KEY = "dim";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";
    private static final String TOP_N_KEY = "topN";
    private static final String NPC_ENABLED_KEY = "npcEnabled";
    private static final String LAST_TOP_1_KEY = "lastTop1";

    private static final String LAST_RANKS_KEY = "lastRanks";
    private static final String UUID_KEY = "uuid";
    private static final String RANK_KEY = "rank";
    private static final String HEADER_KEY = "header";
    private static final String STAT_TYPE_KEY = "statType";

    public static class LeaderboardConfig {
        public final String id;
        public String dimension;
        public double x;
        public double y;
        public double z;
        public int topN;
        public boolean npcEnabled;
        public UUID lastTop1;
        public String header;
        public StatType statType = StatType.PLAYER_KILLS; // Default to player kills

        public LeaderboardConfig(String id) {
            this.id = id;
        }
    }

    private final Map<String, LeaderboardConfig> leaderboards = new HashMap<>();
    private final Map<UUID, Integer> lastRanks = new HashMap<>();

    public static LeaderboardData create() {
        return new LeaderboardData();
    }

    public static LeaderboardData load(CompoundTag tag) {
        LeaderboardData data = new LeaderboardData();

        ListTag lbs = tag.getList(LEADERBOARDS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < lbs.size(); i++) {
            CompoundTag lbTag = lbs.getCompound(i);
            if (!lbTag.contains(ID_KEY, Tag.TAG_STRING)) {
                continue;
            }

            String id = lbTag.getString(ID_KEY);
            LeaderboardConfig cfg = new LeaderboardConfig(id);
            cfg.dimension = lbTag.getString(DIM_KEY);
            cfg.x = lbTag.getDouble(X_KEY);
            cfg.y = lbTag.getDouble(Y_KEY);
            cfg.z = lbTag.getDouble(Z_KEY);
            cfg.topN = lbTag.getInt(TOP_N_KEY);
            cfg.npcEnabled = lbTag.getBoolean(NPC_ENABLED_KEY);
            if (lbTag.hasUUID(LAST_TOP_1_KEY)) {
                cfg.lastTop1 = lbTag.getUUID(LAST_TOP_1_KEY);
            }
            if (lbTag.contains(HEADER_KEY, Tag.TAG_STRING)) {
                cfg.header = lbTag.getString(HEADER_KEY);
            }
            if (lbTag.contains(STAT_TYPE_KEY, Tag.TAG_STRING)) {
                cfg.statType = StatType.fromString(lbTag.getString(STAT_TYPE_KEY));
            }

            data.leaderboards.put(id, cfg);
        }

        ListTag ranks = tag.getList(LAST_RANKS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < ranks.size(); i++) {
            CompoundTag rTag = ranks.getCompound(i);
            if (!rTag.hasUUID(UUID_KEY)) {
                continue;
            }
            UUID uuid = rTag.getUUID(UUID_KEY);
            int rank = rTag.getInt(RANK_KEY);
            data.lastRanks.put(uuid, rank);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag lbs = new ListTag();
        for (LeaderboardConfig cfg : leaderboards.values()) {
            CompoundTag lbTag = new CompoundTag();
            lbTag.putString(ID_KEY, cfg.id);
            lbTag.putString(DIM_KEY, cfg.dimension);
            lbTag.putDouble(X_KEY, cfg.x);
            lbTag.putDouble(Y_KEY, cfg.y);
            lbTag.putDouble(Z_KEY, cfg.z);
            lbTag.putInt(TOP_N_KEY, cfg.topN);
            lbTag.putBoolean(NPC_ENABLED_KEY, cfg.npcEnabled);
            if (cfg.lastTop1 != null) {
                lbTag.putUUID(LAST_TOP_1_KEY, cfg.lastTop1);
            }
            if (cfg.header != null && !cfg.header.isEmpty()) {
                lbTag.putString(HEADER_KEY, cfg.header);
            }
            if (cfg.statType != null) {
                lbTag.putString(STAT_TYPE_KEY, cfg.statType.name());
            }
            lbs.add(lbTag);
        }
        tag.put(LEADERBOARDS_KEY, lbs);

        ListTag ranks = new ListTag();
        for (Map.Entry<UUID, Integer> e : lastRanks.entrySet()) {
            CompoundTag rTag = new CompoundTag();
            rTag.putUUID(UUID_KEY, e.getKey());
            rTag.putInt(RANK_KEY, e.getValue());
            ranks.add(rTag);
        }
        tag.put(LAST_RANKS_KEY, ranks);

        return tag;
    }

    public static LeaderboardData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LeaderboardData::load, LeaderboardData::create, DATA_NAME);
    }

    public Map<String, LeaderboardConfig> getLeaderboards() {
        return leaderboards;
    }

    public LeaderboardConfig getLeaderboard(String id) {
        return leaderboards.get(id);
    }

    public Map<UUID, Integer> getLastRanks() {
        return lastRanks;
    }

    public Integer getLastRank(UUID uuid) {
        return lastRanks.get(uuid);
    }

    public void upsertLeaderboard(LeaderboardConfig cfg) {
        leaderboards.put(cfg.id, cfg);
        setDirty();
    }

    public void removeLeaderboard(String id) {
        leaderboards.remove(id);
        setDirty();
    }

    public void setLastRank(UUID uuid, int rank) {
        lastRanks.put(uuid, rank);
        setDirty();
    }

    public void updateLastRanks(Map<UUID, Integer> ranks) {
        if (lastRanks.equals(ranks)) {
            return;
        }
        lastRanks.clear();
        lastRanks.putAll(ranks);
        setDirty();
    }
}
