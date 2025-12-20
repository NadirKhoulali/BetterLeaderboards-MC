package austizz.better_leaderboards;



import java.util.HashMap;
import java.util.UUID;

public class ServerList {
    static HashMap<UUID, PlayerStatsList> list;

    public ServerList () {
        list = new HashMap<>();
    }

    public static void addPlayer(UUID uuid) {
        list.putIfAbsent(uuid, new PlayerStatsList());
        DataHandler.markDirty();
    }

    public static void removePlayer (UUID uuid) {
        list.remove(uuid);
        DataHandler.markDirty();
    }

    public static HashMap<UUID, PlayerStatsList> getSafeList () {
        final HashMap<UUID, PlayerStatsList> finalList = new HashMap<>(list);
        return finalList;
    }

    public static PlayerStatsList getPlayer (UUID uuid) {
        return list.get(uuid);
    }

    public static boolean isAlreadyInList (UUID uuid) {

        return list.containsKey(uuid);
    }

    /**
     * Clear all data - called when switching worlds to ensure data is per-world
     */
    public static void clear() {
        if (list != null) {
            list.clear();
        }
        list = null;
    }
}
