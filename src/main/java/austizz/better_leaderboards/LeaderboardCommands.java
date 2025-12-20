package austizz.better_leaderboards;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Better_leaderboards.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LeaderboardCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("bl");

        var create = Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "kills", 10, false, null))
                        .then(Commands.argument("statType", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String name : StatType.getNames()) {
                                        builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "statType"), 10, false, null))
                                .then(Commands.argument("topN", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "statType"), IntegerArgumentType.getInteger(ctx, "topN"), false, null))
                                        .then(Commands.argument("npcEnabled", BoolArgumentType.bool())
                                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "statType"), IntegerArgumentType.getInteger(ctx, "topN"), BoolArgumentType.getBool(ctx, "npcEnabled"), null))
                                                .then(Commands.argument("header", StringArgumentType.greedyString())
                                                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "statType"), IntegerArgumentType.getInteger(ctx, "topN"), BoolArgumentType.getBool(ctx, "npcEnabled"), StringArgumentType.getString(ctx, "header"))))))));

        var delete = Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));

        var refresh = Commands.literal("refresh")
                .executes(ctx -> refresh(ctx.getSource(), null))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> refresh(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));

        var list = Commands.literal("list")
                .executes(ctx -> list(ctx.getSource()));

        var moveHere = Commands.literal("moveHere")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> moveHere(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));

        var moveTo = Commands.literal("moveTo")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> moveTo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                        DoubleArgumentType.getDouble(ctx, "z")
                                                ))))));

        var lb = Commands.literal("lb")
                .requires(src -> src.hasPermission(2))
                .then(create)
                .then(delete)
                .then(refresh)
                .then(list)
                .then(moveHere)
                .then(moveTo);

        root.then(lb);

        root.then(Commands.literal("rank")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> rank(ctx.getSource(), "kills"))
                .then(Commands.argument("statType", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String name : StatType.getNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> rank(ctx.getSource(), StringArgumentType.getString(ctx, "statType")))));

        dispatcher.register(root);
    }

    private static int create(CommandSourceStack source, String idRaw, String statTypeStr, int topN, boolean npcEnabled, String header) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        String id = LeaderboardManager.normalizeId(idRaw);
        StatType statType = StatType.fromString(statTypeStr);
        boolean ok = LeaderboardManager.createLeaderboard(player, id, statType, topN, npcEnabled, header);
        if (!ok) {
            source.sendFailure(Component.literal("Could not create leaderboard (already exists?)"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Created leaderboard '" + id + "'"), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String idRaw) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        String id = LeaderboardManager.normalizeId(idRaw);
        boolean ok = LeaderboardManager.deleteLeaderboard(player, id);
        if (!ok) {
            source.sendFailure(Component.literal("Could not delete leaderboard (not found, wrong dimension, or too far away)"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Deleted leaderboard '" + id + "'"), true);
        return 1;
    }

    private static int moveHere(CommandSourceStack source, String idRaw) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        String id = LeaderboardManager.normalizeId(idRaw);
        double x = player.getX();
        double y = player.getY() + 2.5D;
        double z = player.getZ();

        boolean ok = LeaderboardManager.moveLeaderboard(player, id, x, y, z);
        if (!ok) {
            source.sendFailure(Component.literal("Could not move leaderboard (not found, wrong dimension, or too far from current placement)"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Moved leaderboard '" + id + "'"), true);
        return 1;
    }

    private static int moveTo(CommandSourceStack source, String idRaw, double x, double y, double z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        String id = LeaderboardManager.normalizeId(idRaw);
        boolean ok = LeaderboardManager.moveLeaderboard(player, id, x, y, z);
        if (!ok) {
            source.sendFailure(Component.literal("Could not move leaderboard (not found, wrong dimension, or too far from current placement)"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Moved leaderboard '" + id + "' to " + x + ", " + y + ", " + z), true);
        return 1;
    }

    private static int refresh(CommandSourceStack source, String idRaw) {
        if (source.getServer() == null) {
            return 0;
        }

        if (idRaw == null) {
            LeaderboardManager.requestRefresh(source.getServer());
            LeaderboardManager.refresh(source.getServer());
            source.sendSuccess(() -> Component.literal("Refreshed leaderboards"), true);
            return 1;
        }

        String id = LeaderboardManager.normalizeId(idRaw);
        LeaderboardData data = LeaderboardData.get(source.getServer());
        if (data.getLeaderboard(id) == null) {
            source.sendFailure(Component.literal("Leaderboard '" + id + "' not found"));
            return 0;
        }

        LeaderboardManager.requestRefresh(source.getServer());
        LeaderboardManager.refresh(source.getServer());
        source.sendSuccess(() -> Component.literal("Refreshed leaderboard '" + id + "'"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (source.getServer() == null) {
            return 0;
        }

        LeaderboardData data = LeaderboardData.get(source.getServer());
        if (data.getLeaderboards().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No leaderboards"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Leaderboards:"), false);
        data.getLeaderboards().values().forEach(cfg -> {
            String statName = cfg.statType != null ? cfg.statType.getDisplayName() : "Player Kills";
            source.sendSuccess(() -> Component.literal("- " + cfg.id + " [" + statName + "] (top " + cfg.topN + ")"), false);
        });
        return 1;
    }

    private static int rank(CommandSourceStack source, String statTypeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        StatType statType = StatType.fromString(statTypeStr);
        UUID uuid = player.getUUID();

        // Get player stats
        PlayerStatsList stats = ServerList.getPlayer(uuid);

        // Calculate rank for this specific stat type
        Integer rank = LeaderboardManager.getRankForStat(uuid, statType);
        String value = statType.getFormattedValue(stats);
        String suffix = statType.getSuffix();
        String statDisplay = value + (suffix.isEmpty() ? "" : " " + suffix);

        if (rank == null) {
            player.sendSystemMessage(Component.literal("Your " + statType.getDisplayName() + " rank: Unranked (" + statDisplay + ")"));
        } else {
            player.sendSystemMessage(Component.literal("Your " + statType.getDisplayName() + " rank: #" + rank + " (" + statDisplay + ")"));
        }
        return 1;
    }
}
