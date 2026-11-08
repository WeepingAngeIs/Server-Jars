package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.settings.MusicSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

final class MusicEditorSmoke {
    static void edit(ServerPlayer player,String track,double percent) {
        MusicSettings.handle(player,new MusicSettings.Action(track,MusicSettings.get(player).snapshot().getLong("revision"),percent));
    }
    static void exercise(ServerPlayer player,Map<String,Object> report)throws Exception {
        var players=player.server.getPlayerList(); boolean wasOp=players.isOp(player.getGameProfile());
        MusicSettings state=MusicSettings.get(player); CompoundTag original=state.snapshot();
        try {
            require(!MusicSettings.TRACKS.isEmpty()&&MusicSettings.TRACKS.contains("lavender"),"Song catalog lacks Lavender Town");
            require(MusicSettings.TRACKS.stream().distinct().count()==MusicSettings.TRACKS.size(),"Song catalog duplicates tracks");
            players.deop(player.getGameProfile());reject(player,MusicSettings.MASTER,75);
            reject(player,"lavender",50);
            players.op(player.getGameProfile());
            for(double bad:new double[]{-1,201,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                reject(player,MusicSettings.MASTER,bad);reject(player,"lavender",bad);
            }
            reject(player,"missing_track",50);reject(player,"../lavender",50);
            long stale=state.snapshot().getLong("revision");
            edit(player,MusicSettings.MASTER,75);edit(player,"lavender",50);
            CompoundTag changed=state.snapshot();
            require(changed.getDouble("master")==75&&changed.getCompound("volumes").getDouble("lavender")==50,"Music edits not applied");
            MusicSettings.handle(player,new MusicSettings.Action("lavender",stale,20));
            require(state.snapshot().equals(changed),"Stale music edit accepted");
            player.server.overworld().getDataStorage().save();
            var path=player.server.getWorldPath(LevelResource.ROOT).resolve("data/unyielding_music.dat");
            require(Files.isRegularFile(path),"World music settings not saved to disk");
            var disk=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getCompound("data");
            require(MusicSettings.load(disk,player.registryAccess()).snapshot().equals(changed),"Music settings did not survive disk serialization and reload");
            edit(player,"lavender",0);require(state.snapshot().getCompound("volumes").getDouble("lavender")==0,"Track mute rejected");
            edit(player,"lavender",200);require(state.snapshot().getCompound("volumes").getDouble("lavender")==200,"Track maximum rejected");
            edit(player,"lavender",100);require(!state.snapshot().getCompound("volumes").contains("lavender"),"Normal volume should clear per-track override");
            CompoundTag corrupt=new CompoundTag();corrupt.putDouble("master",Double.NaN);CompoundTag entries=new CompoundTag();
            entries.putDouble("lavender",Double.POSITIVE_INFINITY);entries.putDouble("missing_track",50);corrupt.put("volumes",entries);
            var clean=MusicSettings.load(corrupt,null).snapshot();
            require(clean.getDouble("master")==100&&clean.getCompound("volumes").isEmpty(),"Invalid saved music data was trusted");
            players.deop(player.getGameProfile());reject(player,MusicSettings.MASTER,0);
            report.put("music_operator_stale_catalog_finite_range_and_mute_controls","passed");
            report.put("music_world_file_serialization_and_reload","passed");
        }finally {
            restore(state,original);player.server.overworld().getDataStorage().save();
            if(wasOp)players.op(player.getGameProfile());else players.deop(player.getGameProfile());
        }
    }
    @SuppressWarnings("unchecked") static void restore(MusicSettings state,CompoundTag original)throws Exception {
        var loaded=MusicSettings.load(original,null);
        for(String name:new String[]{"revision","master"}) {var f=MusicSettings.class.getDeclaredField(name);f.setAccessible(true);f.set(state,f.get(loaded));}
        var f=MusicSettings.class.getDeclaredField("volumes");f.setAccessible(true);
        var values=(Map<String,Double>)f.get(state);values.clear();values.putAll((Map<String,Double>)f.get(loaded));state.setDirty();
    }
    private static void reject(ServerPlayer player,String track,double percent) {
        var before=MusicSettings.get(player).snapshot();edit(player,track,percent);
        require(MusicSettings.get(player).snapshot().equals(before),"Rejected music edit changed state: "+track+" "+percent);
    }
}
