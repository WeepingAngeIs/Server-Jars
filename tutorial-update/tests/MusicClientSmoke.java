package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.MusicClient;
import dev.unyielding.quests.client.MusicAdminScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

final class MusicClientSmoke {
    static boolean received() {return Math.abs(MusicClient.adjustGain(-6,"lavender")-(-6+20*Math.log10(.375)))<.0001;}
    static void exercise(Minecraft mc,Map<String,Object> report)throws Exception {
        var stateField=MusicClient.class.getDeclaredField("state");stateField.setAccessible(true);
        CompoundTag original=((CompoundTag)stateField.get(null)).copy();
        try {
            MusicClient.clear();close(MusicClient.adjustGain(-6,"lavender"),-6,"Disconnect reset");
            MusicClient.apply(settings(100,100));close(MusicClient.adjustGain(-6,"lavender"),-6,"Default level");
            MusicClient.apply(settings(50,50));close(MusicClient.adjustGain(-6,"lavender"),-6+20*Math.log10(.25),"Combined multipliers");
            close(MusicClient.adjustGain(-6,"another_track"),-6+20*Math.log10(.5),"Unspecified track default");
            MusicClient.apply(settings(200,200));close(MusicClient.adjustGain(0,"lavender"),6,"Positive gain clamp");
            close(MusicClient.adjustGain(-80,"lavender"),-80,"Personal mute");
            close(MusicClient.adjustGain(-50,"lavender"),-50,"Fade endpoint");
            MusicClient.apply(settings(0,100));close(MusicClient.adjustGain(-6,"lavender"),-80,"Overall mute");
            MusicClient.apply(settings(100,0));close(MusicClient.adjustGain(-6,"lavender"),-80,"Track mute");
            require(Float.isNaN(MusicClient.adjustGain(Float.NaN,"lavender")),"Invalid source gain changed");
            transformedReactiveMusic(report);
            ui(mc,report);
            report.put("music_live_server_sync_math_mute_fade_gain_clamp_and_disconnect_reset","passed");
        }finally{MusicClient.apply(original);}
    }
    private static void transformedReactiveMusic(Map<String,Object> report)throws Exception {
        Class<?> music=Class.forName("circuitlord.reactivemusic.ReactiveMusic"), thread=Class.forName("circuitlord.reactivemusic.PlayerThread");
        Class<?> audio=Class.forName("rm_javazoom.jl.player.JavaSoundAudioDevice");
        Method handler=Arrays.stream(audio.getDeclaredMethods()).filter(m->m.getName().contains("unyielding$musicVolume")).findFirst().orElseThrow(()->new IllegalStateException("Reactive Music audio device did not receive the production mixin"));
        require(handler.getParameterCount()==1&&handler.getParameterTypes()[0]==float.class,"Unexpected music injection signature");handler.setAccessible(true);
        var packField=music.getField("currentSongpack");var songField=thread.getField("currentSong");Object oldPack=packField.get(null),oldSong=songField.get(null);
        try {
            Object pack=allocate(packField.getType());var configField=pack.getClass().getField("config");Object config=allocate(configField.getType());
            var name=config.getClass().getField("name");name.set(config,"Cobblemon Unyielding");configField.set(pack,config);packField.set(null,pack);songField.set(null,"lavender");
            MusicClient.apply(settings(50,50));Object audioDevice=allocate(audio);
            var source=audio.getDeclaredField("source");source.setAccessible(true);
            var setGain=audio.getMethod("setGain",float.class);
            ProbeGain gain=probe(source,audioDevice,-80,6);
            setGain.invoke(audioDevice,-6f);close(gain.getValue(),-6+20*Math.log10(.25),"Actual transformed Reactive Music setGain");
            name.set(config,"Another songpack");setGain.invoke(audioDevice,-6f);close(gain.getValue(),-6,"Unrelated songpack preserved");
            name.set(config,"Cobblemon Unyielding");
            gain=probe(source,audioDevice,-50,0);MusicClient.apply(settings(200,200));setGain.invoke(audioDevice,-6f);close(gain.getValue(),0,"Physical device upper gain limit");
            MusicClient.apply(settings(0,100));setGain.invoke(audioDevice,-6f);close(gain.getValue(),-50,"Physical device mute limit");
            report.put("real_reactive_music_1_3_5_transformed_set_gain_and_device_limits","passed");
        }finally{packField.set(null,oldPack);songField.set(null,oldSong);}
    }
    private static Object allocate(Class<?> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
    }
    private static final class ProbeGain extends FloatControl {
        ProbeGain(float min,float max){super(FloatControl.Type.MASTER_GAIN,min,max,.01f,0,0,"dB");}
    }
    private static ProbeGain probe(java.lang.reflect.Field field,Object audio,float min,float max)throws Exception {
        ProbeGain gain=new ProbeGain(min,max);
        Object line=Proxy.newProxyInstance(MusicClientSmoke.class.getClassLoader(),new Class<?>[]{SourceDataLine.class},(proxy,method,args)-> {
            if(method.getName().equals("getControl"))return gain;
            if(method.getName().equals("isControlSupported"))return true;
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            if(method.getReturnType()==long.class)return 0L;
            if(method.getReturnType()==float.class)return 0f;
            return null;
        });field.set(audio,line);return gain;
    }
    private static void ui(Minecraft mc,Map<String,Object> report)throws Exception {
        Screen previous=mc.screen;int permission=mc.player.getPermissionLevel();
        try {
            mc.player.setPermissionLevel(2);
            for(int[] size:new int[][]{{640,400},{320,240}}) {
                var list=new MusicAdminScreen(null,settings(100,100),"");mc.setScreen(list);draw(mc,list,size);
                EditBox search=list.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("Lavender Town");draw(mc,list,size);
                Button lavender=list.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().startsWith("Lavender Town  ")).findFirst().orElseThrow();
                lavender.onPress();require(mc.screen instanceof MusicAdminScreen.TrackScreen,"Music track button did not open editor");
                Screen track=mc.screen;draw(mc,track,size);press(track,"Half");
                require(input(track).getValue().equals("50"),"Music half preset failed");press(track,"Mute");require(input(track).getValue().equals("0"),"Music mute preset failed");
                input(track).setValue("201");press(track,"Save");
                var pending=MusicAdminScreen.TrackScreen.class.getDeclaredField("pending");pending.setAccessible(true);require(!pending.getBoolean(track),"Invalid music volume was submitted");
                for(var child:track.children())if(child instanceof AbstractWidget w)require(w.getX()>=0&&w.getX()+w.getWidth()<=size[0],"Music control exceeds screen");
                draw(mc,track,size);
            }
            report.put("music_mixer_search_presets_validation_and_640x400_320x240_layout","passed");
        }finally{mc.player.setPermissionLevel(permission);mc.setScreen(previous);}
    }
    private static EditBox input(Screen screen){return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();}
    private static void press(Screen screen,String text){screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow().onPress();}
    private static void draw(Minecraft mc,Screen s,int[] size){s.init(mc,size[0],size[1]);GuiGraphics g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());s.render(g,-1000,-1000,0);g.flush();}
    private static CompoundTag settings(double master,double lavender){CompoundTag s=new CompoundTag();s.putDouble("master",master);CompoundTag tracks=new CompoundTag();tracks.putDouble("lavender",lavender);s.put("volumes",tracks);return s;}
    private static void close(double actual,double expected,String label){require(Math.abs(actual-expected)<.0001,label+" mismatch: "+actual+" expected "+expected);}
}
