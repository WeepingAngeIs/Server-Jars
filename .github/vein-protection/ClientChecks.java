package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.UltimineClient;
import dev.unyielding.quests.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static String seen="";static String[] command;static int delay;static BlockPos target;static boolean actionSent;static int naturalWait;
    static final Path ROOT=Path.of("/tmp/uq-vein-controls");
    static Object field(Object target,String name)throws Exception{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!"true".equals(System.getenv("GITHUB_ACTIONS")))return;var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.getGameProfile().getName().equals("Dev"))return;
        try{
            if(command!=null){
                if(!command[1].equals("menu")&&mc.screen!=null)mc.setScreen(null);
                boolean natural=command[1].startsWith("natural_");
                UltimineClient.KEY.setDown(!natural||actionSent||naturalWait>0);aim(mc,target);
                boolean eligible=mc.screen==null&&UltimineClient.KEY.isDown()&&dev.unyielding.quests.ultimine.UltimineConfig.ENABLED.get()&&dev.unyielding.quests.client.ClientData.ultimineEnabled&&(!dev.unyielding.quests.client.ClientData.ultimineRequiresItem||!mc.player.getMainHandItem().isEmpty());
                if(eligible&&!natural)Net.sendToServer(new Net.UltimineState(true,(int)fieldClass(UltimineClient.class,"shape")));
                if(natural&&actionSent){boolean remaining=!mc.level.getBlockState(target).isAir();mc.options.keyAttack.setDown(remaining);if(!remaining&&delay>20)delay=20;}
                if(!actionSent){
                    String[] expected=command[7].split(",");double dx=mc.player.getX()-Double.parseDouble(expected[0]),dy=mc.player.getY()-Double.parseDouble(expected[1]),dz=mc.player.getZ()-Double.parseDouble(expected[2]);
                    String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString();boolean blocksReady=true;
                    for(String block:command[8].split(";")){String[] data=block.split("=",2),xyz=data[0].split(",");var pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals(data[1]))blocksReady=false;}
                    String status="position="+mc.player.position()+" expected="+command[7]+" item="+item+" expected="+command[5]+" mode="+mc.gameMode.getPlayerMode()+" expected="+command[6]+" blocksReady="+blocksReady+" eligible="+eligible+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName())+" key="+UltimineClient.KEY.isDown()+" localEnabled="+dev.unyielding.quests.ultimine.UltimineConfig.ENABLED.get()+" serverEnabled="+dev.unyielding.quests.client.ClientData.ultimineEnabled+" requiresItem="+dev.unyielding.quests.client.ClientData.ultimineRequiresItem;
                    Files.writeString(ROOT.resolve("Dev.status"),status);
                    if(dx*dx+dy*dy+dz*dz>.25||!item.equals(command[5])||!mc.gameMode.getPlayerMode().name().equals(command[6])||!blocksReady)return;
                    if(command[4].contains("populated")||command[1].equals("cycle")){
                        var party=com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty();
                        if(party==null||party.getSlots().stream().filter(java.util.Objects::nonNull).count()!=6)return;
                        if(!com.cobblemon.mod.common.client.gui.PartyOverlay.Companion.canRender())throw new IllegalStateException("party overlay hidden");
                    }
                    Files.writeString(ROOT.resolve("Dev.holding"),command[0]);
                    Path ready=ROOT.resolve("Dev.ready");if(!natural&&(!Files.exists(ready)||!Files.readString(ready).equals(command[0])))return;
                    if(natural){
                        if(command[1].equals("natural_warm")&&naturalWait==0){naturalWait=11;UltimineClient.KEY.setDown(true);return;}
                        if(naturalWait>1){naturalWait--;return;}
                        UltimineClient.KEY.setDown(true);mc.options.keyAttack.setDown(true);net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());actionSent=true;delay=80;return;
                    }
                    var hit=new BlockHitResult(Vec3.atCenterOf(target).add(0,.5,0),Direction.UP,target,false);
                    if(command[1].equals("mine")||command[1].equals("disabled")||command[1].equals("empty"))mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,target,Direction.UP,0));
                    if(command[1].equals("use"))mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,hit,0));
                    actionSent=true;if(command[1].equals("cycle"))delay=36;return;
                }
                if(command[1].equals("cycle")&&delay%6==0){
                    var scroll=new net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent(0,-1,false,false,false,0,0);
                    new UltimineClient.Events().onScroll(scroll);
                    int expected=(7-delay/6)%6,actual=(int)fieldClass(UltimineClient.class,"shape");
                    if(!scroll.isCanceled()||actual!=expected)throw new IllegalStateException("shape cycle expected "+expected+" got "+actual);
                }
                if(command[1].equals("cycle")&&delay%6==5){
                    int actual=(int)fieldClass(UltimineClient.class,"shape");
                    Screenshot.grab(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).toFile(),command[4]+"-shape-"+actual+".png",mc.getMainRenderTarget(),c->{});
                }
                if(--delay>0)return;String verdict="passed";
                try{String text=result(mc);Files.writeString(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).resolve(command[4]+".txt"),text+"\n");if(command[3].equals("NONE")){if(text.startsWith("Vein mining:"))throw new IllegalStateException("unexpected vein feedback: "+text);}else if(!text.contains(command[3]))throw new IllegalStateException("wrong vein feedback: "+text);
                    if(command[1].startsWith("preview")){if(!(boolean)fieldClass(UltimineClient.class,"held"))throw new IllegalStateException("held overlay inactive");if(((java.util.List<?>)fieldClass(UltimineClient.class,"preview")).size()!=3)throw new IllegalStateException("wrong preview size");}
                    if(command[1].equals("menu")&&(boolean)fieldClass(UltimineClient.class,"held"))throw new IllegalStateException("menu retained held mining state");
                    if((command[1].equals("disabled")||command[1].equals("empty"))&&(boolean)fieldClass(UltimineClient.class,"held"))throw new IllegalStateException("server-disabled overlay remained held");
                    try{int[] bounds=(int[])fieldClass(UltimineClient.class,"lastHudBounds");
                        boolean hidden=mc.options.hideGui||mc.screen!=null;
                        if(hidden&&bounds!=null)throw new IllegalStateException("vein panel rendered over hidden HUD/menu");
                        if(!hidden&&(boolean)fieldClass(UltimineClient.class,"held")&&bounds==null)throw new IllegalStateException("held vein panel missing");
                        if(bounds!=null&&(bounds[0]<70||bounds[1]<34||bounds[2]>mc.getWindow().getGuiScaledWidth()-162||bounds[3]>105))throw new IllegalStateException("vein panel intrudes on reserved startup/party HUD area");
                        Files.writeString(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).resolve(command[4]+".hud.txt"),"bounds="+java.util.Arrays.toString(bounds)+" gui="+mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight()+" shape="+fieldClass(UltimineClient.class,"shape")+" hidden="+hidden+"\n");
                    }catch(NoSuchFieldException oldBuild){}
                }catch(Throwable bad){verdict="failed: "+bad+"; hit="+mc.hitResult+" position="+mc.player.position()+" target="+mc.level.getBlockState(target)+" destroying="+field(mc.gameMode,"isDestroying");}
                Screenshot.grab(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).toFile(),command[4]+".png",mc.getMainRenderTarget(),c->{});
                Files.writeString(ROOT.resolve("Dev.ack"),command[0]+"\t"+verdict);UltimineClient.KEY.setDown(false);mc.options.keyAttack.setDown(false);if(!natural)Net.sendToServer(new Net.UltimineState(false,0));command=null;return;
            }
            Path path=ROOT.resolve("Dev.txt");if(!Files.exists(path))return;String value=Files.readString(path);if(value.equals(seen))return;seen=value;command=value.split("\t",-1);mc.setScreen(null);mc.options.hideGui=command[1].equals("preview_hidden");mc.gui.setOverlayMessage(Component.empty(),false);
            try{UltimineClient.class.getMethod("acceptFeedback",String.class).invoke(null,"");}catch(NoSuchMethodException oldBuild){}
            String[] xyz=command[2].split(",");var pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));target=pos;var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false);mc.hitResult=hit;
            boolean natural=command[1].startsWith("natural_");UltimineClient.KEY.setDown(!natural);if(!natural)Net.sendToServer(new Net.UltimineState(true,0));
            aim(mc,pos);
            if(command[1].equals("menu"))mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            delay=25;actionSent=false;naturalWait=0;
        }catch(Throwable bad){bad.printStackTrace();try{Files.writeString(ROOT.resolve("Dev.ack"),(command==null?"0":command[0])+"\tfailed: client "+bad);}catch(Exception ignored){}command=null;}
    }
    static Object fieldClass(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static String result(Minecraft mc)throws Exception{try{return (String)fieldClass(UltimineClient.class,"feedbackText");}catch(NoSuchFieldException oldBuild){return ((Component)field(mc.gui,"overlayMessageString")).getString();}}
    static void aim(Minecraft mc,BlockPos pos){var eye=mc.player.getEyePosition();double dx=pos.getX()+.5-eye.x,dy=pos.getY()+.8-eye.y,dz=pos.getZ()+.5-eye.z;mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));}
}
