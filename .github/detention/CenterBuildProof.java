package dev.unyielding.bundlesmoke;

import dev.unyielding.jail.JailData;
import dev.unyielding.jail.JailPlacement;
import dev.unyielding.jail.Loc;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Synthetic flat site in a disposable integration-test world, entered only from the native center test. */
public final class CenterBuildProof {
    private static int staff(ServerPlayer admin, String command) throws Exception {
        return admin.server.getCommands().getDispatcher().execute(command, admin.createCommandSourceStack());
    }
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException("Rosecourt placement proof: " + reason);
    }
    private static String blockId(ServerLevel level, BlockPos p) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
    }
    public static void place(ServerPlayer admin, ServerPlayer visitor) throws Exception {
        var level = admin.serverLevel();
        level.getServer().getPlayerList().op(admin.getGameProfile());
        // The native harness uses a private temporary superflat world. Building a platform above spawn
        // keeps placement deterministic without depending on player proximity to generated terrain.
        BlockPos min = new BlockPos(admin.getBlockX()-9, Math.min(level.getMaxBuildHeight()-20, admin.getBlockY()+20), admin.getBlockZ()+4);
        for (int x=min.getX() >> 4; x <= (min.getX()+19) >> 4; x++)
            for (int z=(min.getZ()-5) >> 4; z <= (min.getZ()+30) >> 4; z++) level.getChunk(x,z);
        require(level.getEntities((Entity)null, new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+19,min.getY()+10,min.getZ()+30)).isEmpty(),"fixture volume occupied");
        for (int x=0; x<19; x++) for (int z=-5; z<30; z++) {
            for (int y=-1; y<10; y++) require(level.getBlockState(min.offset(x,y,z)).isAir(),"expected empty fixture volume");
            level.setBlock(min.offset(x,-1,z), Blocks.STONE.defaultBlockState(),3);
        }
        admin.teleportTo(level,min.getX()+9.5,min.getY(),min.getZ()-3.5,0,0);
        visitor.teleportTo(level,min.getX()+13.5,min.getY(),min.getZ()-3.5,0,0);
        var data=JailData.get(admin.server); var prior=data.location(); int priorGeometry=data.geometry();
        level.setBlock(min,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        require(staff(admin,"jailbuild preview")==0,"gold accepted for replacement");
        level.setBlock(min,Blocks.ANDESITE.defaultBlockState(),3);
        require(staff(admin,"jailbuild preview")==1,"initial preview refused");
        level.setBlock(min,Blocks.DIORITE.defaultBlockState(),3);
        require(staff(admin,"jailbuild place")==0 && level.getBlockState(min).is(Blocks.DIORITE),"stale preview applied");
        level.setBlock(min,Blocks.ANDESITE.defaultBlockState(),3);
        require(staff(admin,"jailbuild preview")==1 && staff(admin,"jailbuild details 1")==1 && staff(admin,"jailbuild place")==1,"verified placement refused");
        checkCells(admin,min);
        var original=level.getBlockState(min);
        level.setBlock(min,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        require(staff(admin,"jailbuild undo")==0,"undo accepted a third-party edit");
        require(!JailPlacement.intact(admin.server,data.location(),4),"damaged cell passes integrity check");
        require(JailPlacement.intact(admin.server,cell(data.location(),6),4),"undamaged cell affected by damage to cell 1");
        level.setBlock(min,original,3);
        require(staff(admin,"jailbuild undo")==1 && staff(admin,"jailbuild undo-confirm")==1,"undo refused");
        require(java.util.Objects.equals(prior,data.location()) && priorGeometry==data.geometry(),"prior geometry/anchor lost");
        for (BlockPos p:BlockPos.betweenClosed(min,min.offset(18,9,29)))
            require(p.equals(min)?level.getBlockState(p).is(Blocks.ANDESITE):level.getBlockState(p).isAir(),"incomplete terrain restoration at "+p);
        require(staff(admin,"jailbuild preview")==1 && staff(admin,"jailbuild place")==1,"second placement refused");
        checkCells(admin,min);
    }
    private static Loc cell(Loc first,int n) {
        return new Loc(first.dim(),first.x()+(n%2==1?0:12),first.y(),first.z()+6*((n-1)/2),first.yaw(),first.pitch());
    }
    private static void checkCells(ServerPlayer admin,BlockPos min) {
        var level=admin.serverLevel(); var data=JailData.get(admin.server);
        require(data.geometry()==4 && data.location().equals(Loc.of(level,min.getX()+3.5,min.getY()+1,min.getZ()+3.5,0,0)),"wrong installed facility");
        var palette = new HashSet<String>();
        for (BlockPos pos:BlockPos.betweenClosed(min,min.offset(18,9,29))) {
            String id=blockId(level,pos);
            if(id.startsWith("minecraftjail:")) palette.add(id.substring("minecraftjail:".length()));
        }
        require(palette.equals(Set.of("pearl_ashlar","smoked_chestnut","rose_inlay","petal_lantern","ceramic_blossom","ward_grille",
                "cell_plate_1","cell_plate_2","cell_plate_3","cell_plate_4","cell_plate_5","cell_plate_6")),"incomplete physical block palette: "+palette);
        for(int n=1;n<=6;n++){
            boolean west=n%2==1; int z=3+6*((n-1)/2);
            require(blockId(level,min.offset(west?6:12,4,z)).equals("minecraftjail:cell_plate_"+n),"wrong plaque "+n);
            require(blockId(level,min.offset(west?7:11,1,z)).equals("unyieldingquests:custody_vault"),"missing locker "+n);
            require(JailPlacement.intact(admin.server,cell(data.location(),n),4),"cell invalid "+n);
        }
    }
    private CenterBuildProof() { }
}
