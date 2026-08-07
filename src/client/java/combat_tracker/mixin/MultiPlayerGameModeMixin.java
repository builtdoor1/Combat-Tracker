package combat_tracker.mixin;

import combat_tracker.detection.ComboTracker;
import combat_tracker.detection.IntegrityMonitor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Outgoing-attack detection. {@code attack(player, target)} is called when the
 * local player left-clicks an entity within reach — i.e. a landed melee hit. We
 * forward the target to the combo tracker, which keeps only sprint hits on other
 * players.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void jumpResetTracker$onAttack(Player player, Entity target, CallbackInfo ci) {
        // Checking here as well as in startAttack closes a killaura that calls
        // mc.gameMode.attack(player, target) directly: that never touches
        // Minecraft.startAttack, so the check one layer up never runs. Vanilla only
        // reaches this from startAttack, which is itself only reached from
        // handleKeybinds, so the keybind window is open on every legitimate path.
        IntegrityMonitor.get().onAttack();
        ComboTracker.get().onAttack(target);
    }
}
