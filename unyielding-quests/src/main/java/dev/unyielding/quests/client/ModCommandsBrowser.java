package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/** Shows the same commands the server advertises to this player in chat. */
final class ModCommandsBrowser extends FormScreen {
    private static final int PAGE_SIZE = 8;
    private static final int LIMIT = 256;
    private String query;
    private String message = "";
    private boolean dirty = true;
    private boolean lastAdmin;
    private int page;
    private Object lastRoot;
    private boolean numericOnly;
    private String numericPath = "";
    private String number = "";
    private List<CommandCatalog.Entry> results = List.of();

    ModCommandsBrowser(Screen parent, String filter) {
        super(parent, "Server commands");
        this.query = filter;
    }

    static String activate(Screen parent, String filter, CommandCatalog.Entry entry) {
        var client = Minecraft.getInstance();
        var connection = client.getConnection();
        if (connection == null || !AdminSettingsScreen.canManage()) return "Server admin access is required.";
        var root = connection.getCommands().getRoot();
        if (CommandCatalog.isBooleanAction(root, entry.path())) {
            return sendBoolean(client, entry.path());
        }
        if (NumericCommandInput.supported(root, entry.path())) {
            var browser = new ModCommandsBrowser(parent, filter);
            browser.numericPath = entry.path();
            browser.numericOnly = true;
            client.setScreen(browser);
            return "";
        }
        return "The server's available commands changed. Refresh and choose the action again.";
    }

    private static String sendBoolean(Minecraft client, String path) {
        var connection = client.getConnection();
        if (connection == null || !AdminSettingsScreen.canManage() || path.length() > 255
                || !CommandCatalog.isBooleanAction(connection.getCommands().getRoot(), path)) {
            return "The server's available commands changed. Choose the action again.";
        }
        connection.sendCommand(path);
        return "Sent /" + path + ". The server reports the result in chat.";
    }

    @Override public void tick() {
        super.tick();
        var connection = minecraft.getConnection();
        Object root = connection == null ? null : connection.getCommands().getRoot();
        boolean admin = AdminSettingsScreen.canManage();
        if (dirty || root != lastRoot || admin != lastAdmin) relayout();
    }

    @Override protected void build() {
        var connection = minecraft.getConnection();
        boolean previousAdmin = lastAdmin;
        lastAdmin = AdminSettingsScreen.canManage();
        if (connection == null || !lastAdmin) {
            dirty = false;
            lastRoot = connection == null ? null : connection.getCommands().getRoot();
            paragraph("Join the world as a server admin to view these commands.", Theme.text, false);
            return;
        }
        var root = connection.getCommands().getRoot();
        if (dirty || lastRoot != root || !previousAdmin) {
            results = CommandCatalog.search(root, query, LIMIT);
            dirty = false;
            lastRoot = root;
        }
        if (!numericPath.isEmpty()) {
            paragraph("/" + numericPath, Theme.text, true);
            String hint = NumericCommandInput.rangeHint(root, numericPath);
            if (!hint.isBlank()) paragraph(hint, Theme.textDim, false);
            field("number", left(), cy, innerWidth(), number, "Enter a number", 64, value -> number = value);
            gap(25);
            button(left(), innerWidth(), "Run command", null, Ui.Style.ACCENT, this::submitNumber);
            gap(25);
            if (!message.isEmpty()) paragraph(message, Theme.heading, false);
            return;
        }
        field("commands", left(), cy, innerWidth() - 63, query, "Search server commands", 96, value -> {
            query = value;
            page = 0;
            scroll = 0;
            message = "";
            dirty = true;
        });
        button(right() - 59, 59, "Show all", null, Ui.Style.NORMAL, () -> {
            query = "";
            page = 0;
            scroll = 0;
            dirty = true;
            relayout();
        });
        gap(27);
        if (!message.isEmpty()) paragraph(message, Theme.heading, false);
        if (results.isEmpty()) {
            paragraph("No matching command. Try the command name, or choose Show all.", Theme.textDim, false);
            return;
        }
        if (results.size() == LIMIT) paragraph("Showing the first " + LIMIT + " matches. Search to narrow the list.", Theme.textDim, false);
        int pages = (results.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.min(page, pages - 1);
        if (pages > 1) {
            int half = (innerWidth() - 4) / 2;
            button(left(), half, "Previous", null, Ui.Style.NORMAL, () -> turn(-1)).active = page > 0;
            button(left() + half + 4, half, "Next", null, Ui.Style.NORMAL, () -> turn(1)).active = page + 1 < pages;
            gap(24);
            paragraph("Page " + (page + 1) + " of " + pages, Theme.textDim, false);
        }
        for (var entry : results.subList(page * PAGE_SIZE, Math.min(results.size(), (page + 1) * PAGE_SIZE))) {
            paragraph("/" + entry.usage(), Theme.text, true);
            boolean toggle = CommandCatalog.isBooleanAction(root, entry.path());
            if (entry.path().length() + 1 <= 256) {
                boolean numeric = NumericCommandInput.supported(root, entry.path());
                String label = numeric ? "Set number" : toggle ? "Run " + entry.path().substring(entry.path().lastIndexOf(' ') + 1) :
                        entry.needsArguments() ? "Set arguments in chat" : "Open in chat";
                button(left(), innerWidth(), label, null, Ui.Style.NORMAL, () -> select(entry, toggle, numeric));
                gap(25);
            }
        }
    }

    private void turn(int amount) {
        page += amount;
        scroll = 0;
        relayout();
    }

    private void select(CommandCatalog.Entry entry, boolean run, boolean numeric) {
        var connection = minecraft.getConnection();
        if (connection == null || !AdminSettingsScreen.canManage()) return;
        if (numeric && NumericCommandInput.supported(connection.getCommands().getRoot(), entry.path())) {
            numericPath = entry.path();
            number = "";
            message = "";
            scroll = 0;
            relayout();
            return;
        }
        if (run) {
            message = sendBoolean(minecraft, entry.path());
            dirty = true;
            relayout();
        } else {
            minecraft.setScreen(new ChatScreen("/" + entry.path() + (entry.needsArguments() ? " " : "")));
        }
    }

    private void submitNumber() {
        var connection = minecraft.getConnection();
        if (connection == null || !AdminSettingsScreen.canManage()) return;
        var command = NumericCommandInput.prepare(connection.getCommands().getRoot(), numericPath, number.strip());
        if (command.isEmpty()) {
            message = "Enter a number within the range this command accepts.";
        } else {
            connection.sendCommand(command.get());
            message = "Sent /" + command.get() + ". The server reports the result in chat.";
            numericPath = "";
            if (numericOnly) {
                minecraft.setScreen(parent);
                if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(message), true);
                return;
            }
        }
        relayout();
    }

    private void paragraph(String text, int color, boolean bold) {
        var contents = net.minecraft.network.chat.Component.literal(text);
        if (bold) contents.withStyle(net.minecraft.ChatFormatting.BOLD);
        for (var line : font.split(contents, innerWidth())) {
            var visible = new StringBuilder();
            line.accept((index, style, codepoint) -> { visible.appendCodePoint(codepoint); return true; });
            bodyLine(visible.toString(), color, bold);
        }
    }

    @Override public void onClose() { cancel(); }
    @Override protected String saveLabel() { return "Back"; }
    @Override protected boolean showCancelButton() { return false; }
    @Override protected void save() { cancel(); }
    @Override protected void cancel() {
        if (numericOnly || numericPath.isEmpty()) super.cancel();
        else {
            numericPath = "";
            message = "";
            relayout();
        }
    }
}
