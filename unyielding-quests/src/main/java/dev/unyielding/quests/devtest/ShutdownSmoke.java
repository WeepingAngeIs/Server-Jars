package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only disposable harness drain barrier; never packaged in the distributable. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class ShutdownSmoke {
    static final Path READY=Path.of("/tmp/uq-smoke-shutdown-ready");
    static boolean armed;static int settled,ticks;
    static boolean hosted(){return "true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"));}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        if(!hosted())return;
        event.getDispatcher().register(Commands.literal("uqsmoke_shutdown_ready").requires(s->s.getEntity()==null&&s.hasPermission(4)).executes(c->{
            try{Files.deleteIfExists(READY);armed=true;settled=ticks=0;return 1;}catch(java.io.IOException bad){throw new IllegalStateException(bad);}
        }));
    }
    static Object field(ChunkMap map,String name)throws ReflectiveOperationException {var f=ChunkMap.class.getDeclaredField(name);f.setAccessible(true);return f.get(map);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!armed||!hosted())return;
        try{
            int generation=0,unsaved=0,tasks=0,holders=0;
            for(var level:event.getServer().getAllLevels()){
                var map=level.getChunkSource().chunkMap;Set<ChunkHolder> all=Collections.newSetFromMap(new IdentityHashMap<>());
                for(String name:List.of("updatingChunkMap","visibleChunkMap","pendingUnloads"))for(Object value:((Map<?,?>)field(map,name)).values())all.add((ChunkHolder)value);
                tasks+=((List<?>)field(map,"pendingGenerationTasks")).size();holders+=all.size();
                for(var holder:all){generation+=holder.getGenerationRefCount();if(!holder.isReadyForSaving())unsaved++;}
            }
            if(event.getServer().getPlayerCount()==0&&generation==0&&unsaved==0&&tasks==0)settled++;else settled=0;
            if(++ticks%20==0)UnyieldingQuests.LOGGER.info("Disposable shutdown readiness: players={} holders={} generationRefs={} unsaved={} tasks={} stableTicks={}",event.getServer().getPlayerCount(),holders,generation,unsaved,tasks,settled);
            if(settled>=20){Files.writeString(READY,"ready: native generation references and save futures settled for 20 ticks, no players\n");armed=false;}
        }catch(Exception bad){armed=false;UnyieldingQuests.LOGGER.error("Disposable shutdown readiness could not be verified",bad);}
    }
}
