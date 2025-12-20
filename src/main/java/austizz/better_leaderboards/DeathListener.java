package austizz.better_leaderboards;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Better_leaderboards.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DeathListener {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }

        if (ServerList.list == null) {
            new ServerList();
        }

        if (victim instanceof ServerPlayer victimPlayer) {
            ServerList.addPlayer(victimPlayer.getUUID());
            PlayerStatsList victimStats = ServerList.getPlayer(victimPlayer.getUUID());
            if (victimStats != null) {
                victimStats.addPlayerDeath();
            }
        }

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer killerPlayer)) {
            return;
        }

        ServerList.addPlayer(killerPlayer.getUUID());
        PlayerStatsList killerStats = ServerList.getPlayer(killerPlayer.getUUID());
        if (killerStats == null) {
            return;
        }

        if (victim instanceof ServerPlayer victimPlayer) {
            if (!killerPlayer.getUUID().equals(victimPlayer.getUUID())) {
                killerStats.addPlayerKill();
            }
        } else {
            killerStats.addMobKill();
        }

        LeaderboardManager.requestRefresh(killerPlayer.getServer());
    }
}
