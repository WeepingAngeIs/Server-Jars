package dev.unyielding.bundlesmoke;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.unyielding.jail.JailConfig;
import dev.unyielding.jail.JailCustodyService;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** Calls the real server UI handler; it neither edits TOML nor substitutes a validator. */
final class PolicySettingsProof {
    private static String setting;
    private PolicySettingsProof() {}

    private static String key() throws Exception {
        if(setting!=null) return setting;
        var catalog=Class.forName("dev.unyielding.quests.settings.NeoForgeSettings");
        Method rows=catalog.getDeclaredMethod("rows",String.class,String.class,int.class,int.class);
        rows.setAccessible(true);
        Object page=rows.invoke(null,"minecraftjail","confiscate_inventory",0,25);
        Method accessor=page.getClass().getDeclaredMethod("rows");accessor.setAccessible(true);
        ListTag values=(ListTag)accessor.invoke(page);
        require(values.size()==1,"Expected one registered confiscation setting");
        CompoundTag value=values.getCompound(0);
        require(value.getString("aliases").equals("jail.confiscate_inventory")
                && value.getString("kind").equals("boolean") && value.getBoolean("available"),
                "Jail's registered server setting is unavailable");
        proof.put("server_config_scope",value.getString("scope"));
        setting=value.getString("key");
        return setting;
    }

    private static void action(ServerPlayer player,String key,String expected,String value) throws Exception {
        var action=Class.forName("dev.unyielding.quests.settings.ConfigEditor$Action");
        Object payload=action.getConstructor(String.class,String.class,String.class).newInstance(key,expected,value);
        Class.forName("dev.unyielding.quests.settings.ConfigEditor").getMethod("handle",ServerPlayer.class,action)
                .invoke(null,player,payload);
    }

    private static String request(String expected,boolean desired) throws Exception {
        var row=new JsonObject();row.addProperty("p",0);row.addProperty("q","");row.addProperty("key",key());
        row.addProperty("expected",expected);row.addProperty("value",Boolean.toString(desired));
        return row.toString();
    }

    static void defaults(ServerPlayer player) throws Exception {
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Confiscation did not default On");
        flag("confiscation_defaults_on");
        require(!player.hasPermissions(2),"Permission control requires an ordinary player");
        String payload=request("true",false);
        action(player,"__mod_settings_add","","minecraftjail");
        action(player,"__mod_settings_write","minecraftjail",payload);
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"A nonadministrator changed the authoritative policy");
        flag("nonadministrator_policy_edit_refused");
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            action(player,"__mod_settings_add","","minecraftjail");
            action(player,"__mod_settings_write","minecraftjail",request("false",false));
            require(JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"The admin UI accepted a stale expected value");
            flag("stale_server_ui_edit_refused");
            action(player,"__mod_settings_write","minecraftjail",payload);
            require(!JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Admin UI could not turn confiscation Off");
            action(player,"__mod_settings_write","minecraftjail",request("false",true));
            require(JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Admin UI could not restore confiscation On");
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        flag("server_admin_ui_policy_saved_and_read_back");
    }

    static void set(ServerPlayer player,boolean value) throws Exception {
        boolean before=JailConfig.JAIL_CONFISCATE_INVENTORY.get();
        require(before!=value,"Fixture requested no policy transition");
        // A prisoner is never promoted to bypass their network/custody restriction.
        // While custody is active, the separate synthetic administrator exercises the
        // same authoritative UI handler. This is not a second connected-client claim.
        ServerPlayer actor=JailCustodyService.quarantined(player)
                ? FakePlayerFactory.get(player.serverLevel(),new GameProfile(UUID.fromString("eed358c4-6d2d-4bcb-bbc6-69a646624d10"),"PolicyAdmin"))
                : player;
        require(!JailCustodyService.quarantined(actor),"Policy administrator is confined");
        actor.server.getPlayerList().op(actor.getGameProfile());
        try {
            action(actor,"__mod_settings_add","","minecraftjail");
            action(actor,"__mod_settings_write","minecraftjail",request(Boolean.toString(before),value));
            require(JailConfig.JAIL_CONFISCATE_INVENTORY.get()==value,"Authoritative admin policy edit did not persist");
        } finally { actor.server.getPlayerList().deop(actor.getGameProfile()); }
        require(!player.hasPermissions(2),"The prisoner gained administrator permission");
        if(actor!=player)proof.put("active_policy_admin_scope","unconfined synthetic server FakePlayer using actual ConfigEditor handler");
    }
}
