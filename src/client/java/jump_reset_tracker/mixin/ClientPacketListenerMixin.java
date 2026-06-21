package jump_reset_tracker.mixin;

import jump_reset_tracker.detection.AttemptCorrelator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side hit detection. Injects into the damage-event packet handler to
 * record the timestamp whenever the local player is damaged by another player.
 * Injected at TAIL so it runs once, on the client main thread (after the
 * handler's same-thread check), avoiding a double-fire on the network thread.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("TAIL"))
    private void jumpResetTracker$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (packet.entityId() != player.getId()) {
            return; // damage to some other entity
        }
        Level level = player.level();
        DamageSource source = packet.getSource(level);
        if (source == null) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && p != player) {
            AttemptCorrelator.get().onPlayerHit(System.currentTimeMillis());
        }
    }
}
