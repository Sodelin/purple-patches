import android.content.Context;
import dev.jason.gboardpatches.extension.accesspoint.GboardAccessPointContributionsRuntime;
import dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPointsContribution;
import dev.purple.patches.gboard.PurpleAccessPoints;
import dev.purple.patches.gboard.PurpleAiRuntime;
import java.util.Arrays;
import java.util.List;

/** Exercises the real overlay and its materialized pinned upstream hooks with synthetic host classes. */
public final class PurpleAccessPointsCheck {
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static wpa stock() { return new wpa(Arrays.asList("stock-a", "stock-b")); }
    private static void reset(boolean enabled, int failAt) {
        PurpleAiRuntime.enabled = enabled;
        PurpleAiRuntime.calls = 0;
        PurpleAiRuntime.failAt = failAt;
        PurpleAiRuntime.clicked = null;
        GboardEditingAccessPointsContribution.registrations = 0;
    }

    public static void main(String[] args) {
        Context context = new Context();
        wpa original = stock();
        reset(false, 0);
        check(GboardAccessPointContributionsRuntime.includeOrderCatalog(context, original) == original,
                "Disabled overlay changed stock catalog");
        mwv disabled = new mwv(original);
        GboardAccessPointContributionsRuntime.afterControllerCreated(disabled, context);
        check(disabled.g.c == original && disabled.registered.isEmpty(), "Disabled overlay registered actions");
        check(GboardEditingAccessPointsContribution.registrations == 1, "Upstream registration was skipped");

        reset(true, 0);
        mwv enabled = new mwv(stock());
        GboardAccessPointContributionsRuntime.afterControllerCreated(enabled, context);
        check(enabled.g.c.containsAll(Arrays.asList("stock-a", "stock-b", "purple_whisper", "purple_translate"))
                && enabled.g.c.size() == 4, "Stock or optional catalog entries missing");
        check(enabled.registered.size() == 2, "Optional actions not registered exactly once");
        mtr voice = enabled.registered.get(0), translate = enabled.registered.get(1);
        check("purple_whisper".equals(voice.token) && "Whisper 3".equals(voice.label)
                && voice.icon == android.R.drawable.ic_btn_speak_now, "Voice descriptor differs");
        check("purple_translate".equals(translate.token) && "AI Translate".equals(translate.label)
                && translate.icon == android.R.drawable.ic_menu_edit, "Translation descriptor differs");
        voice.callback.run(); check(Boolean.TRUE.equals(PurpleAiRuntime.clicked), "Voice callback differs");
        translate.callback.run(); check(Boolean.FALSE.equals(PurpleAiRuntime.clicked), "Translation callback differs");
        check(GboardEditingAccessPointsContribution.registrations == 1, "Upstream registration was skipped");
        Object again = PurpleAccessPoints.extend(context, enabled.g.c);
        check(again instanceof wpa && ((wpa) again).size() == 4, "Repeat catalog extension duplicated entries");
        wpa unexpected = new wpa(Arrays.asList("stock-a", Integer.valueOf(7)));
        check(PurpleAccessPoints.extend(context, unexpected) == unexpected, "Unknown stock catalog was changed");
        Object unsupported = new Object();
        check(PurpleAccessPoints.extend(context, unsupported) == unsupported, "Unsupported catalog was changed");

        reset(true, -1);
        original = stock();
        check(GboardAccessPointContributionsRuntime.includeOrderCatalog(context, original) == original,
                "Missing optional dependency must retain stock catalog");
        mwv unavailable = new mwv(original);
        GboardAccessPointContributionsRuntime.afterControllerCreated(unavailable, context);
        check(unavailable.g.c == original, "Unavailable overlay changed stock catalog");
        check(GboardEditingAccessPointsContribution.registrations == 1,
                "Optional linkage failure suppressed upstream registration");

        reset(true, 2);
        mwv lateFailure = new mwv(stock());
        GboardAccessPointContributionsRuntime.afterControllerCreated(lateFailure, context);
        check(lateFailure.g.c.containsAll(List.of("stock-a", "stock-b")), "Stock entries disappeared");
        check(GboardEditingAccessPointsContribution.registrations == 1,
                "Registration linkage failure suppressed upstream registration");
        System.out.println("GBOARD_ACCESS_POINTS_SYNTHETIC_CONTROLS_PASSED");
    }
}
