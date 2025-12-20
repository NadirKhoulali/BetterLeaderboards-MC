package austizz.better_leaderboards;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Better_leaderboards.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LeaderboardManager {

    private static final String TAG_LB = "bl_lb";
    private static final String TAG_LINE_PREFIX = "bl_line_";
    private static final String TAG_LB_PREFIX = "bl_lb_";
    private static final String TAG_STATUE = "bl_statue";

    private static final double LINE_SPACING = 0.25D;
    private static final double ACTIONBAR_RADIUS = 32.0D;
    private static final int REFRESH_PERIOD_TICKS = 100; // Refresh leaderboards every 5 seconds (100 ticks)
    private static final int ACTIONBAR_PERIOD_TICKS = 20; // Update actionbars every second
    private static final int TIME_TRACK_PERIOD_TICKS = 1200; // Update time played every minute (60 seconds * 20 ticks)

    // Hex colors for top 3 positions
    private static final int COLOR_GOLD = 0xFFD700;    // Gold for 1st place
    private static final int COLOR_SILVER = 0xC0C0C0;  // Silver for 2nd place
    private static final int COLOR_BRONZE = 0xCD7F32;  // Bronze for 3rd place

    private static boolean refreshRequested;
    private static int tickCounter;
    private static int timeTrackCounter;

    private static Map<UUID, Integer> cachedRanks = new HashMap<>();
    private static Map<UUID, Integer> cachedKills = new HashMap<>();

    // Delay initial refresh to allow entities to load (2 seconds = 40 ticks)
    private static final int INITIAL_DELAY_TICKS = 40;
    private static boolean initialDelayComplete = false;

    public static void requestRefresh(MinecraftServer server) {
        refreshRequested = true;
    }

    public static void reset() {
        tickCounter = 0;
        timeTrackCounter = 0;
        initialDelayComplete = false;
        refreshRequested = false;
        cachedRanks.clear();
        cachedKills.clear();
        FakePlayer.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        tickCounter++;
        timeTrackCounter++;

        // Wait for initial delay before processing refreshes
        if (!initialDelayComplete) {
            if (tickCounter >= INITIAL_DELAY_TICKS) {
                initialDelayComplete = true;
            } else {
                return;
            }
        }

        // Track time played for all online players (every minute to reduce lag)
        if (timeTrackCounter >= TIME_TRACK_PERIOD_TICKS) {
            timeTrackCounter = 0;
            trackTimePlayed(server);
        }

        // Refresh leaderboards periodically or when requested
        if (tickCounter % REFRESH_PERIOD_TICKS == 0 || refreshRequested) {
            refreshRequested = false;
            refresh(server);
        }

        // Update actionbars more frequently
        if (tickCounter % ACTIONBAR_PERIOD_TICKS == 0) {
            tickActionbars(server);
        }
    }

    private static void trackTimePlayed(MinecraftServer server) {
        if (server == null || ServerList.list == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            ServerList.addPlayer(uuid); // Ensure player is in the list
            PlayerStatsList stats = ServerList.getPlayer(uuid);
            if (stats != null) {
                stats.addTimePlayed(60); // Add 60 seconds (1 minute)
            }
        }
    }

    public static void refresh(MinecraftServer server) {
        if (server == null) {
            return;
        }

        if (ServerList.list == null) {
            new ServerList();
        }

        LeaderboardData data = LeaderboardData.get(server);

        List<Map.Entry<UUID, PlayerStatsList>> entries = new ArrayList<>(ServerList.list.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<UUID, PlayerStatsList> e) -> e.getValue() == null ? 0 : e.getValue().playerKills).reversed());

        Map<UUID, Integer> newRanks = new HashMap<>();
        Map<UUID, Integer> newKills = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            UUID uuid = entries.get(i).getKey();
            PlayerStatsList stats = entries.get(i).getValue();
            int kills = stats == null ? 0 : stats.playerKills;
            newRanks.put(uuid, i + 1);
            newKills.put(uuid, kills);
        }

        notifyRankChanges(server, data, newRanks, newKills);
        data.updateLastRanks(newRanks);
        cachedRanks = newRanks;
        cachedKills = newKills;

        for (LeaderboardData.LeaderboardConfig cfg : data.getLeaderboards().values()) {
            ServerLevel level = resolveLevel(server, cfg.dimension);
            if (level == null) {
                continue;
            }
            updateLeaderboard(level, server, cfg, entries);
        }
    }

    private static void notifyRankChanges(MinecraftServer server, LeaderboardData data, Map<UUID, Integer> newRanks, Map<UUID, Integer> kills) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            Integer newRank = newRanks.get(uuid);
            Integer prevRank = data.getLastRank(uuid);
            if (newRank == null) {
                continue;
            }

            if ((prevRank == null || prevRank > 10) && newRank <= 10) {
                int k = kills.getOrDefault(uuid, 0);
                player.sendSystemMessage(Component.literal("You are now in the Top 10! Position: #" + newRank + " with " + k + " kills"));
            }

            if ((prevRank == null || prevRank != 1) && newRank == 1) {
                int k = kills.getOrDefault(uuid, 0);
                player.sendSystemMessage(Component.literal("Congratulations! You are now #1 with " + k + " kills"));
            }
        }
    }

    private static void tickActionbars(MinecraftServer server) {
        if (server == null) {
            return;
        }

        LeaderboardData data = LeaderboardData.get(server);
        if (data.getLeaderboards().isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LeaderboardData.LeaderboardConfig nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (LeaderboardData.LeaderboardConfig cfg : data.getLeaderboards().values()) {
                ServerLevel level = resolveLevel(server, cfg.dimension);
                if (level == null || player.level() != level) {
                    continue;
                }

                double dx = player.getX() - cfg.x;
                double dy = player.getY() - cfg.y;
                double dz = player.getZ() - cfg.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= ACTIONBAR_RADIUS * ACTIONBAR_RADIUS && distSq < nearestDist) {
                    nearestDist = distSq;
                    nearest = cfg;
                }
            }

            if (nearest == null) {
                continue;
            }

            UUID uuid = player.getUUID();
            PlayerStatsList stats = ServerList.getPlayer(uuid);
            StatType statType = nearest.statType != null ? nearest.statType : StatType.PLAYER_KILLS;

            // Calculate rank for this specific stat type
            List<Map.Entry<UUID, PlayerStatsList>> entries = new ArrayList<>(ServerList.list.entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<UUID, PlayerStatsList> e) -> statType.getValue(e.getValue())).reversed());

            Integer rank = null;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(uuid)) {
                    rank = i + 1;
                    break;
                }
            }

            String value = statType.getFormattedValue(stats);
            String suffix = statType.getSuffix();
            String statDisplay = value + (suffix.isEmpty() ? "" : " " + suffix);

            if (rank == null) {
                player.displayClientMessage(Component.literal("Your rank: Unranked (" + statDisplay + ")"), true);
            } else {
                player.displayClientMessage(Component.literal("Your rank: #" + rank + " (" + statDisplay + ")"), true);
            }
        }
    }

    private static void updateLeaderboard(ServerLevel level, MinecraftServer server, LeaderboardData.LeaderboardConfig cfg, List<Map.Entry<UUID, PlayerStatsList>> allEntries) {
        int topN = cfg.topN <= 0 ? 10 : cfg.topN;
        StatType statType = cfg.statType != null ? cfg.statType : StatType.PLAYER_KILLS;

        // Sort entries based on the leaderboard's stat type
        List<Map.Entry<UUID, PlayerStatsList>> sorted = new ArrayList<>(allEntries);
        sorted.sort(Comparator.comparingInt((Map.Entry<UUID, PlayerStatsList> e) -> statType.getValue(e.getValue())).reversed());

        List<Component> lines = new ArrayList<>();
        // Use custom header if provided, otherwise use default based on stat type
        String headerText = (cfg.header != null && !cfg.header.isEmpty()) ? cfg.header : "Top " + statType.getDisplayName();
        lines.add(parseColorCodes(headerText));
        lines.add(Component.literal(" "));

        for (int i = 0; i < topN; i++) {
            if (i >= sorted.size()) {
                break;
            }
            UUID uuid = sorted.get(i).getKey();
            PlayerStatsList stats = sorted.get(i).getValue();
            String name = resolveName(server, uuid);
            String value = statType.getFormattedValue(stats);
            String suffix = statType.getSuffix();
            String text = "#" + (i + 1) + " " + name + " - " + value + (suffix.isEmpty() ? "" : " " + suffix);

            // Apply gold, silver, bronze colors for top 3
            Component lineComponent;
            if (i == 0) {
                lineComponent = Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_GOLD)));
            } else if (i == 1) {
                lineComponent = Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_SILVER)));
            } else if (i == 2) {
                lineComponent = Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_BRONZE)));
            } else {
                lineComponent = Component.literal(text);
            }
            lines.add(lineComponent);
        }

        String lbTag = TAG_LB_PREFIX + cfg.id;
        for (int i = 0; i < lines.size(); i++) {
            double y = cfg.y - (i * LINE_SPACING);
            ArmorStand stand = findArmorStand(level, lbTag, TAG_LINE_PREFIX + i, cfg.x, cfg.y, cfg.z);
            if (stand == null) {
                stand = new ArmorStand(level, cfg.x, y, cfg.z);
                stand.addTag(TAG_LB);
                stand.addTag(lbTag);
                stand.addTag(TAG_LINE_PREFIX + i);
                stand.setNoGravity(true);
                stand.setInvulnerable(true);
                stand.setInvisible(true);
                stand.setNoBasePlate(true);
                stand.setCustomNameVisible(true);
                setMarker(stand, true);
                level.addFreshEntity(stand);
            } else {
                stand.setPos(cfg.x, y, cfg.z);
            }

            Component text = lines.get(i);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
        }

        removeExtraLines(level, lbTag, lines.size(), cfg.x, cfg.y, cfg.z);

        // Only add statue AFTER the leaderboard lines are created
        UUID top1 = sorted.isEmpty() ? null : sorted.get(0).getKey();
        if (cfg.npcEnabled && top1 != null) {
            updateStatue(level, server, cfg, top1, sorted.get(0).getValue());
            if (cfg.lastTop1 == null || !cfg.lastTop1.equals(top1)) {
                cfg.lastTop1 = top1;
                LeaderboardData.get(server).upsertLeaderboard(cfg);
            }
        } else {
            removeStatue(level, lbTag, cfg.x, cfg.y, cfg.z);
            if (cfg.lastTop1 != null) {
                cfg.lastTop1 = null;
                LeaderboardData.get(server).upsertLeaderboard(cfg);
            }
        }
    }

    private static void updateStatue(ServerLevel level, MinecraftServer server, LeaderboardData.LeaderboardConfig cfg, UUID top1, PlayerStatsList stats) {
        String lbTag = TAG_LB_PREFIX + cfg.id;

        int lines = 2 + (cfg.topN <= 0 ? 10 : cfg.topN);
        double statueY = cfg.y - (lines * LINE_SPACING) - 1.5D;

        // Get player name and stat display
        String name = resolveName(server, top1);
        StatType statType = cfg.statType != null ? cfg.statType : StatType.PLAYER_KILLS;
        String value = statType.getFormattedValue(stats);
        String suffix = statType.getSuffix();
        String statDisplay = value + (suffix.isEmpty() ? "" : " " + suffix);

        // Spawn or update the fake player (packet-based, shows full skin)
        FakePlayer.spawnOrUpdate(server, cfg.id, top1, name, cfg.x, statueY, cfg.z, 0.0F);

        // Create/update name tag armor stand above the fake player
        ArmorStand nameTag = findArmorStand(level, lbTag, TAG_STATUE, cfg.x, cfg.y, cfg.z);
        double nameTagY = statueY + 2.0D;

        if (nameTag == null) {
            nameTag = new ArmorStand(level, cfg.x, nameTagY, cfg.z);
            nameTag.addTag(TAG_LB);
            nameTag.addTag(lbTag);
            nameTag.addTag(TAG_STATUE);
            nameTag.setNoGravity(true);
            nameTag.setInvulnerable(true);
            nameTag.setInvisible(true);
            nameTag.setNoBasePlate(true);
            nameTag.setCustomNameVisible(true);
            setMarker(nameTag, true);
            level.addFreshEntity(nameTag);
        } else {
            nameTag.setPos(cfg.x, nameTagY, cfg.z);
        }

        nameTag.setCustomName(Component.literal("§6#1 " + name + " §7(" + statDisplay + ")"));
        nameTag.setCustomNameVisible(true);
    }

    private static void removeStatue(ServerLevel level, String lbTag, double x, double y, double z) {
        // Force load the chunk to ensure entities are available
        BlockPos pos = BlockPos.containing(x, y, z);
        level.getChunk(pos);

        // Remove the packet-based fake player
        FakePlayer.despawn(level.getServer(), lbTag.replace(TAG_LB_PREFIX, ""));

        AABB box = searchBox(x, y, z);

        // Remove armor stand name tags
        List<ArmorStand> statues = level.getEntitiesOfClass(ArmorStand.class, box,
            s -> s.getTags().contains(lbTag) && s.getTags().contains(TAG_STATUE));
        for (ArmorStand s : statues) {
            s.discard();
        }
    }

    private static void removeExtraLines(ServerLevel level, String lbTag, int keepLines, double x, double y, double z) {
        // Force load the chunk to ensure entities are available
        BlockPos pos = BlockPos.containing(x, y, z);
        level.getChunk(pos);

        AABB box = searchBox(x, y, z);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, s -> s.getTags().contains(lbTag) && s.getTags().contains(TAG_LB));
        for (ArmorStand s : stands) {
            for (String tag : s.getTags()) {
                if (tag.startsWith(TAG_LINE_PREFIX)) {
                    String idxStr = tag.substring(TAG_LINE_PREFIX.length());
                    try {
                        int idx = Integer.parseInt(idxStr);
                        if (idx >= keepLines) {
                            s.discard();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private static ArmorStand findArmorStand(ServerLevel level, String lbTag, String requiredTag, double x, double y, double z) {
        // Force load the chunk to ensure entities are available
        BlockPos pos = BlockPos.containing(x, y, z);
        level.getChunk(pos);

        AABB box = searchBox(x, y, z);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, s -> s.getTags().contains(lbTag) && s.getTags().contains(requiredTag));
        if (stands.isEmpty()) {
            return null;
        }
        return stands.get(0);
    }

    private static AABB searchBox(double x, double y, double z) {
        return new AABB(x - 4.0D, y - 128.0D, z - 4.0D, x + 4.0D, y + 128.0D, z + 4.0D);
    }

    public static boolean createLeaderboard(ServerPlayer player, String id, int topN) {
        return createLeaderboard(player, id, StatType.PLAYER_KILLS, topN, false, null);
    }

    public static boolean createLeaderboard(ServerPlayer player, String id, int topN, boolean npcEnabled) {
        return createLeaderboard(player, id, StatType.PLAYER_KILLS, topN, npcEnabled, null);
    }

    public static boolean createLeaderboard(ServerPlayer player, String id, int topN, boolean npcEnabled, String header) {
        return createLeaderboard(player, id, StatType.PLAYER_KILLS, topN, npcEnabled, header);
    }

    public static boolean createLeaderboard(ServerPlayer player, String id, StatType statType, int topN, boolean npcEnabled, String header) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        LeaderboardData data = LeaderboardData.get(server);
        if (data.getLeaderboard(id) != null) {
            return false;
        }

        LeaderboardData.LeaderboardConfig cfg = new LeaderboardData.LeaderboardConfig(id);
        cfg.dimension = player.level().dimension().location().toString();
        cfg.x = player.getX();
        cfg.y = player.getY() + 2.5D;
        cfg.z = player.getZ();
        cfg.topN = topN <= 0 ? 10 : topN;
        cfg.npcEnabled = npcEnabled;
        cfg.statType = statType != null ? statType : StatType.PLAYER_KILLS;
        cfg.header = header;
        data.upsertLeaderboard(cfg);

        requestRefresh(server);
        refresh(server);
        return true;
    }

    public static boolean moveLeaderboard(ServerPlayer player, String id, double x, double y, double z) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        LeaderboardData data = LeaderboardData.get(server);
        LeaderboardData.LeaderboardConfig cfg = data.getLeaderboard(id);
        if (cfg == null) {
            return false;
        }

        ServerLevel level = resolveLevel(server, cfg.dimension);
        if (level == null || player.level() != level) {
            return false;
        }

        double dx = player.getX() - cfg.x;
        double dy = player.getY() - cfg.y;
        double dz = player.getZ() - cfg.z;
        if ((dx * dx + dy * dy + dz * dz) > 64.0D * 64.0D) {
            return false;
        }

        String lbTag = TAG_LB_PREFIX + cfg.id;
        AABB box = searchBox(cfg.x, cfg.y, cfg.z);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, s -> s.getTags().contains(lbTag));
        for (ArmorStand s : stands) {
            s.discard();
        }

        cfg.dimension = player.level().dimension().location().toString();
        cfg.x = x;
        cfg.y = y;
        cfg.z = z;
        data.upsertLeaderboard(cfg);

        requestRefresh(server);
        refresh(server);
        return true;
    }

    public static boolean deleteLeaderboard(ServerPlayer player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        LeaderboardData data = LeaderboardData.get(server);
        LeaderboardData.LeaderboardConfig cfg = data.getLeaderboard(id);
        if (cfg == null) {
            return false;
        }

        ServerLevel level = resolveLevel(server, cfg.dimension);
        if (level == null || player.level() != level) {
            return false;
        }

        double dx = player.getX() - cfg.x;
        double dy = player.getY() - cfg.y;
        double dz = player.getZ() - cfg.z;
        if ((dx * dx + dy * dy + dz * dz) > 64.0D * 64.0D) {
            return false;
        }

        String lbTag = TAG_LB_PREFIX + cfg.id;
        AABB box = searchBox(cfg.x, cfg.y, cfg.z);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, s -> s.getTags().contains(lbTag));
        for (ArmorStand s : stands) {
            s.discard();
        }

        // Remove the fake player statue if it exists
        FakePlayer.despawn(server, id);

        data.removeLeaderboard(id);
        requestRefresh(server);
        return true;
    }

    public static boolean setNpcEnabled(ServerPlayer player, String id, boolean enabled) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        LeaderboardData data = LeaderboardData.get(server);
        LeaderboardData.LeaderboardConfig cfg = data.getLeaderboard(id);
        if (cfg == null) {
            return false;
        }

        cfg.npcEnabled = enabled;
        data.upsertLeaderboard(cfg);

        requestRefresh(server);
        refresh(server);
        return true;
    }

    public static Integer getRank(UUID uuid) {
        return cachedRanks.get(uuid);
    }

    public static int getKills(UUID uuid) {
        return cachedKills.getOrDefault(uuid, 0);
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dim) {
        if (server == null || dim == null) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return server.getLevel(key);
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Optional<GameProfile> cached = server.getProfileCache().get(uuid);
        if (cached.isPresent() && cached.get().getName() != null) {
            return cached.get().getName();
        }
        String s = uuid.toString();
        return s.substring(0, Math.min(8, s.length()));
    }

    private static ItemStack makePlayerHead(MinecraftServer server, UUID uuid, String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);

        GameProfile profile = null;
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            profile = online.getGameProfile();
        } else {
            Optional<GameProfile> cached = server.getProfileCache().get(uuid);
            if (cached.isPresent()) {
                profile = cached.get();
            }
        }

        if (profile == null) {
            profile = new GameProfile(uuid, name);
        }

        CompoundTag tag = head.getOrCreateTag();
        tag.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
        head.setTag(tag);
        return head;
    }

    private static void setMarker(ArmorStand stand, boolean marker) {
        CompoundTag tag = stand.saveWithoutId(new CompoundTag());
        tag.putBoolean("Marker", marker);
        stand.load(tag);
    }

    public static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public static BlockPos getLeaderboardPos(LeaderboardData.LeaderboardConfig cfg) {
        return BlockPos.containing(cfg.x, cfg.y, cfg.z);
    }

    /**
     * Parses Minecraft color codes (§ or &) and converts them to a styled Component.
     * Supports standard color codes (0-9, a-f) and formatting codes (k, l, m, n, o, r).
     * Also supports hex colors using &#RRGGBB or §#RRGGBB format.
     */
    private static Component parseColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return Component.literal("");
        }

        // Replace & with § for easier parsing (common alternative)
        text = text.replace('&', '§');

        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder currentText = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                // Flush current text with current style
                if (currentText.length() > 0) {
                    result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                    currentText = new StringBuilder();
                }

                char code = text.charAt(i + 1);

                // Check for hex color format: §#RRGGBB
                if (code == '#' && i + 8 < text.length()) {
                    String hex = text.substring(i + 2, i + 8);
                    try {
                        int color = Integer.parseInt(hex, 16);
                        currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(color));
                        i += 7; // Skip §#RRGGBB
                        continue;
                    } catch (NumberFormatException ignored) {
                        // Not a valid hex, treat as normal text
                    }
                }

                // Standard color codes
                switch (Character.toLowerCase(code)) {
                    case '0' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x000000)); // Black
                    case '1' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x0000AA)); // Dark Blue
                    case '2' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x00AA00)); // Dark Green
                    case '3' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x00AAAA)); // Dark Aqua
                    case '4' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xAA0000)); // Dark Red
                    case '5' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xAA00AA)); // Dark Purple
                    case '6' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)); // Gold
                    case '7' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)); // Gray
                    case '8' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x555555)); // Dark Gray
                    case '9' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x5555FF)); // Blue
                    case 'a' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55)); // Green
                    case 'b' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)); // Aqua
                    case 'c' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)); // Red
                    case 'd' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFF55FF)); // Light Purple
                    case 'e' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF55)); // Yellow
                    case 'f' -> currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)); // White
                    case 'k' -> currentStyle = currentStyle.withObfuscated(true);  // Obfuscated
                    case 'l' -> currentStyle = currentStyle.withBold(true);         // Bold
                    case 'm' -> currentStyle = currentStyle.withStrikethrough(true); // Strikethrough
                    case 'n' -> currentStyle = currentStyle.withUnderlined(true);   // Underline
                    case 'o' -> currentStyle = currentStyle.withItalic(true);       // Italic
                    case 'r' -> currentStyle = Style.EMPTY;                         // Reset
                    default -> {
                        // Unknown code, keep the § and the character
                        currentText.append(c).append(code);
                    }
                }
                i++; // Skip the code character
            } else {
                currentText.append(c);
            }
        }

        // Flush remaining text
        if (currentText.length() > 0) {
            result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
        }

        return result;
    }
}
