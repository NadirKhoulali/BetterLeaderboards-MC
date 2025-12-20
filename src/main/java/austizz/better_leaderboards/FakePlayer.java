package austizz.better_leaderboards;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.*;

/**
 * Manages packet-based fake players for displaying player statues.
 * These are not real entities - they exist only as packets sent to clients.
 */
public class FakePlayer {

    // Store active fake players by leaderboard ID
    private static final Map<String, FakePlayerData> activeFakePlayers = new HashMap<>();

    // Use entity IDs starting from a high number but not near MAX_VALUE
    private static int nextEntityId = 1000000000;

    public static class FakePlayerData {
        public final UUID originalUuid;  // The real player's UUID (for skin lookup)
        public final UUID fakeUuid;      // A unique UUID for this fake entity
        public final GameProfile originalProfile; // Original profile with skin data
        public final GameProfile fakeProfile;     // Fake profile for the entity
        public final String leaderboardId;
        public double x, y, z;
        public float yaw;
        public ServerPlayer fakeEntity;
        public int entityId;

        public FakePlayerData(UUID originalUuid, GameProfile originalProfile, String leaderboardId, double x, double y, double z, float yaw) {
            this.originalUuid = originalUuid;
            // Generate a unique UUID for the fake entity based on leaderboard ID
            this.fakeUuid = UUID.nameUUIDFromBytes(("bl_fake_" + leaderboardId).getBytes());
            this.originalProfile = originalProfile;
            // Create fake profile with unique UUID but same name and skin properties
            this.fakeProfile = new GameProfile(this.fakeUuid, originalProfile.getName());
            this.fakeProfile.getProperties().putAll(originalProfile.getProperties());

            // Debug: Check if texture properties were copied
            boolean hasTextures = this.fakeProfile.getProperties().containsKey("textures")
                && !this.fakeProfile.getProperties().get("textures").isEmpty();
            System.out.println("[BetterLeaderboards] FakeProfile created for " + originalProfile.getName()
                + ", hasTextures: " + hasTextures
                + ", fakeUUID: " + this.fakeUuid);

            this.leaderboardId = leaderboardId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.entityId = nextEntityId--;
        }
    }

