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
    private String intervalMinutes, intervalSeconds, graceMinutes, graceSeconds, error="";
    private int refreshTicks;
    private GroundSettingsScreen(Screen parent,CompoundTag d) {
        super(parent,"Ground cleanup"); data=d;
        setFields("interval",Math.max(0,d.getInt("interval")/20));
        setFields("grace",Math.max(60,d.getInt("grace")/20));
    }
    public static void receive(GroundSettings.Sync s) {
        Minecraft mc=Minecraft.getInstance();
        if(s.open()) mc.setScreen(new GroundSettingsScreen(mc.screen,s.data()));
        else if(mc.screen instanceof GroundSettingsScreen g) { g.data=s.data(); if (!s.data().getString("message").isEmpty()) g.error=s.data().getString("message"); g.relayout(); }
    }
    @Override public void tick() {
        super.tick();
        if (++refreshTicks >= 20) { refreshTicks=0; send("refresh",0); }
    }
    private void send(String k,int v) { Net.sendToServer(new GroundSettings.Action(k,v)); }
    private void setFields(String key,int seconds) {
        if(key.equals("interval")) { intervalMinutes=""+(seconds/60); intervalSeconds=""+(seconds%60); }
        else { graceMinutes=""+(seconds/60); graceSeconds=""+(seconds%60); }
    }
    private void duration(String key,String minutes,String seconds) {
        try {
            long m=Long.parseLong(minutes), s=Long.parseLong(seconds);
            long total=Math.addExact(Math.multiplyExact(m,60),s);
            long min=key.equals("grace")?60:5, max=key.equals("grace")?1600:86400;
            if(m<0 || s<0 || s>59 || total<min || total>max) throw new NumberFormatException();
            send(key,(int)total);
        } catch(NumberFormatException | ArithmeticException e) { error="Enter valid minutes and seconds (0–59)."; relayout(); }
    }
    private void adjust(String key,int seconds) {
        int min=key.equals("grace")?60:5, max=key.equals("grace")?1600:86400;
        int value=Math.max(min,Math.min(max,seconds)); setFields(key,value); send(key,value);
    }
    private static String time(int ticks) { int seconds=Math.max(0,ticks)/20; return (seconds/60)+" min "+(seconds%60)+" sec"; }
    @Override protected void build() {
        note("Applies to loaded items in Overworld, Nether, and End.");
        note("Next run: "+time(data.getInt("interval")-data.getInt("timer"))+(data.getInt("enabled")==0?" (paused)":"")); gap(5);
        boolean active=data.getInt("enabled")==1;
        toggle(left(),innerWidth(),"Automatic cleanup",active,v->send("enabled",v?1:0)); gap(25);
        note("Interval: "+time(data.getInt("interval"))+". Minutes / seconds:");
        int fieldWidth=(innerWidth()-68)/2;
        field("intervalMinutes",left(),fieldWidth,intervalMinutes,"Minutes",v->intervalMinutes=v);
        field("intervalSeconds",left()+fieldWidth+4,fieldWidth,intervalSeconds,"Seconds",v->intervalSeconds=v);
        button(right()-60,60,"Set",null,Ui.Style.NORMAL,()->duration("interval",intervalMinutes,intervalSeconds)); gap(25);
        int half=(innerWidth()-4)/2;
        button(left(),half,"< 1 minute",null,Ui.Style.NORMAL,()->adjust("interval",data.getInt("interval")/20-60));
        button(left()+half+4,half,"1 minute >",null,Ui.Style.NORMAL,()->adjust("interval",data.getInt("interval")/20+60)); gap(25);
        int w=(innerWidth()-8)/3;
        button(left(),w,"5 min",null,Ui.Style.NORMAL,()->adjust("interval",300));
        button(left()+w+4,w,"10 min",null,Ui.Style.NORMAL,()->adjust("interval",600));
        button(left()+2*w+8,w,"20 min",null,Ui.Style.NORMAL,()->adjust("interval",1200)); gap(25);
        note("Fresh-drop grace: "+time(data.getInt("grace"))+" (minimum 1 min)");
        field("graceMinutes",left(),fieldWidth,graceMinutes,"Minutes",v->graceMinutes=v);
        field("graceSeconds",left()+fieldWidth+4,fieldWidth,graceSeconds,"Seconds",v->graceSeconds=v);
        button(right()-60,60,"Set",null,Ui.Style.NORMAL,()->duration("grace",graceMinutes,graceSeconds)); gap(25);
        button(left(),half,"< 1 minute",null,Ui.Style.NORMAL,()->adjust("grace",data.getInt("grace")/20-60));
        button(left()+half+4,half,"1 minute >",null,Ui.Style.NORMAL,()->adjust("grace",data.getInt("grace")/20+60)); gap(27);
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
