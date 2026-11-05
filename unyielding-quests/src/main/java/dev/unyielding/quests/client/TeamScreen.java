package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.team.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Team management: create / join / invite / settings / chat. */
public class TeamScreen extends Screen {
    private enum Tab {TEAM, JOIN, SETTINGS, CHAT}

    private static Tab tab = Tab.TEAM;
    private static String chatDraft = "";

    private record Field(EditBox box, int x, int y, int w, int h) {
    }

    private int px, py, pw, ph, top;
    private int seenVersion = -1;
    private EditBox nameBox, inviteBox, chatBox, descBox, colorBox, renameBox;
    private String nameDraft = "";
    private final List<Field> fields = new ArrayList<>();

    public TeamScreen() {
        super(Component.literal("Teams"));
    }

    private UUID me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : new UUID(0, 0);
    }

    private static void send(String action, String a, String b) {
        Net.sendToServer(new Net.TeamAction(action, a, b));
        Ui.play(SoundEvents.UI_BUTTON_CLICK, 1.2f, 0.35f);
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        fields.clear();
        pw = Math.min(400, width - 16);
        ph = Math.min(262, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        top = py + 58;
        seenVersion = ClientData.teamVersion;
        Team mine = ClientData.myTeam();
        boolean party = mine != null && mine.type != Team.Type.PLAYER;
        if (!party && (tab == Tab.SETTINGS || tab == Tab.CHAT)) tab = Tab.TEAM;

        // header buttons
        addRenderableWidget(SpriteButton.iconButton(px + pw - 26, py + 6, 18, Ui.ICON_CLOSE, Component.literal("Close"), x -> onClose()));
        int qw = font.width("Quests") + 24;
        addRenderableWidget(new SpriteButton(px + pw - 30 - qw, py + 6, qw, 18, Component.literal("Quests"), Ui.ICON_QUESTS, Ui.Style.NORMAL, x -> {
            if (ClientData.file != null) Minecraft.getInstance().setScreen(new QuestScreen());
        }));

        // tabs
        int tx = px + 10;
        for (Tab t : Tab.values()) {
            if (!party && (t == Tab.SETTINGS || t == Tab.CHAT)) continue;
            final Tab target = t;
            String label = switch (t) {
                case TEAM -> "Team";
                case JOIN -> ClientData.invites.isEmpty() ? "Join" : "Join (" + ClientData.invites.size() + ")";
                case SETTINGS -> "Settings";
                case CHAT -> "Chat";
            };
            ResourceLocation icon = switch (t) {
                case TEAM -> Ui.ICON_TEAM;
                case JOIN -> Ui.ICON_JOIN;
                case SETTINGS -> Ui.ICON_GEAR;
                case CHAT -> Ui.ICON_CHAT;
            };
            int w = font.width(label) + 26;
            SpriteButton b = new SpriteButton(tx, py + 32, w, 18, Component.literal(label), icon, tab == t ? Ui.Style.TAB_SELECTED : Ui.Style.TAB, x -> {
                saveDrafts();
                tab = target;
                rebuildWidgets();
            });
            addRenderableWidget(b);
            tx += w + 3;
        }

        int y = top;
        switch (tab) {
            case TEAM -> {
                if (!party) {
                    nameBox = box(px + 14, y + 48, 190, "Party name", nameDraft, 64);
                    SpriteButton create = new SpriteButton(px + 212, y + 48, 104, 20, Component.literal("Create party"), Ui.ICON_JOIN, Ui.Style.ACCENT, x -> {
                        send("create", nameBox.getValue(), "");
                        nameDraft = "";
                    });
                    create.active = ClientData.canCreateParty;
                    addRenderableWidget(create);
                } else {
                    buildMemberButtons(mine, y + 30);
                    int by = py + ph - 30;
                    inviteBox = box(px + 14, by, 130, "Player name", "", 32);
                    addRenderableWidget(new SpriteButton(px + 150, by, 60, 20, Component.literal("Invite"), Ui.ICON_JOIN, Ui.Style.ACCENT, x -> {
                        send("invite", inviteBox.getValue(), "");
                        inviteBox.setValue("");
                    }));
                    boolean owner = mine.rankOf(me()) == Team.Rank.OWNER;
                    if (mine.type == Team.Type.PARTY) {
                        addRenderableWidget(new SpriteButton(px + pw - 14 - 58 - (owner ? 66 : 0), by, 58, 20, Component.literal("Leave"), null, Ui.Style.NORMAL,
                                x -> send("leave", "", "")));
                        if (owner) {
                            addRenderableWidget(new SpriteButton(px + pw - 14 - 62, by, 62, 20, Component.literal("Disband"), null, Ui.Style.ACCENT,
                                    x -> send("disband", "", "")));
                        }
                    }
                }
            }
            case JOIN -> {
                int ry = y + 16;
                List<UUID> inv = new ArrayList<>(ClientData.invites);
                for (UUID id : inv) {
                    if (ry > py + ph - 44) break;
                    final String key = id.toString();
                    addRenderableWidget(new SpriteButton(px + pw - 14 - 116, ry + 2, 56, 16, Component.literal("Accept"), Ui.ICON_CHECK, Ui.Style.ACCENT,
                            x -> send("join", key, "")));
                    addRenderableWidget(new SpriteButton(px + pw - 14 - 56, ry + 2, 56, 16, Component.literal("Decline"), null, Ui.Style.NORMAL,
                            x -> send("decline", key, "")));
                    ry += 22;
                }
                if (inv.isEmpty()) ry += 22;
                ry += 18;
                if (!party) {
                    for (Team t : ClientData.openParties()) {
                        if (ry > py + ph - 28) break;
                        final String key = t.id.toString();
                        addRenderableWidget(new SpriteButton(px + pw - 14 - 56, ry + 2, 56, 16, Component.literal("Join"), Ui.ICON_JOIN, Ui.Style.ACCENT,
                                x -> send("join", key, "")));
                        ry += 22;
                    }
                }
            }
            case SETTINGS -> {
                boolean officer = mine != null && mine.rankOf(me()) != null && mine.rankOf(me()).atLeast(Team.Rank.OFFICER);
                if (officer) {
                    renameBox = box(px + 80, y + 4, 200, "Name", mine.name, 64);
                    addRenderableWidget(new SpriteButton(px + 288, y + 4, 44, 20, Component.literal("Set"), null, Ui.Style.ACCENT,
                            x -> send("set", "name", renameBox.getValue())));
                    descBox = box(px + 80, y + 30, 200, "Description", mine.description, 200);
                    addRenderableWidget(new SpriteButton(px + 288, y + 30, 44, 20, Component.literal("Set"), null, Ui.Style.ACCENT,
                            x -> send("set", "description", descBox.getValue())));
                    colorBox = box(px + 80, y + 56, 200, "#RRGGBB", String.format("#%06X", mine.color & 0xFFFFFF), 7);
                    addRenderableWidget(new SpriteButton(px + 288, y + 56, 44, 20, Component.literal("Set"), null, Ui.Style.ACCENT,
                            x -> send("set", "color", colorBox.getValue())));
                    addRenderableWidget(new SpriteButton(px + 80, y + 86, 200, 20,
                            Component.literal(mine.freeToJoin ? "Anyone can join: ON" : "Anyone can join: OFF"), mine.freeToJoin ? Ui.ICON_CHECK : Ui.ICON_LOCK,
                            Ui.Style.NORMAL, x -> send("set", "free", mine.freeToJoin ? "false" : "true")));
                }
            }
            case CHAT -> {
                chatBox = box(px + 14, py + ph - 30, pw - 92, "Type a message to your team", chatDraft, 300);
                addRenderableWidget(new SpriteButton(px + pw - 72, py + ph - 30, 58, 20, Component.literal("Send"), Ui.ICON_CHAT, Ui.Style.ACCENT, x -> sendChat()));
                setFocused(chatBox);
            }
        }
    }

    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x + 6, y + 6, w - 12, 10, Component.literal(hint));
        b.setBordered(false);
        b.setMaxLength(max);
        b.setTextColor(Theme.text);
        b.setHint(Component.literal(hint).withStyle(s -> s.withColor(Theme.textMuted)));
        b.setValue(value);
        addRenderableWidget(b);
        fields.add(new Field(b, x, y, w, 20));
        return b;
    }

    private void sendChat() {
        if (chatBox == null) return;
        String v = chatBox.getValue().trim();
        if (v.isEmpty()) return;
        send("chat", v, "");
        chatBox.setValue("");
        chatDraft = "";
    }

    private void saveDrafts() {
        if (nameBox != null) nameDraft = nameBox.getValue();
        if (chatBox != null) chatDraft = chatBox.getValue();
    }

    private void buildMemberButtons(Team t, int startY) {
        UUID me = me();
        Team.Rank myRank = t.rankOf(me);
        if (myRank == null) return;
        int y = startY;
        int shown = 0;
        for (var e : t.members.entrySet()) {
            if (shown++ >= 6) break;
            UUID u = e.getKey();
            Team.Rank r = e.getValue();
            if (!u.equals(me) && myRank.atLeast(Team.Rank.OFFICER) && r.ordinal() < myRank.ordinal()) {
                final String name = t.nameOf(u);
                int bx = px + pw - 16 - 44;
                addRenderableWidget(new SpriteButton(bx, y + 2, 44, 15, Component.literal("Kick"), null, Ui.Style.ACCENT, x -> send("kick", name, "")));
                if (myRank == Team.Rank.OWNER) {
                    bx -= 48;
                    if (r == Team.Rank.MEMBER) {
                        addRenderableWidget(new SpriteButton(bx, y + 2, 44, 15, Component.literal("Promote"), null, Ui.Style.NORMAL, x -> send("promote", name, "")));
                    } else {
                        addRenderableWidget(new SpriteButton(bx, y + 2, 44, 15, Component.literal("Demote"), null, Ui.Style.NORMAL, x -> send("demote", name, "")));
                    }
                    bx -= 48;
                    addRenderableWidget(new SpriteButton(bx, y + 2, 44, 15, Component.literal("Owner"), Ui.ICON_CROWN, Ui.Style.NORMAL, x -> send("transfer", name, "")));
                }
            }
            y += 20;
        }
    }

    @Override
    public void tick() {
        if (seenVersion != ClientData.teamVersion) {
            saveDrafts();
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft.level == null) renderPanorama(g, pt);
        if (UiConfig.blur()) renderBlurredBackground(pt);
        g.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.55f + 0.45f * UiConfig.opacity()));
    }

    private static PlayerSkin skin(UUID id) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        Ui.panel(g, px, py, pw, ph);
        Ui.panel(g, Ui.HEADER, px, py, pw, 30);
        Ui.icon(g, Ui.ICON_TEAM, px + 10, py + 11, Theme.accent);
        Team mine = ClientData.myTeam();
        boolean party = mine != null && mine.type != Team.Type.PLAYER;
        g.drawString(font, Component.literal(party ? "Your Party" : "Teams").withStyle(ChatFormatting.BOLD), px + 24, py + 11, Theme.text, true);
        g.fill(px + 8, py + 53, px + pw - 8, py + 54, 0x30FFFFFF);
        for (Field f : fields) Ui.blit(g, f.box().isFocused() ? Ui.FIELD_FOCUSED : Ui.FIELD, f.x(), f.y(), f.w(), f.h());

        int y = top;
        int x0 = px + 14, x1 = px + pw - 14;
        switch (tab) {
            case TEAM -> {
                if (!party) {
                    Ui.blit(g, Ui.CARD, x0 - 4, y - 2, x1 - x0 + 8, 34);
                    g.drawString(font, Component.literal("You are playing solo.").withStyle(ChatFormatting.BOLD), x0 + 2, y + 4, Theme.text, false);
                    g.drawString(font, ClientData.canCreateParty ? "Make a party to share quest progress with your friends."
                            : "Making parties is turned off on this server.", x0 + 2, y + 17, Theme.textDim, false);
                    g.drawString(font, "Party name", x0, y + 38, Theme.textMuted, false);
                    if (!ClientData.invites.isEmpty()) {
                        String s = "You have " + ClientData.invites.size() + " invite(s). Open the Join tab to answer.";
                        Ui.chip(g, font, x0, y + 76, s, Theme.claim, 0xFF2A1C00);
                    }
                } else {
                    int dot = mine.color | 0xFF000000;
                    g.fill(x0, y + 2, x0 + 6, y + 8, dot);
                    g.drawString(font, Component.literal(mine.name).withStyle(s -> s.withColor(mine.color).withBold(true)), x0 + 10, y + 1, 0xFFFFFFFF, true);
                    String type = mine.type == Team.Type.SERVER ? "SERVER TEAM" : "PARTY";
                    int nw = font.width(Component.literal(mine.name).withStyle(ChatFormatting.BOLD));
                    Ui.chip(g, font, x0 + 16 + nw, y, type, 0xFF3A4150, Theme.textDim);
                    String cnt = mine.members.size() + (mine.members.size() == 1 ? " member" : " members");
                    g.drawString(font, cnt, x1 - font.width(cnt), y + 1, Theme.textMuted, false);
                    if (!mine.description.isEmpty())
                        g.drawString(font, font.plainSubstrByWidth(mine.description, pw - 28), x0, y + 14, Theme.textDim, false);
                    int ry = y + 30;
                    int shown = 0;
                    for (var e : mine.members.entrySet()) {
                        if (shown++ >= 6) {
                            g.drawString(font, "+" + (mine.members.size() - 6) + " more", x0, ry + 5, Theme.textMuted, false);
                            break;
                        }
                        UUID u = e.getKey();
                        boolean online = ClientData.online.containsKey(u);
                        Ui.blit(g, Ui.CARD, x0 - 4, ry, x1 - x0 + 8, 19);
                        PlayerFaceRenderer.draw(g, skin(u), x0, ry + 3, 12);
                        g.fill(x0 + 10, ry + 12, x0 + 14, ry + 16, 0xFF101216);
                        g.fill(x0 + 11, ry + 13, x0 + 13, ry + 15, online ? Theme.done : Theme.locked);
                        g.drawString(font, mine.nameOf(u), x0 + 18, ry + 6, online ? Theme.text : Theme.textDim, false);
                        int nx = x0 + 22 + font.width(mine.nameOf(u));
                        switch (e.getValue()) {
                            case OWNER -> {
                                Ui.icon(g, Ui.ICON_CROWN, nx, ry + 7, Theme.gold);
                                g.drawString(font, "Owner", nx + 10, ry + 6, Theme.gold, false);
                            }
                            case OFFICER -> {
                                Ui.icon(g, Ui.ICON_STAR, nx, ry + 6, Theme.available);
                                g.drawString(font, "Officer", nx + 10, ry + 6, Theme.available, false);
                            }
                            default -> g.drawString(font, "Member", nx, ry + 6, Theme.textMuted, false);
                        }
                        ry += 20;
                    }
                    g.drawString(font, "Invite a player", x0, py + ph - 42, Theme.textMuted, false);
                }
            }
            case JOIN -> {
                g.drawString(font, Component.literal("INVITATIONS").withStyle(ChatFormatting.BOLD), x0, y + 2, Theme.heading, false);
                int ry = y + 16;
                if (ClientData.invites.isEmpty()) {
                    g.drawString(font, "No invites right now. Ask a friend to invite you.", x0, ry + 6, Theme.textMuted, false);
                    ry += 22;
                }
                for (UUID id : ClientData.invites) {
                    if (ry > py + ph - 44) break;
                    Team t = ClientData.teams.get(id);
                    Ui.blit(g, Ui.CARD, x0 - 4, ry, x1 - x0 + 8, 20);
                    g.drawString(font, t == null ? "Unknown team" : font.plainSubstrByWidth(t.name, pw - 150), x0 + 2, ry + 6,
                            t == null ? Theme.textMuted : t.color | 0xFF000000, false);
                    ry += 22;
                }
                ry += 4;
                g.drawString(font, Component.literal("OPEN PARTIES").withStyle(ChatFormatting.BOLD), x0, ry + 2, Theme.heading, false);
                ry += 14;
                if (party) {
                    g.drawString(font, "Leave your current team to join another one.", x0, ry + 6, Theme.textMuted, false);
                } else {
                    List<Team> open = ClientData.openParties();
                    if (open.isEmpty()) g.drawString(font, "No parties are open to join right now.", x0, ry + 6, Theme.textMuted, false);
                    for (Team t : open) {
                        if (ry > py + ph - 28) break;
                        Ui.blit(g, Ui.CARD, x0 - 4, ry, x1 - x0 + 8, 20);
                        g.drawString(font, font.plainSubstrByWidth(t.name, pw - 120), x0 + 2, ry + 6, t.color | 0xFF000000, false);
                        String c = t.members.size() + " in party";
                        g.drawString(font, c, x1 - 64 - font.width(c), ry + 6, Theme.textMuted, false);
                        ry += 22;
                    }
                }
            }
            case SETTINGS -> {
                boolean officer = mine != null && mine.rankOf(me()) != null && mine.rankOf(me()).atLeast(Team.Rank.OFFICER);
                if (!officer) {
                    g.drawString(font, "Only officers and the owner can change these settings.", x0, y + 6, Theme.textMuted, false);
                } else {
                    g.drawString(font, "Name", x0, y + 10, Theme.textDim, false);
                    g.drawString(font, "About", x0, y + 36, Theme.textDim, false);
                    g.drawString(font, "Colour", x0, y + 62, Theme.textDim, false);
                    g.fill(px + 336, y + 58, px + 352, y + 74, 0xFF000000);
                    g.fill(px + 337, y + 59, px + 351, y + 73, mine.color | 0xFF000000);
                    g.drawString(font, "Who can join", x0, y + 92, Theme.textDim, false);
                }
            }
            case CHAT -> {
                int ly0 = y, ly1 = py + ph - 36;
                Ui.blit(g, Ui.CARD, x0 - 4, ly0 - 2, x1 - x0 + 8, ly1 - ly0 + 2);
                if (mine != null) {
                    int lines = (ly1 - ly0 - 6) / 10;
                    int from = Math.max(0, mine.history.size() - lines);
                    int ry = ly0 + 3;
                    if (mine.history.isEmpty()) g.drawString(font, "No messages yet. Say hi!", x0 + 2, ry, Theme.textMuted, false);
                    for (int i = from; i < mine.history.size(); i++) {
                        Team.Message m = mine.history.get(i);
                        if (m.fromName.isEmpty()) {
                            g.drawString(font, font.plainSubstrByWidth(m.text, pw - 32), x0 + 2, ry, Theme.textMuted, false);
                        } else {
                            Component c = Component.literal(m.fromName + ": ").withStyle(s -> s.withColor(Theme.accentText).withBold(true))
                                    .append(Component.literal(m.text).withStyle(s -> s.withColor(Theme.text).withBold(false)));
                            g.drawString(font, font.substrByWidth(c, pw - 32).getString(), x0 + 2, ry, Theme.text, false);
                        }
                        ry += 10;
                    }
                }
            }
        }
        for (var r : renderables) r.render(g, mx, my, pt);
        if (!ClientData.notice.isEmpty() && System.currentTimeMillis() - ClientData.noticeTime < 4000) {
            int w = font.width(ClientData.notice) + 16;
            int ny = py + ph + 4 > height - 18 ? py + ph - 52 : py + ph + 4;
            Ui.blit(g, Ui.CHIP, width / 2 - w / 2, ny, w, 16, 0xE0A02020);
            g.drawCenteredString(font, ClientData.notice, width / 2, ny + 4, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && tab == Tab.CHAT && chatBox != null && chatBox.isFocused()) {
            sendChat();
            return true;
        }
        if ((key == 257 || key == 335) && nameBox != null && nameBox.isFocused() && tab == Tab.TEAM) {
            send("create", nameBox.getValue(), "");
            return true;
        }
        if ((key == 257 || key == 335) && inviteBox != null && inviteBox.isFocused() && tab == Tab.TEAM) {
            send("invite", inviteBox.getValue(), "");
            inviteBox.setValue("");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        saveDrafts();
        Net.sendToServer(new Net.TeamAction("refresh", "", ""));
    }
}
