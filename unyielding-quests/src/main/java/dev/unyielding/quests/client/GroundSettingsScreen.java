package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.GroundSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public final class GroundSettingsScreen extends FormScreen {
    private CompoundTag data;
    private String interval,grace,error="";
    private GroundSettingsScreen(Screen parent,CompoundTag d) { super(parent,"Ground cleanup"); data=d; interval=""+(d.getInt("interval")/20); grace=""+Math.max(60,d.getInt("grace")/20); }
    public static void receive(GroundSettings.Sync s) {
        Minecraft mc=Minecraft.getInstance();
        if(s.open()) mc.setScreen(new GroundSettingsScreen(mc.screen,s.data()));
        else if(mc.screen instanceof GroundSettingsScreen g) { g.data=s.data(); g.error=s.data().getString("message"); g.relayout(); }
    }
    private void send(String k,int v) { Net.sendToServer(new GroundSettings.Action(k,v)); }
    private void numeric(String key,String raw) { try { send(key,Integer.parseInt(raw)); } catch(NumberFormatException e) { error="Enter a whole number of seconds."; relayout(); } }
    private static String time(int ticks) { int seconds=Math.max(0,ticks)/20; return (seconds/60)+" min "+(seconds%60)+" sec"; }
    @Override protected void build() {
        note("Applies to loaded items in Overworld, Nether, and End.");
        note("Next run: "+time(data.getInt("interval")-data.getInt("timer"))+(data.getInt("enabled")==0?" (paused)":"")); gap(5);
        boolean active=data.getInt("enabled")==1;
        toggle(left(),innerWidth(),"Automatic cleanup",active,v->send("enabled",v?1:0)); gap(25);
        note("Interval: "+time(data.getInt("interval"))+". Enter seconds:");
        field("interval",left(),innerWidth()-65,interval,"Seconds",v->interval=v);
        button(right()-60,60,"Set",null,Ui.Style.NORMAL,()->numeric("interval",interval)); gap(25);
        int w=(innerWidth()-8)/3;
        button(left(),w,"5 min",null,Ui.Style.NORMAL,()->send("interval",300));
        button(left()+w+4,w,"10 min",null,Ui.Style.NORMAL,()->send("interval",600));
        button(left()+2*w+8,w,"20 min",null,Ui.Style.NORMAL,()->send("interval",1200)); gap(25);
        note("Fresh-drop grace in seconds (at least 60):");
        field("grace",left(),innerWidth()-65,grace,"Seconds",v->grace=v);
        button(right()-60,60,"Set",null,Ui.Style.NORMAL,()->numeric("grace",grace)); gap(27);
        for(String key:new String[]{"gear","food","other"}) {
            boolean on=data.getInt(key)==1;
            String label=key.equals("other")?"Other items and mob drops":key.equals("gear")?"Tools, weapons and gear":"Food";
            toggle(left(),innerWidth(),label,on,v->send("clear_"+key,v?1:0)); gap(24);
        }
        heading("Affected dimensions");
        for(String dim:new String[]{"overworld","nether","end","custom"}) {
            String title=dim.equals("custom")?"Custom dimension overrides":dim.substring(0,1).toUpperCase()+dim.substring(1);
            toggle(left(),innerWidth(),title,data.getInt("dim_"+dim)==1,v->send("dim_"+dim,v?1:0)); gap(24);
        }
        note("Manual clear covers selected vanilla dimensions only.");
        note("Custom dimension overrides run on automatic cleanup."); gap(6);
        if(data.getBoolean("preview")) {
            note("Preview: "+data.getInt("preview_stacks")+" stacks, "+data.getInt("preview_items")+" individual items.");
            note("Fresh drops are protected. Counts may change.");
            button(left(),(innerWidth()-4)/2,"Confirm clear",null,Ui.Style.ACCENT,()->send("confirm",0));
            button(left()+(innerWidth()+4)/2,(innerWidth()-4)/2,"Cancel clear",null,Ui.Style.NORMAL,()->send("cancel_preview",0)); gap(25);
        } else {
            button(left(),innerWidth(),"Preview manual clear",null,Ui.Style.NORMAL,()->send("preview",0)); gap(25);
        }
        note("Changes apply when selected. One-minute and five-second");
        note("chat warnings appear when the interval permits."); gap(5);
        note(font.plainSubstrByWidth(error,innerWidth())); gap(4);
        button(left(),innerWidth(),"Refresh countdown",null,Ui.Style.NORMAL,()->send("refresh",0)); gap(25);
    }
    @Override protected String saveLabel(){return "Done";}
    @Override protected void save(){cancel();}
}
