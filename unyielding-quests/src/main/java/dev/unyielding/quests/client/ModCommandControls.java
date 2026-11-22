package dev.unyielding.quests.client;

import com.mojang.brigadier.tree.RootCommandNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A small list of directly usable actions from this player's current command tree. */
final class ModCommandControls {
    record Control(CommandCatalog.Entry entry, String label) {}

    private RootCommandNode<?> root;
    private String filter = "";
    private boolean excavationVisible;
    private List<Control> controls = List.of();

    static String modFilter(String modId) {
        return switch (modId) {
            case "uwudimension" -> "uwu";
            case "anyastorage" -> "anya storage";
            default -> modId;
        };
    }

    List<Control> find(RootCommandNode<?> current, String modId, String query, boolean hideExcavation) {
        String wanted = (modFilter(modId) + " " + query).strip().toLowerCase(Locale.ROOT);
        if (current == root && filter.equals(wanted) && hideExcavation == excavationVisible) return controls;
        root = current;
        filter = wanted;
        excavationVisible = hideExcavation;
        var found = new ArrayList<Control>();
        for (var entry : CommandCatalog.search(current, wanted, 128)) {
            String path = entry.path();
            if (path.length() > 255) continue;
            String first = path.split(" ", 2)[0];
            String alias = modFilter(modId);
            // Command ownership is not transmitted. Require a matching root or namespace for in-page actions.
            if (!path.startsWith(alias + " ") && !first.equals(alias) && !first.startsWith(alias + ":") && !first.startsWith(alias + "_")
                    && !first.startsWith(alias + "-") && !first.startsWith(modId + ":")
                    && !(modId.equals("uwudimension") && (first.equals("uwucourt") || first.equals("uwuguardian")))) continue;
            if (NumericCommandInput.supported(current, path)) {
                found.add(new Control(entry, "Set number"));
            } else if (CommandCatalog.isBooleanAction(current, path)) {
                if (hideExcavation && modId.equals("uwudimension") && path.contains("launcher excavation ")) continue;
                found.add(new Control(entry, "Run " + path.substring(path.lastIndexOf(' ') + 1)));
            }
            if (found.size() == 6) break;
        }
        controls = List.copyOf(found);
        return controls;
    }
}
