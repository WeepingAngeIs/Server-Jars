package dev.unyielding.jail;

import dev.unyielding.jail.JailConfig;
import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Disabled by default. A switch never bypasses any runtime, provider or native-state audit. */
public final class JailAdmissions {
    private JailAdmissions(){}
    static String contract(ServerPlayer p)throws IOException {
        if(JailConfig.JAIL_CERTIFIED_ADMISSIONS.get())return auditedContract(p);
        if(JailHumanAudit.disposable(p)&&!Boolean.getBoolean("uq.certifiedJail")&&!Boolean.getBoolean("uq.retainedXp"))return Boolean.getBoolean("uq.expandedJail")?JailIntegrated.EXPANDED:"human-empty-v1";
        throw new IOException("Jail admissions are disabled. Console: /jailintegrated check "+p.getGameProfile().getName()+", then /jailintegrated mode supported. Recovery remains available");
    }
    static String auditedContract(ServerPlayer p){return JailXp.nonzero(p)?JailIntegrated.RETAINED_XP:JailPackedWallet.present(p)?JailIntegrated.PACKED_WALLET:JailIntegrated.SUPPORTED;}
    static void boundary(ServerPlayer p)throws IOException {boundary(p,false);}
    static void boundary(ServerPlayer p,boolean retainXp)throws IOException {
        if(p.server.getWorldData().isHardcore())throw new IOException("Hardcore player respawn is not supported");
        if(retainXp)JailXp.live(p);
        if(!retainXp&&(p.totalExperience!=0||p.experienceLevel!=0||p.experienceProgress!=0))throw new IOException("Player carries XP; XP custody is not certified. XP was left untouched");
        if(!p.getActiveEffects().isEmpty()||p.getAbsorptionAmount()!=0)throw new IOException("Wait for active effects/absorption to end before certified admission");
        var a=p.getAbilities();if(a.mayfly||a.flying||a.instabuild||a.invulnerable||!a.mayBuild||a.getFlyingSpeed()!=0.05F||a.getWalkingSpeed()!=0.1F)throw new IOException("Creative/flight or nondefault abilities are not certified for jail respawn");
        CompoundTag attachments=p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        JailPackState.verifyAttachments(p,attachments);
    }
    /** Guard a pending certified transaction even before item removal or during uncertain recovery. */
    public static boolean protectedCustody(ServerPlayer p){
        if(p.server==null||!p.server.isSameThread()||!JailData.get(p.server).integratedCustodyInitialized())return false;
        try{return JailIntegrated.certifiedActive(p);}catch(IOException|RuntimeException bad){return JailCustodyService.quarantined(p);}
    }
}
