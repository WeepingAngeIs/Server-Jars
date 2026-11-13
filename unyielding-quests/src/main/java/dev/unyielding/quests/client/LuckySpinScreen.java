package dev.unyielding.quests.client;

import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Horizontal prize reel. The server pays once when the spin is accepted;
 * closing the animation cannot lose or repeat the reward. */
public final class LuckySpinScreen extends Screen {
    private final Screen parent;
    private CompoundTag state=new CompoundTag();
    private LuckySpin.Spin result;
    private long started;
    private boolean resultSoundPlayed;
    private boolean waiting=true;
    private Button spin;
    public LuckySpinScreen(Screen parent){super(Component.literal("Lucky Spin"));this.parent=parent;}
    @Override protected void init(){
        spin=addRenderableWidget(Button.builder(Component.literal("Spin"),b->requestSpin()).bounds(width/2-100,height/2+64,96,20).build());
        addRenderableWidget(Button.builder(Component.literal("Quest spins"),b->ClientInit.openQuests()).bounds(width/2+4,height/2+64,96,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2-50,height/2+90,100,20).build());
        CompoundTag tag=new CompoundTag();tag.putString("action","status");if(minecraft.getConnection()!=null) Net.sendToServer(new Net.SpinMenuAction(tag));
        updateButton();
    }
    private boolean animating(){return result!=null && Util.getMillis()-started<4500;}
    private void updateButton(){if(spin!=null)spin.active=!waiting && !animating() && state.getBoolean("ready") && state.getBoolean("enabled") && state.getLong("count")>0;}
    private void requestSpin(){
        if(!spin.active)return;
        waiting=true;result=null;resultSoundPlayed=false;updateButton();
        CompoundTag tag=new CompoundTag();tag.putString("action","spin");tag.putString("token",state.getString("token"));Net.sendToServer(new Net.SpinMenuAction(tag));
    }
    public static void receive(CompoundTag tag){
        if(Minecraft.getInstance().screen instanceof LuckySpinScreen screen){screen.state=tag.copy();screen.waiting=false;screen.updateButton();}
    }
    public void receiveSpins(List<LuckySpin.Spin> spins){
        if(spins.isEmpty())return;
        result=spins.get(0);started=Util.getMillis();resultSoundPlayed=false;updateButton();
    }
    @Override public void tick(){
        if(result!=null && !animating() && !resultSoundPlayed){
            resultSoundPlayed=true;
            LuckySpinSounds.play(result.tier());
        }
        updateButton();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);
        int w=Math.min(360,width-16),x=(width-w)/2,y=height/2-70;
        g.fill(x,y,x+w,y+126,0xEE151925);
        g.drawCenteredString(font,title,width/2,y+10,0xFFFFCE40);
        String status=waiting?"Loading...":!state.getBoolean("ready")?"Choose your starter with H first.":!state.getBoolean("enabled")?"Lucky Spin is disabled.":"Saved spins: "+state.getLong("count");
        g.drawCenteredString(font,status,width/2,y+28,0xFFFFFFFF);
        int top=y+50;
        g.enableScissor(x+8,top,x+w-8,top+40);
        if(result!=null){
            double t=Math.min(1,(Util.getMillis()-started)/4500.0);
            double offset=result.index()*(1-Math.pow(1-t,5));
            for(int i=0;i<result.strip().size();i++){
                int sx=(int)Math.round(width/2.0+(i-offset)*38-16);
                if(sx<x-40 || sx>x+w)continue;
                var entry=result.strip().get(i);
                g.fill(sx,top+2,sx+32,top+34,entry.tier().color);
                g.fill(sx+1,top+3,sx+31,top+33,0xFF252C39);
                g.renderItem(entry.stack(),sx+8,top+10);
            }
        }else g.drawCenteredString(font,"Press Spin to reveal your reward",width/2,top+14,0xFFB8C0D0);
        g.disableScissor();
        g.fill(width/2-1,top-3,width/2+1,top+38,0xFFFFCE40);
        if(result!=null)g.drawCenteredString(font,animating()?"Spinning...":font.plainSubstrByWidth(result.name(),w-16),width/2,y+103,animating()?0xFFFFFFFF:result.tier().color);
        for(var widget:renderables) widget.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
