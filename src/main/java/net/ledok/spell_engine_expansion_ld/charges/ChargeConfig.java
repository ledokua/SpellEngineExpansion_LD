package net.ledok.spell_engine_expansion_ld.charges;

import com.google.gson.JsonObject;

/// One `spell_charges` file: `{"charges": 2, "recharge": 8.0, "recast_lock": 0.5}`.
/// - `charges`: how many casts the spell holds (required, at least 1).
/// - `recharge`: seconds one charge takes to come back. Omitted = the spell's cooldown `duration`.
///   Either way `cooldown_duration_deduct` modifiers and haste apply, as they do to the cooldown.
/// - `recast_lock`: seconds between two casts (default 0.5), so a full stack can't go out in one tick.
public record ChargeConfig(int charges, float recharge, float recastLock) {
    public static final float RECHARGE_FROM_COOLDOWN = -1F;
    public static final float DEFAULT_RECAST_LOCK = 0.5F;

    public boolean rechargeFromCooldown() {
        return recharge < 0;
    }

    public static ChargeConfig fromJson(JsonObject json) {
        if (!json.has("charges")) {
            throw new IllegalArgumentException("missing \"charges\"");
        }
        int charges = json.get("charges").getAsInt();
        if (charges < 1) {
            throw new IllegalArgumentException("\"charges\" must be at least 1");
        }
        float recharge = json.has("recharge") ? json.get("recharge").getAsFloat() : RECHARGE_FROM_COOLDOWN;
        float lock = json.has("recast_lock") ? json.get("recast_lock").getAsFloat() : DEFAULT_RECAST_LOCK;
        return new ChargeConfig(charges, recharge, Math.max(0F, lock));
    }
}
