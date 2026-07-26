package combat_tracker.record;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Integrity helpers for recorded sessions.
 *
 * <p><b>Honest limitation:</b> the signing key below ships inside this
 * open-source jar, so the HMAC signature only proves a file was produced by an
 * <em>unmodified</em> build and was not edited afterwards. A determined cheater
 * who recompiles the mod can forge data. This is best-effort tamper-<em>evidence</em>,
 * not unforgeable proof.</p>
 */
public final class IntegrityUtil {
    private static final byte[] SIGNING_KEY =
            "combat-tracker::builtdoor::v1::session-integrity".getBytes(StandardCharsets.UTF_8);

    private IntegrityUtil() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hmacSha256Hex(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY, "HmacSHA256"));
            return hex(mac.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
