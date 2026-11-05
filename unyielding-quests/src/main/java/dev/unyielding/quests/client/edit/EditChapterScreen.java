package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestEdit;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestFile.Chapter;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** New chapter / edit chapter form. */
public class EditChapterScreen extends FormScreen {
    private final Chapter existing;
    private String title, subtitle, icon;
    private long group;

    public EditChapterScreen(Screen parent, Chapter c, long defaultGroup) {
        super(parent, c == null ? "New chapter" : "Edit chapter");
        existing = c;
        title = c == null ? "" : c.title;
        subtitle = c == null || c.subtitle.isEmpty() ? "" : String.join("\\n", c.subtitle);
        icon = c == null ? "" : c.icon == null ? "" : c.icon.id;
        group = c == null ? defaultGroup : c.group;
    }

    @Override
    protected int panelHeight() {
        return 196;
    }

    @Override
    protected void build() {
        int x = labelled("Name");
        field("title", x, right() - x, title, "Chapter name (use & for colours)", s -> title = s);
        gap(24);
        x = labelled("Subtitle");
        field("sub", x, right() - x, subtitle, "Short text under the name (optional)", s -> subtitle = s);
        gap(24);
        x = labelled("Icon");
        field("icon", x, right() - x - 70, icon, "Item id (optional)", s -> icon = s.trim());
        button(right() - 64, 64, "Held item", null, Ui.Style.NORMAL, () -> {
            icon = heldItemId();
            relayout();
        });
        gap(24);
        QuestFile f = ClientData.file;
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (f != null) for (QuestFile.Group g : f.groups) {
            ids.add(g.id);
            names.add(g.title.isEmpty() ? "(unnamed group)" : g.title.replaceAll("&[0-9a-fk-or]", ""));
        }
        names.add("(no group)");
        ids.add(0L);
        x = labelled("Group");
        int cur = Math.max(0, ids.indexOf(group));
        cycle(x, right() - x, "", names.toArray(new String[0]), names.get(cur), s -> group = ids.get(names.indexOf(s)));
        gap(26);
        note("Groups are the headings in the chapter list on the left.");
        gap(4);
    }

    @Override
    protected String saveLabel() {
        return existing == null ? "Create" : "Save";
    }

    @Override
    protected void save() {
        if (title.isBlank()) title = "New chapter";
        List<String> sub = new ArrayList<>();
        if (!subtitle.isBlank()) for (String l : subtitle.split("\\\\n")) sub.add(l);
        if (existing == null) {
            Net.sendToServer(new Net.EditRequest(QuestEdit.chapterAdd(QuestEdit.newId(), title, group, icon)));
        } else {
            Net.sendToServer(new Net.EditRequest(QuestEdit.chapterSet(existing.id, title, sub, group, icon)));
        }
        minecraft.setScreen(parent);
    }
}
