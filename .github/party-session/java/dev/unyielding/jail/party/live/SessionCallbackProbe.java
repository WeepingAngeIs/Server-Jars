package dev.unyielding.jail.party.live;
import com.cobblemon.mod.common.CobblemonCallbacks;
import com.cobblemon.mod.common.platform.events.ServerPlayerTickEvent;
import dev.unyielding.jail.party.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** One matched same-tick native callback experiment; no authority is granted. */
public final class SessionCallbackProbe {
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static String baselineHash;
    private static long gameTime;
    private static void save()throws Exception {
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/session-callback-contract.json"),SessionBridge.GSON.toJson(report));
    }
    private static void run(ServerPlayer p){
        CobblemonCallbacks.INSTANCE.run(ResourceLocation.parse("cobblemon:player_tick_pre"),
            new ServerPlayerTickEvent.Pre(p).getContext(),Map.of(),null);
    }
    private static String hash(ServerPlayer p)throws Exception {
        var m=GlobalProfile.manager();
        return NativeFence.hash(new CobblemonSnapshotCodec(p.registryAccess()).capture(p.getUUID(),
            HandoffCoordinator.providerIdentity(GlobalProfile.adapter()),m.getParty(p),m.getPC(p)));
    }
    public static void baseline(ServerPlayer p)throws Exception {
        gameTime=p.serverLevel().getGameTime();
        SessionBridge.require(gameTime%20==0,"Callback fixture must run on native wallpaper-check tick");
        report.put("commit",System.getenv("GITHUB_SHA"));report.put("game_time",gameTime);
        report.put("biome",p.serverLevel().getBiome(p.blockPosition()).unwrapKey().map(Object::toString).orElse("unkeyed"));
        report.put("production_changed",false);report.put("compatibility_passed",false);
        var resources=new TreeMap<String,Object>();
        for(var e:p.server.getResourceManager().listResources("callbacks/player_tick_pre",x->x.getPath().endsWith(".molang")).entrySet()){
            try(var in=e.getValue().open()){
                resources.put(e.getKey().toString(),Map.of("pack",e.getValue().sourcePackId(),
                    "sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))));
            }
        }
        report.put("active_callback_resources",resources);
        String before=hash(p);report.put("baseline_before",before);save();
        try{run(p);}catch(Throwable t){report.put("baseline_error",t.toString());save();throw new IllegalStateException("Native baseline callback failed",t);}
        baselineHash=hash(p);report.put("baseline_after",baselineHash);
        report.put("baseline_unchanged",before.equals(baselineHash));save();
        SessionBridge.require(before.equals(baselineHash),"Native callback mutated baseline; transaction semantics required before enrolled comparison");
    }
    public static void enrolled(ServerPlayer p,MutationGate.Session s)throws Exception {
        SessionBridge.require(gameTime==p.serverLevel().getGameTime(),"Matched callback crossed game tick");
        SessionBridge.require(baselineHash.equals(NativeFence.hash(s.original())),"Matched enrolled source differs");
        report.put("enrolled_before",NativeFence.hash(s.original()));report.put("same_tick_and_exact_source",true);save();
        Throwable failure=null;
        try{run(p);}catch(Throwable t){failure=t;report.put("enrolled_error",t.toString());}
        try{s.assertIntegrity();report.put("enrolled_integrity_preserved",true);}
        catch(Throwable t){report.put("integrity_error",t.toString());if(failure==null)failure=t;}
        report.put("enrolled_after_receipt_hash",NativeFence.hash(s.original()));
        report.put("stopped_at_first_contract_boundary",true);save();
        // The diagnostic intentionally does not certify the full session workflow.
        throw new IllegalStateException("Matched callback diagnostic complete; inspect contract report",failure);
    }
}
