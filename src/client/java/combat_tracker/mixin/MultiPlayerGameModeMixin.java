package combat_tracker.mixin;

import combat_tracker.detection.ComboTracker;
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
        ComboTracker.get().onAttack(target);
    }
}
