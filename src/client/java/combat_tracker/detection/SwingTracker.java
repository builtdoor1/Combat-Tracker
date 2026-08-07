package combat_tracker.detection;

import combat_tracker.record.SessionRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Measures the geometry of each attack swing: how far away the target was
 * ("reach") and how far the crosshair sat from the middle of their hitbox ("aim").
 *
 * <p>Purely observational, like the rest of the mod — nothing here influences where
 * a swing lands. It answers the two accusations that timing statistics cannot:
 * that you hit from impossible distances, and that your crosshair snaps to hitbox
 * centre the way an aim assist would.</p>
 *
 * <p><b>These are your client's numbers.</b> Remote players are interpolated
 * locally and the server saw them somewhere slightly different, so reach measured
 * here will not match a server anti-cheat's figure exactly. The report says so.</p>
 */
public final class SwingTracker {
    private static final SwingTracker INSTANCE = new SwingTracker();

    public static SwingTracker get() {
        return INSTANCE;
    }

    /** A swing is attributed to the closest player within this range, if any. */
    private static final double CANDIDATE_RANGE = 6.0;
    /**
     * ...and only if the crosshair is within this cone of them. Beyond it you were
     * plainly not swinging at that player, and counting it would invent misses.
     */
    private static final double CANDIDATE_CONE_DEG = 30.0;

    private SwingTracker() {
    }

    /**
     * Called for every left-click attack, landed or not.
     *
     * @param hitResult what vanilla's own pick found this frame, possibly a miss
     */
    public void onSwing(Minecraft client, HitResult hitResult) {
        LocalPlayer self = client.player;
        if (self == null || client.level == null) {
            return;
        }

        // Tick-aligned eye and view, NOT interpolated to the render frame. Vanilla
        // validates reach against the tick position (Player.isWithinEntityInteraction
        // Range calls the no-arg getEyePosition), and the target's bounding box we
        // measure against is a tick position too. Mixing an interpolated eye with a
        // tick-position hitbox was silently adding up to a quarter of a block of
        // error at sprint speed.
        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getViewVector(1.0F).normalize();

        // A landed hit on a player is unambiguous; otherwise work out who the swing
        // was plainly meant for. No candidate means an air swing at nobody, which
        // carries no reach or aim information and is dropped.
        boolean landed = false;
        Player target = null;
        if (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player p && p != self) {
            target = p;
            landed = true;
        } else {
            target = findIntendedTarget(client, self, eye, look);
        }
        if (target == null) {
            return;
        }

        AABB box = target.getBoundingBox();
        Vec3 centre = box.getCenter();

        double reach = reachTo(eye, box);
        double aimDeg = angleBetween(look, centre.subtract(eye));
        double[] placement = placementOnHitbox(eye, look, box, centre);

        String targetName = target.getName().getString();
        OpponentTracker.get().note(targetName);
        SessionRecorder.get().recordSwing(
                reach, landed, aimDeg, placement[0], placement[1], targetName);
    }

    /**
     * Distance from the eye to the <em>nearest point</em> of the target's hitbox.
     *
     * <p>This is the number vanilla itself validates. {@code
     * Player.isWithinEntityInteractionRange} is exactly:</p>
     *
     * <pre>box.distanceToSqr(getEyePosition()) &lt; range * range</pre>
     *
     * <p>where {@code range} is the {@code entity_interaction_range} attribute,
     * 3.0 by default. So it is also what server anti-cheats measure and what the
     * community means by "reach".</p>
     *
     * <p>An earlier version measured to the point where the aim ray <em>crosses</em>
     * the box instead. That is a different and always-larger quantity — aim at
     * someone's head from below and the ray enters the box further away than its
     * nearest corner — which made legitimate vanilla hits report over 3.0 blocks.
     * Wolren's ReachDisplay draws the same distinction and treats the ray-crossing
     * as an alternate display mode, not as reach.</p>
     */
    private static double reachTo(Vec3 eye, AABB box) {
        return Math.sqrt(box.distanceToSqr(eye));
    }

    /**
     * Where the aim ray passes the hitbox, relative to its centre, as
     * {@code [horizontal, vertical]} in blocks. Horizontal is signed left/right
     * across the player's facing, so a consistent bias shows up as an offset
     * cluster rather than being averaged away.
     */
    private static double[] placementOnHitbox(Vec3 eye, Vec3 look, AABB box, Vec3 centre) {
        // Point on the aim ray closest to the hitbox centre; for a ray that crosses
        // the box this sits essentially at the crossing.
        Vec3 toCentre = centre.subtract(eye);
        double along = Math.max(0.0, toCentre.dot(look));
        Vec3 nearest = eye.add(look.scale(along));
        Vec3 offset = nearest.subtract(centre);

        // Split into vertical and horizontal-across-view components.
        double vertical = offset.y;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) {
            // Looking straight up or down: any horizontal axis is arbitrary.
            return new double[]{0.0, vertical};
        }
        double horizontal = offset.dot(right.normalize());
        return new double[]{horizontal, vertical};
    }

    /** Smallest angle in degrees between the look vector and the direction to a point. */
    private static double angleBetween(Vec3 look, Vec3 toTarget) {
        double len = toTarget.length();
        if (len < 1.0e-6) {
            return 0.0;
        }
        double cos = Math.max(-1.0, Math.min(1.0, look.dot(toTarget.scale(1.0 / len))));
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * The player a whiffed swing was aimed at: nearest to the crosshair by angle,
     * inside {@link #CANDIDATE_RANGE} and {@link #CANDIDATE_CONE_DEG}.
     */
    private static Player findIntendedTarget(Minecraft client, LocalPlayer self, Vec3 eye, Vec3 look) {
        Player best = null;
        double bestAngle = CANDIDATE_CONE_DEG;
        for (Player p : client.level.players()) {
            if (p == self || !p.isAlive()) {
                continue;
            }
            Vec3 toTarget = p.getBoundingBox().getCenter().subtract(eye);
            if (toTarget.length() > CANDIDATE_RANGE) {
                continue;
            }
            double angle = angleBetween(look, toTarget);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = p;
            }
        }
        return best;
    }
}
