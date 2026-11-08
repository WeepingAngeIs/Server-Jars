package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.BalanceSettingsScreen;
import dev.unyielding.quests.client.ConfigValuesScreen;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Arrays;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

final class BalanceUiSmoke {
    static void exercise(Minecraft mc,Map<String,Object> report)throws Exception {
        Screen previous=mc.screen; int permission=mc.player.getPermissionLevel();
        try {
            mc.player.setPermissionLevel(2);
            for(boolean raid:new boolean[]{false,true}) for(int[] size:new int[][]{{640,400},{320,240}}) {
                CompoundTag data=fixture(); data.putString("page",raid?"raid":"armor");
                ConfigValuesScreen.receive(new ConfigEditor.Sync(data,true));
                require(mc.screen instanceof BalanceSettingsScreen,"Balance server response opened wrong screen");
                BalanceSettingsScreen screen=new BalanceSettingsScreen(null,data,raid); mc.setScreen(screen); draw(mc,screen,size);
                require(fields(screen).length==3,"Balance page requires three percentage inputs");
                if(!raid) {
                    require(Arrays.equals(values(screen),new String[]{"50","50","100"}),"Armor displayed wrong percentages");
                    press(screen,"Normal armor preset");
                    require(Arrays.equals(values(screen),new String[]{"100","100","100"}),"Normal armor preset incorrect");
                    press(screen,"Half armor preset");
                    require(Arrays.equals(values(screen),new String[]{"50","50","100"}),"Half armor preset incorrect");
                    fields(screen)[0].setValue("301"); press(screen,"Save"); assertNotPending(screen);
                }else {
                    require(Arrays.equals(values(screen),new String[]{"3","1","0"}),"Raid tier one displayed wrong odds");
                    fields(screen)[0].setValue("10.25"); press(screen,"7");
                    require(Arrays.equals(values(screen),new String[]{"0","25","0"}),"Tier seven uses wrong odds");
                    press(screen,"1");require(values(screen)[0].equals("10.25"),"Switching raid tier lost draft");
                    screen.receive(fixture(),false); require(values(screen)[0].equals("10.25"),"Server sync overwrote unsaved draft");
                    fields(screen)[0].setValue("100");fields(screen)[1].setValue("1");press(screen,"Save");assertNotPending(screen);
                }
                draw(mc,screen,size);
                for(var child:screen.children()) if(child instanceof AbstractWidget w)
                    require(w.getX()>=0&&w.getX()+w.getWidth()<=size[0],"Balance control exceeds narrow screen");
                AdminSettingsScreen admin=new AdminSettingsScreen(null);draw(mc,admin,size);
                require(has(admin,"Armor Balance")&&has(admin,"Raid IV odds"),"Admin balance shortcuts missing");
            }
            report.put("armor_raid_screens_presets_independent_drafts_invalid_save_and_server_routing","passed");
            report.put("armor_raid_admin_controls_render_at_640x400_and_320x240","passed");
        }finally{mc.player.setPermissionLevel(permission);mc.setScreen(previous);}
    }
    private static void assertNotPending(BalanceSettingsScreen screen)throws Exception {
        var f=BalanceSettingsScreen.class.getDeclaredField("pendingGroup");f.setAccessible(true);
        require(f.getInt(screen)==-1,"Invalid balance draft was submitted");
    }
    private static CompoundTag fixture() {
        CompoundTag data=new CompoundTag();data.putString("armor_values","[50,50,100]");
        ListTag tiers=new ListTag();for(int i=1;i<=7;i++){CompoundTag t=new CompoundTag();t.putInt("tier",i);t.putString("value",i==7?"[0,25,0]":"[3,1,0]");t.putDouble("none",i==7?75:96);tiers.add(t);}data.put("raid_tiers",tiers);
        ListTag rows=new ListTag();for(String[] pair:new String[][]{{"raid_ivs.enabled","true"},{"raid_ivs.normal_iv_max","30"},{"armor.mod_overrides","[]"},{"armor.item_overrides","[]"}}){CompoundTag row=new CompoundTag();row.putString("key",pair[0]);row.putString("value",pair[1]);rows.add(row);}data.put("rows",rows);return data;
    }
    private static EditBox[] fields(Screen s){return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toArray(EditBox[]::new);}
    private static String[] values(Screen s){return Arrays.stream(fields(s)).map(EditBox::getValue).toArray(String[]::new);}
    private static boolean has(Screen s,String text){return s.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().equals(text));}
    private static void press(Screen s,String text){s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(text)||b.getMessage().getString().equals(text+"*")).findFirst().orElseThrow(()->new IllegalStateException("Missing balance button "+text)).onPress();}
    private static void draw(Minecraft mc,Screen s,int[] size){s.init(mc,size[0],size[1]);GuiGraphics g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());s.render(g,-1000,-1000,0f);g.flush();}
}
