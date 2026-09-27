import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.client.vfx.ClientConsortCloneEffects;
import com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer;

/** Lifecycle and authored-clock regressions; no graphics context or running world required. */
public class HolyCloneEffectsCheck {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapState = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapState.setAccessible(true);
        bootstrapState.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();

        // Network time can begin at any world tick; fade must be identical after rebasing.
        for (long start : new long[]{0, 1000, 24000000}) {
            for (int lead : new int[]{1, 2, 4, 10, 15}) {
                float previous = 0;
                for (int frame = -4; frame <= 136; frame++) {
                    double elapsed = frame / 8.0;
                    float alpha = ClientConsortCloneEffects.opacity(start + elapsed, start, start + lead, start + 16);
                    require(Float.isFinite(alpha) && alpha >= 0 && alpha <= 1, "Invalid clone alpha");
                    require(alpha == ClientConsortCloneEffects.opacity(elapsed, 0, lead, 16), "World clock shifted the fade");
                    if (elapsed <= 0 || elapsed >= 16) require(alpha == 0, "Clone visible outside lifetime");
                    if (elapsed >= 2 && elapsed <= lead) require(alpha == 1, "Clone faded before the contact pose");
                    require(Math.abs(alpha - previous) < 0.19, "Abrupt appearance/disappearance");
                    previous = alpha;
                }
            }
        }
        float previous = 1;
        for (double time = 11; time <= 16; time += 0.125) {
            float alpha = ClientConsortCloneEffects.opacity(time, 0, 4, 16);
            require(alpha <= previous, "Dissolving clone became solid again");
            previous = alpha;
        }
        require(ClientConsortCloneEffects.opacity(0, 0, 0, 0) == 0, "Invalid lifetime became visible");
        require(ClientConsortCloneEffects.opacity(15.75, 0, 30, 16) < 0.2, "Late contact prevents despawn fade");

        for (var action : PromisedConsortActionId.values()) {
            float last = 0;
            for (double tick = -1; tick <= 260; tick += 0.125) {
                float strength = ClientConsortEnergyRenderer.holyChargeStrength(action, tick);
                require(Float.isFinite(strength) && strength >= 0 && strength <= 1, "Invalid charge for " + action);
                require(Math.abs(strength - last) < 0.05, "Charge pop for " + action + " at " + tick);
                if (tick <= 0 || tick >= 140) require(strength == 0, "Charge outlived the attack");
                last = strength;
            }
        }
        require(ClientConsortEnergyRenderer.holyChargeStrength(PromisedConsortActionId.LIGHT_OF_MIQUELLA, 105) == 1,
                "Great light must peak before the 110-tick eruption");
        require(ClientConsortEnergyRenderer.holyChargeStrength(PromisedConsortActionId.LIGHTSPEED_SLASH, 50) == 1,
                "Airborne clone sequence lost its held radiance");
        require(ClientConsortEnergyRenderer.holyChargeStrength(PromisedConsortActionId.PROMISED_CONSORT, 50) == 0,
                "Finisher buildup leaked into early sword swings");
        require(ClientConsortEnergyRenderer.holyChargeStrength(null, 10) == 0, "Idle generated holy light");
        System.out.println("Holy buildup and clone lifecycle passed: " + checks + " checks");
    }

    private static void require(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
}
