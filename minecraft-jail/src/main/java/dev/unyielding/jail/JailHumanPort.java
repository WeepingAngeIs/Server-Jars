package dev.unyielding.jail;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Audited no-op origin adapter: already Human, no other selected powers, no accessory items.
 * Never clears/replaces an attachment or calls an origin/power/claim lifecycle. */
public final class JailHumanPort implements JailOriginTransaction.Port {
    private static final String CLAIMS="com.cyberday1.neoorigins.data.OriginClaimsData", RECEIPT="uq_integrated_origin";
    private final ServerPlayer player;private final JailVanillaPort inventory;private final UUID id,retired;
    public JailHumanPort(ServerPlayer player,UUID id,UUID retired){this.player=player;this.id=id;this.retired=retired;inventory=new JailVanillaPort(player,id,retired);}
    public UUID owner(){return player.getUUID();}public boolean online(){return inventory.online();}
    private Path claimsFile(){return player.server.getWorldPath(LevelResource.ROOT).resolve("data/neoorigins_origin_claims.dat");}
    private Object claims()throws IOException {
        if(!Files.isRegularFile(claimsFile(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing native claims history required; never initialize during custody");
        return JailHumanAudit.call(CLAIMS,null,"get",new Class<?>[]{net.minecraft.server.MinecraftServer.class},player.server);
    }
    @SuppressWarnings("unchecked") private CompoundTag allClaims(Object claims)throws IOException {
        CompoundTag result=new CompoundTag();Map<ResourceLocation,Map<ResourceLocation,UUID>> layers=(Map<ResourceLocation,Map<ResourceLocation,UUID>>)JailHumanAudit.call(CLAIMS,claims,"view");
        for(var layer:layers.entrySet()){CompoundTag row=new CompoundTag();for(var origin:layer.getValue().entrySet())row.putUUID(origin.getKey().toString(),origin.getValue());result.put(layer.getKey().toString(),row);}return result;
    }
    @SuppressWarnings("unchecked") private JailOriginTransaction.View view(CompoundTag saved,Object claims)throws IOException {
        var audit=JailHumanAudit.inspect(player);CompoundTag nativeData=saved.getCompound("neoforge:attachments");
        if(!nativeData.contains(JailHumanAudit.ATTACHMENT,10)||!nativeData.getCompound("accessories:inventory_holder").contains("accessories_containers",10))throw new IOException("Native origin/accessory persistence missing");
        boolean unique=Boolean.TRUE.equals(JailHumanAudit.call("com.cyberday1.neoorigins.config.AdminConfig",null,"isUniqueLayer",new Class<?>[]{ResourceLocation.class},audit.layer()));
        Object owner=JailHumanAudit.call(CLAIMS,claims,"getOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},audit.layer(),audit.human());
        if(!unique||!owner().equals(owner))throw new IOException("Human must already own its unique native claim; no lease takeover permitted");
        CompoundTag body=new CompoundTag();body.putString("runtime",audit.runtime());body.putString("layer",audit.layer().toString());body.putString("human",audit.human().toString());body.put("slots",audit.slots().copy());
        body.put("origin",nativeData.getCompound(JailHumanAudit.ATTACHMENT).copy());body.put("accessories",nativeData.getCompound("accessories:inventory_holder").getCompound("accessories_containers").copy());body.put("claims",allClaims(claims));
        CompoundTag receipt=saved.getCompound("NeoForgeData").getCompound(RECEIPT).copy();
        UUID transaction=receipt.hasUUID("transaction")?receipt.getUUID("transaction"):null;
        if(transaction!=null&&!transaction.equals(id)){if(!transaction.equals(retired))throw new IOException("Foreign origin return receipt");receipt=new CompoundTag();transaction=null;}
        JailOriginTransaction.Receipt phase=JailOriginTransaction.Receipt.NONE;
        if(!receipt.isEmpty())try{phase=JailOriginTransaction.Receipt.valueOf(receipt.getString("phase"));}catch(IllegalArgumentException bad){throw new IOException("Unknown Human receipt",bad);}
        body.put("receipt",receipt);return new JailOriginTransaction.View(JailNbt.encode(body),transaction,phase);
    }
    public JailOriginTransaction.View current()throws IOException{return view(player.saveWithoutId(new CompoundTag()),claims());}
    public JailOriginTransaction.View durable()throws IOException {
        CompoundTag disk;try(var input=Files.newInputStream(claimsFile())){disk=NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024));}
        if(!disk.contains("data",10))throw new IOException("Native claims save malformed");
        Object claims=JailHumanAudit.call(CLAIMS,null,"load",new Class<?>[]{CompoundTag.class,HolderLookup.Provider.class},disk.getCompound("data"),player.registryAccess());return view(inventory.savedPlayer(),claims);
    }
    public void persistAndVerify()throws IOException {
        var before=current();durable(); // Refuse missing/corrupt source files before any save.
        inventory.persistAndVerify();player.server.overworld().getDataStorage().save();
        try{net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}catch(RuntimeException bad){throw new IOException("Claims IO worker failed",bad);}
        JailVanillaPort.force(claimsFile());if(!same(before,current())||!same(before,durable()))throw new IOException("Human/claims/accessory save changed or failed");
    }
    private static boolean same(JailOriginTransaction.View a,JailOriginTransaction.View b){return Arrays.equals(a.state(),b.state())&&Objects.equals(a.transaction(),b.transaction())&&a.receipt()==b.receipt();}
    public void validateCustody(JailEscrowJournal.Entry entry,boolean releaseStarted)throws IOException {
        var live=inventory.current();var disk=inventory.durable();
        if(!entry.id().equals(id)||!entry.owner().equals(owner())||!live.empty()||!disk.empty()||!id.equals(live.removedBy())||!id.equals(disk.removedBy())||live.restoredBy()!=null||disk.restoredBy()!=null||!Arrays.equals(live.inventory(),disk.inventory()))throw new IOException("Linked held inventory/receipt changed");
    }
    public byte[] prepareIntent(UUID tx,byte[] digest)throws IOException {
        if(!online()||!id.equals(tx)||digest.length!=32)throw new IOException("Wrong/offline Human transaction");var before=current();
        if(before.transaction()!=null||before.receipt()!=JailOriginTransaction.Receipt.NONE)throw new IOException("Unresolved Human transaction");
        CompoundTag intent=new CompoundTag();intent.putUUID("owner",owner());intent.putUUID("transaction",id);intent.putByteArray("custody",digest);intent.putByteArray("before",before.state());return JailNbt.encode(intent);
    }
    private CompoundTag expected(byte[] bytes,UUID tx,JailOriginTransaction.Receipt phase)throws IOException {
        CompoundTag intent=JailNbt.decode(bytes);if(!intent.hasUUID("owner")||!owner().equals(intent.getUUID("owner"))||!intent.hasUUID("transaction")||!id.equals(intent.getUUID("transaction"))||!id.equals(tx))throw new IOException("Foreign Human intent");
        CompoundTag body=JailNbt.decode(intent.getByteArray("before"));CompoundTag receipt=new CompoundTag();receipt.putUUID("transaction",id);receipt.putString("phase",phase.name());receipt.putByteArray("custody",intent.getByteArray("custody"));body.put("receipt",receipt);return body;
    }
    public boolean isPrepared(byte[] bytes,JailOriginTransaction.View view)throws IOException {CompoundTag intent=JailNbt.decode(bytes);expected(bytes,id,JailOriginTransaction.Receipt.TEMPORARY);return view.receipt()==JailOriginTransaction.Receipt.NONE&&view.transaction()==null&&Arrays.equals(view.state(),intent.getByteArray("before"));}
    private boolean matches(byte[] bytes,UUID tx,JailOriginTransaction.View view,JailOriginTransaction.Receipt phase)throws IOException {return tx.equals(view.transaction())&&view.receipt()==phase&&expected(bytes,tx,phase).equals(JailNbt.decode(view.state()));}
    public boolean isTemporary(byte[] b,UUID tx,JailOriginTransaction.View v)throws IOException{return matches(b,tx,v,JailOriginTransaction.Receipt.TEMPORARY);}
    public boolean isOriginal(byte[] b,UUID tx,JailOriginTransaction.View v)throws IOException{return matches(b,tx,v,JailOriginTransaction.Receipt.ORIGINAL);}
    private JailOriginTransaction.View plan(byte[] bytes,UUID tx,boolean temporary)throws IOException {
        var before=current();if(!(temporary?isPrepared(bytes,before):isTemporary(bytes,tx,before)))throw new IOException("Human, claims, runtime or slots changed; retained for review");
        var phase=temporary?JailOriginTransaction.Receipt.TEMPORARY:JailOriginTransaction.Receipt.ORIGINAL;return new JailOriginTransaction.View(JailNbt.encode(expected(bytes,tx,phase)),tx,phase);
    }
    public JailOriginTransaction.View planTemporary(byte[] b,UUID tx)throws IOException{return plan(b,tx,true);}public JailOriginTransaction.View planOriginal(byte[] b,UUID tx)throws IOException{return plan(b,tx,false);}
    private void mark(byte[] b,UUID tx,boolean temporary)throws IOException {var planned=plan(b,tx,temporary);player.getPersistentData().put(RECEIPT,JailNbt.decode(planned.state()).getCompound("receipt").copy());if(!same(planned,current()))throw new IOException("Human receipt mutation changed undeclared state");}
    public void makeTemporary(byte[] b,UUID tx)throws IOException{mark(b,tx,true);}public void restoreOriginal(byte[] b,UUID tx)throws IOException{mark(b,tx,false);}
}