    /**
     * Spawn or update a fake player for a leaderboard
     */
    public static void spawnOrUpdate(MinecraftServer server, String leaderboardId, UUID playerUuid,
                                      String visibleName, double x, double y, double z, float yaw) {
        // Get game profile with skin data
        GameProfile profile = getGameProfile(server, playerUuid);
        if (profile == null) {
            System.out.println("[BetterLeaderboards] Could not get game profile for " + playerUuid);
            return;
        }

        FakePlayerData existing = activeFakePlayers.get(leaderboardId);

        // If we have an existing fake player for a different UUID, remove it first
        if (existing != null && !existing.originalUuid.equals(playerUuid)) {
            despawnForAll(server, existing);
            activeFakePlayers.remove(leaderboardId);
            existing = null;
        }

        if (existing == null) {
            // Create new fake player data
            FakePlayerData data = new FakePlayerData(playerUuid, profile, leaderboardId, x, y, z, yaw);

            // Create the fake ServerPlayer entity using the fake profile
            ServerLevel level = server.overworld();
            data.fakeEntity = new ServerPlayer(server, level, data.fakeProfile);

            // IMPORTANT: Set entity ID immediately after creation
            data.fakeEntity.setId(data.entityId);

            // Hide the player name tag
            data.fakeEntity.setCustomNameVisible(false);

            // Set position and rotation
            data.fakeEntity.setPosRaw(x, y, z);
            data.fakeEntity.setPos(x, y, z);
            data.fakeEntity.setYRot(yaw);
            data.fakeEntity.setYHeadRot(yaw);
            data.fakeEntity.setYBodyRot(yaw);
            data.fakeEntity.xo = x;
            data.fakeEntity.yo = y;
            data.fakeEntity.zo = z;

            activeFakePlayers.put(leaderboardId, data);

            // Send spawn packets to all online players
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                sendSpawnPackets(viewer, data);
            }

            System.out.println("[BetterLeaderboards] Spawned fake player for " + profile.getName() + " at leaderboard: " + leaderboardId);
        } else {
            // Update position if changed
            boolean posChanged = existing.x != x || existing.y != y || existing.z != z;
            existing.x = x;
            existing.y = y;
            existing.z = z;
            existing.yaw = yaw;

            if (existing.fakeEntity != null) {
                existing.fakeEntity.setPos(x, y, z);
                existing.fakeEntity.setYRot(yaw);
                existing.fakeEntity.setYHeadRot(yaw);
                existing.fakeEntity.setYBodyRot(yaw);
            }

            if (posChanged && existing.fakeEntity != null) {
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    sendPositionPacket(viewer, existing);
                }
            }
        }
    }

    /**
     * Remove a fake player for a leaderboard
     */
    public static void despawn(MinecraftServer server, String leaderboardId) {
        FakePlayerData data = activeFakePlayers.remove(leaderboardId);
        if (data != null && server != null) {
            despawnForAll(server, data);
            System.out.println("[BetterLeaderboards] Despawned fake player for leaderboard: " + leaderboardId);
        }
    }

    /**
     * Send fake player packets to a newly joined player
     */
    public static void sendToPlayer(ServerPlayer player) {
        for (FakePlayerData data : activeFakePlayers.values()) {
            sendSpawnPackets(player, data);
        }
    }

    /**
     * Clear all fake players (called on server stop)
     */
    public static void clear() {
        activeFakePlayers.clear();
        nextEntityId = 1000000000;
    }

    private static void sendSpawnPackets(ServerPlayer viewer, FakePlayerData data) {
        if (data.fakeEntity == null) return;

        try {
            // 1. Add player to player info first (this must come BEFORE spawn packet for skin to work)
            ClientboundPlayerInfoUpdatePacket playerInfoPacket =
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(data.fakeEntity));
            viewer.connection.send(playerInfoPacket);

            // 2. Spawn the player entity (must come after player info so client has skin data)
            ClientboundAddPlayerPacket spawnPacket = new ClientboundAddPlayerPacket(data.fakeEntity);
            viewer.connection.send(spawnPacket);

            // 3. Set head rotation
            ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
                data.fakeEntity,
                (byte) (data.yaw * 256.0F / 360.0F)
            );
            viewer.connection.send(headPacket);

            // 4. Send entity metadata (skin layer visibility, etc.)
            List<SynchedEntityData.DataValue<?>> entityData = data.fakeEntity.getEntityData().getNonDefaultValues();
            if (entityData != null && !entityData.isEmpty()) {
                ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(
                    data.entityId,
                    entityData
                );
                viewer.connection.send(metadataPacket);
            }

            // 5. Create team to hide name tag AFTER entity is spawned
            String teamName = "bl_npc_" + Math.abs(data.leaderboardId.hashCode() % 100000);
            Scoreboard scoreboard = viewer.server.getScoreboard();

            // Get or create team on the actual scoreboard
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                team.setNameTagVisibility(Team.Visibility.NEVER);
            }

            // Add the fake player to the team
            scoreboard.addPlayerToTeam(data.fakeProfile.getName(), team);

            // Send the team packets to the viewer
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, data.fakeProfile.getName(), ClientboundSetPlayerTeamPacket.Action.ADD));

            // 6. Remove from tab list after a short delay (so skin loads properly)
            // Schedule on main thread with a small delay
            final UUID fakeUuid = data.fakeUuid;
            viewer.server.tell(new net.minecraft.server.TickTask(viewer.server.getTickCount() + 5, () -> {
                if (viewer.connection.isAcceptingMessages()) {
                    viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(fakeUuid)));
                }
            }));

        } catch (Exception e) {
            System.err.println("[BetterLeaderboards] Error spawning fake player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendPositionPacket(ServerPlayer viewer, FakePlayerData data) {
        if (data.fakeEntity == null) return;

        try {
            // Send teleport packet
            ClientboundTeleportEntityPacket teleportPacket = new ClientboundTeleportEntityPacket(data.fakeEntity);
            viewer.connection.send(teleportPacket);
        } catch (Exception e) {
            System.err.println("[BetterLeaderboards] Error updating fake player position: " + e.getMessage());
        }
    }

    private static void despawnForAll(MinecraftServer server, FakePlayerData data) {
        // Remove entity packet
        ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(data.entityId);

        // Clean up team from scoreboard
        String teamName = "bl_npc_" + Math.abs(data.leaderboardId.hashCode() % 100000);
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            // Send remove entity packet
            viewer.connection.send(removePacket);

            // Send remove from player info (in case still present)
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(data.fakeUuid)));

            // Send team removal packet
            if (team != null) {
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
            }
        }

        // Remove team from scoreboard
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    private static GameProfile getGameProfile(MinecraftServer server, UUID uuid) {
        // Try online player first - they always have full skin data
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            GameProfile profile = online.getGameProfile();
            // Check if it has texture properties
            if (profile.getProperties().containsKey("textures") && !profile.getProperties().get("textures").isEmpty()) {
                return profile;
            }
        }

        // Try to get profile with skin from the session service
        try {
            GameProfile profile = null;

            // First get the basic profile from cache
            if (server.getProfileCache() != null) {
                Optional<GameProfile> cached = server.getProfileCache().get(uuid);
                if (cached.isPresent()) {
                    profile = cached.get();
                }
            }

            // If we don't have a profile yet, create a basic one
            if (profile == null && online != null) {
                profile = online.getGameProfile();
            }

            if (profile == null) {
                return null;
            }

            // Check if profile already has textures
            if (profile.getProperties().containsKey("textures") && !profile.getProperties().get("textures").isEmpty()) {
                return profile;
            }

            // Fill in the profile properties (including skin) from Mojang's session service
            GameProfile filledProfile = server.getSessionService().fillProfileProperties(profile, true);
            if (filledProfile != null) {
                return filledProfile;
            }

            // Return the original profile even without skin
            return profile;

        } catch (Exception e) {
            System.err.println("[BetterLeaderboards] Error fetching game profile: " + e.getMessage());
        }

        return null;
    }
}

