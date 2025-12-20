package austizz.better_leaderboards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;

public class DataHandler extends SavedData {

    private static DataHandler INSTANCE;
    private static MinecraftServer serverInstance;

    private static final String DATA_NAME = "BetterLeaderboards_PlayerStats";
    private static final String PLAYERS_KEY = "players";
    private static final String UUID_KEY = "uuid";
    private static final String PLAYER_KILLS_KEY = "playerKills";
    private static final String PLAYER_DEATHS_KEY = "playerDeaths";
    private static final String MOB_KILLS_KEY = "mobKills";
    private static final String TIME_PLAYED_KEY = "timePlayed";

    public DataHandler() {
        INSTANCE = this;
        if (ServerList.list == null) {
            new ServerList();
        }
    }

    public static DataHandler create() {
        return new DataHandler();
    }

    public static DataHandler load(CompoundTag tag) {
        DataHandler data = new DataHandler();

        // Ensure ServerList is initialized
        if (ServerList.list == null) {
            new ServerList();
        } else {
            ServerList.list.clear();
        }

        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        System.out.println("[BetterLeaderboards] Loading " + players.size() + " player stats from world data");

        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID(UUID_KEY)) {
                continue;
            }
            UUID uuid = playerTag.getUUID(UUID_KEY);

            PlayerStatsList killList = new PlayerStatsList();
            killList.playerKills = playerTag.getInt(PLAYER_KILLS_KEY);
            killList.playerDeaths = playerTag.getInt(PLAYER_DEATHS_KEY);
            killList.mobKills = playerTag.getInt(MOB_KILLS_KEY);
            killList.timePlayed = playerTag.getInt(TIME_PLAYED_KEY);

            ServerList.list.put(uuid, killList);
            System.out.println("[BetterLeaderboards] Loaded stats for " + uuid + ": kills=" + killList.playerKills + ", deaths=" + killList.playerDeaths);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag) {
        ListTag players = new ListTag();

        if (ServerList.list != null) {
            System.out.println("[BetterLeaderboards] Saving " + ServerList.list.size() + " player stats to world data");

            for (Map.Entry<UUID, PlayerStatsList> entry : ServerList.list.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(UUID_KEY, entry.getKey());

                PlayerStatsList killList = entry.getValue();
                if (killList != null) {
                    playerTag.putInt(PLAYER_KILLS_KEY, killList.playerKills);
                    playerTag.putInt(PLAYER_DEATHS_KEY, killList.playerDeaths);
                    playerTag.putInt(MOB_KILLS_KEY, killList.mobKills);
                    playerTag.putInt(TIME_PLAYED_KEY, killList.timePlayed);
                    System.out.println("[BetterLeaderboards] Saving stats for " + entry.getKey() + ": kills=" + killList.playerKills);
                }

                players.add(playerTag);
            }
        }

        pCompoundTag.put(PLAYERS_KEY, players);
        return pCompoundTag;
    }


// In some method within the class
    public static void generateFile(MinecraftServer server) {
        serverInstance = server;
        get(server);
    }

    public static DataHandler get(MinecraftServer server) {
        serverInstance = server;
        DataHandler data = server.overworld().getDataStorage().computeIfAbsent(DataHandler::load, DataHandler::create, DATA_NAME);
        INSTANCE = data;
        return data;
    }

    public static void markDirty(MinecraftServer server) {
        serverInstance = server;
        DataHandler data = get(server);
        data.setDirty();
    }

    public static void markDirty() {
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        } else {
            // Try to get the server from ServerLifecycleHooks if instance is null
            MinecraftServer server = serverInstance;
            if (server == null) {
                server = ServerLifecycleHooks.getCurrentServer();
            }
            if (server != null) {
                DataHandler data = get(server);
                data.setDirty();
            }
        }
    }

    /**
     * Clear the instance - called when switching worlds to ensure data is per-world
     */
    public static void clear() {
        INSTANCE = null;
        serverInstance = null;
    }
}
