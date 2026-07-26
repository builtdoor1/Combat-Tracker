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

import java.util.Optional;

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

    /** How far to trace when looking for the hitbox the crosshair crosses. */
    private static final double TRACE_DISTANCE = 8.0;
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

        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = self.getEyePosition(partial);
        Vec3 look = self.getViewVector(partial).normalize();

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

        double reach = reachTo(eye, look, box);
        double aimDeg = angleBetween(look, centre.subtract(eye));
        double[] placement = placementOnHitbox(eye, look, box, centre);

        SessionRecorder.get().recordSwing(
                reach, landed, aimDeg, placement[0], placement[1], target.getName().getString());
    }

    /**
     * Distance from the eye to the target's hitbox — the number the community and
     * server anti-cheats both mean by "reach". When the crosshair actually crosses
     * the box we use that entry point; when it misses we fall back to the nearest
     * point on the box, which is still the distance you were swinging from.
     */
    private static double reachTo(Vec3 eye, Vec3 look, AABB box) {
        Optional<Vec3> entry = box.clip(eye, eye.add(look.scale(TRACE_DISTANCE)));
        if (entry.isPresent()) {
            return eye.distanceTo(entry.get());
        }
        return eye.distanceTo(clampToBox(eye, box));
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

    /** Nearest point on the box to a position outside it. */
    private static Vec3 clampToBox(Vec3 p, AABB box) {
        return new Vec3(
                Math.max(box.minX, Math.min(box.maxX, p.x)),
                Math.max(box.minY, Math.min(box.maxY, p.y)),
                Math.max(box.minZ, Math.min(box.maxZ, p.z)));
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
