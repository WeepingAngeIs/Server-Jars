package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Destructive SYNTHETIC fixture, compiled only into the independent disposable test mod. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class PackedWalletCustodySmoke {
    static final int SLOT=12;
    static final String WALLET="io.github.lightman314.lightmanscurrency.common.items.WalletItem";
    static final String WRAPPER="io.github.lightman314.lightmanscurrency.common.items.data.WalletDataWrapper";
    static ServerPlayer admin, player;
    static UUID owner, tx;
    static Path world;
    static String scenario;
    static int phase;
    static byte[] original;
    static boolean started, waiting, done;
    static final Map<String,Object> report=new LinkedHashMap<>();
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void proof(boolean value,String name){require(value,name);report.put(name,true);}
    static int command(String value)throws Exception{return admin.server.getCommands().getDispatcher().execute(value,admin.server.createCommandSourceStack());}
    static Object call(String api,Object target,String method,Class<?>[] types,Object...args)throws IOException{return OriginCustodyPort.call(api,target,method,types,args);}
    static JailEquipmentPort items()throws IOException{return new JailEquipmentPort(player,tx==null?UUID.randomUUID():tx,null,true,true);}
    static JailCustodyLedger.Binding binding()throws IOException{return JailCustodyService.ledger(admin.server).list().stream().filter(b->b.owner().equals(owner)).toList().getLast();}
    static void save()throws Exception{admin.server.saveEverything(true,true,true);net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}
    static void forceWrite(Path path,byte[] bytes)throws IOException {
        try(var f=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())f.write(b);f.force(true);}
        try(var directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}
    }
    static void rememberTransaction()throws IOException{tx=binding().id();forceWrite(world.resolve("packed-wallet-tx"),tx.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static CompoundTag stackTag(ItemStack stack){return stack.isEmpty()?new CompoundTag():(CompoundTag)stack.save(player.registryAccess());}
    static ItemStack nativeWallet()throws IOException {
        String api="io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler";
        Object handler=call(api,null,"get",new Class<?>[]{LivingEntity.class},player);
        return (ItemStack)call(api,handler,"getWallet",new Class<?>[0]);
    }
    static void equip(ItemStack stack)throws Exception {
        String api="io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler";
        Object handler=call(api,null,"get",new Class<?>[]{LivingEntity.class},player);
        call(api,handler,"setWallet",new Class<?>[]{ItemStack.class},stack);CertifiedJailSmoke.target=player;CertifiedJailSmoke.flush();
    }
    static ItemStack makeWallet(String label)throws Exception {
        ItemStack result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:wallet_leather")));
        result.set(DataComponents.CUSTOM_NAME,Component.literal(label));CompoundTag marker=new CompoundTag();marker.putString("synthetic",UUID.randomUUID().toString());result.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
        Object wrapper=call(WALLET,null,"getDataWrapper",new Class<?>[]{ItemStack.class},result);
        Container contents=(Container)call(WRAPPER,wrapper,"getContents",new Class<?>[0]);
        ItemStack gold=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),11);
        gold.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven exact custody coins"));CompoundTag coinMarker=new CompoundTag();coinMarker.putInt("test_id",1642);gold.set(DataComponents.CUSTOM_DATA,CustomData.of(coinMarker));contents.setItem(0,gold);
        call(WRAPPER,wrapper,"setContents",new Class<?>[]{Container.class,LivingEntity.class},contents,player);
        return result;
    }
    static void init()throws Exception {
        admin.server.getPlayerList().op(admin.getGameProfile());admin.setInvulnerable(true);player.setInvulnerable(true);
        IntegratedJailSmoke.admin=admin;var min=IntegratedJailSmoke.site();admin.teleportTo(admin.serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);
        require(admin.server.getCommands().getDispatcher().execute("jailbuild preview",admin.createCommandSourceStack())>0&&admin.server.getCommands().getDispatcher().execute("jailbuild place",admin.createCommandSourceStack())>0,"placement failed");
        player.teleportTo(player.serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);
        CertifiedJailSmoke.admin=admin;CertifiedJailSmoke.target=player;CertifiedJailSmoke.world=world;CertifiedJailSmoke.seed();
        require(command("jailcustody init")==1&&command("jailintegrated init")==1,"custody init failed");
        proof(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()&&!JailConfig.PACKED_WALLET_ADMISSIONS.get(),"both_admission_switches_default_off");
        player.setInvulnerable(false);require(player.getInventory().getItem(SLOT).isEmpty(),"synthetic fixture slot occupied");
        player.getInventory().setItem(SLOT,makeWallet("Synthetic packed wallet owner"));CertifiedJailSmoke.flush();save();
        original=items().current().inventory();forceWrite(world.resolve("packed-wallet-original.nbt"),original);
        forceWrite(world.resolve("packed-wallet-native.nbt"),OriginCustodyPort.encode(stackTag(player.getInventory().getItem(SLOT))));
        proof(command("jail Visitor")==0&&Arrays.equals(original,items().current().inventory()),"default_off_refused_without_mutation");
        require(command("jailintegrated mode supported")==1,"supported mode failed");
        proof(command("jail Visitor")==0&&Arrays.equals(original,items().current().inventory()),"legacy_supported_mode_does_not_enable_wallets");
        require(command("jailintegrated mode packed-wallet")==1,"packed wallet mode failed");
        proof(command("jailintegrated check Visitor")==1,"packed_wallet_native_preflight_passed");
        if(scenario.equals("lifecycle"))negativeAdmission();
    }
    static void refusalUnchanged(String name)throws Exception {
        byte[] before=items().current().inventory();int size=JailCustodyService.ledger(admin.server).list().size();
        proof(command("jailintegrated check Visitor")==0&&command("jail Visitor")==0,name);
        proof(Arrays.equals(before,items().current().inventory())&&JailCustodyService.ledger(admin.server).list().size()==size,name+"_nonmutating");
    }
    static void negativeAdmission()throws Exception {
        ItemStack selectedBefore=player.getInventory().removeItemNoUpdate(player.getInventory().selected);ItemStack captured=player.getInventory().getItem(SLOT);player.getInventory().setItem(SLOT,ItemStack.EMPTY);player.getInventory().setItem(player.getInventory().selected,captured);CertifiedJailSmoke.flush();refusalUnchanged("selected_wallet_refused");player.getInventory().setItem(player.getInventory().selected,selectedBefore);player.getInventory().setItem(SLOT,captured);CertifiedJailSmoke.flush();
        player.getInventory().setItem(13,captured.copy());refusalUnchanged("multiple_wallets_refused");player.getInventory().setItem(13,ItemStack.EMPTY);
        captured.set(DataComponents.MAX_STACK_SIZE,2);refusalUnchanged("altered_component_refused");captured.remove(DataComponents.MAX_STACK_SIZE);
        // Restore only our synthetic full stack, not a normalized approximation.
        captured=ItemStack.parseOptional(player.registryAccess(),OriginCustodyPort.decode(Files.readAllBytes(world.resolve("packed-wallet-native.nbt"))));player.getInventory().setItem(SLOT,captured);
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);var pokemon=PokemonProperties.Companion.parse("eevee level=7 shiny=yes"," ","=").create(player);require(party.add(pokemon),"synthetic party seed failed");CompoundTag expected=party.saveToNBT(new CompoundTag(),player.registryAccess());refusalUnchanged("nonempty_party_still_refused");proof(expected.equals(party.saveToNBT(new CompoundTag(),player.registryAccess())),"party_not_changed_by_refusal");require(party.remove(pokemon),"synthetic teardown failed");
        player.getInventory().setItem(SLOT,ItemStack.EMPTY);equip(captured);refusalUnchanged("equipped_wallet_still_refused");proof(stackTag(nativeWallet()).equals(stackTag(captured)),"equipped_wallet_preserved");equip(ItemStack.EMPTY);player.getInventory().setItem(SLOT,captured);
        player.totalExperience=1;refusalUnchanged("packed_wallet_nonzero_xp_refused");player.totalExperience=0;
        CertifiedJailSmoke.flush();save();proof(Arrays.equals(original,items().current().inventory()),"negative_tests_restored_synthetic_source_exactly");
    }
    static void stored(String state)throws Exception {
        try(var journal=new JailEscrowJournal(world.resolve(binding().items()))){var row=journal.read(tx);proof(row!=null&&row.owner().equals(owner)&&Arrays.equals(row.snapshot(),original),"full_wallet_and_equipment_snapshot_owned_by_transaction");if(state!=null)proof(row.state().name().equals(state),"item_journal_"+state.toLowerCase());}
    }
    static void held()throws Exception {
        proof(JailCustodyService.quarantined(player)&&JailData.get(admin.server).assignment(owner)!=null,"quarantined_with_assignment");
        proof(items().current().empty()&&tx.equals(items().current().removedBy())&&nativeWallet().isEmpty(),"no_wallet_or_equipment_accessible_while_held");stored("HELD");noDrops();
    }
    static void noDrops(){proof(player.serverLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(128)).isEmpty(),"no_item_or_wallet_drops");}
    static void returned()throws Exception {
        proof(Arrays.equals(original,items().current().inventory()),"complete_wallet_components_coins_slots_and_equipment_restored");
        proof(OriginCustodyPort.decode(Files.readAllBytes(world.resolve("packed-wallet-native.nbt"))).equals(stackTag(player.getInventory().getItem(SLOT))),"native_wallet_exact_original_slot");
        proof(!JailCustodyService.quarantined(player)&&JailData.get(admin.server).assignment(owner)==null,"released_without_remaining_assignment");
        proof(nativeWallet().isEmpty(),"wallet_not_duplicated_into_equipped_storage");stored("COMPLETE");noDrops();
        for(int i=0;i<3;i++)proof(command("unjail Visitor")==0&&Arrays.equals(original,items().current().inventory()),"repeated_release_never_grants_again");
    }
    static void admit()throws Exception {
        proof(command("jail Visitor")==1,"actual_wallet_custody_admission");rememberTransaction();held();
        int size=JailCustodyService.ledger(admin.server).list().size();proof(command("jail Visitor")==0&&JailCustodyService.ledger(admin.server).list().size()==size,"duplicate_admission_refused");
    }
    static void offlineRefusal()throws Exception {
        require(admin.server.getPlayerList().getPlayer(owner)==null,"Offline negative control has a live target");
        var before=binding();boolean refused=false;
        try{refused=command("unjail Visitor")==0;}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){
            require(expected.getRawMessage().getString().equals("No player was found"),"Unexpected parser failure: "+expected);
            refused=true;
        }
        proof(refused&&before.equals(binding()),"offline_release_refused");
    }
    static void disconnect()throws Exception {save();waiting=true;player.connection.disconnect(Component.literal("Disposable packed wallet persistence test"));}
    static void writeReport(boolean crash,String stage)throws IOException {
        report.put("commit",System.getenv("GITHUB_SHA"));report.put("scenario",scenario);report.put("phase",phase);report.put("transaction",tx==null?null:tx.toString());report.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));report.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));report.put("hard_stop",crash);report.put("crash_boundary",stage);
        Path evidence=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke","packed-wallet-"+scenario+"-phase"+phase+".json");
        forceWrite(evidence,new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        forceWrite(Path.of("/tmp/uq-packed-wallet-result"),("passed: "+scenario+" phase "+phase+" hard_stop="+crash+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static void haltAt(JailIntegrated.Boundary point)throws Exception {
        JailIntegrated.disposableCheckpoint(player,at->{if(at==point){
            guard();rememberTransaction();report.put("injected_after",at.name());
            var live=items().current();var disk=items().durable();
            boolean returnStage=at==JailIntegrated.Boundary.ITEMS_RETURN_MUTATED||at==JailIntegrated.Boundary.ITEMS_RETURN_DURABLE;
            boolean durable=at==JailIntegrated.Boundary.ITEMS_DURABLE||at==JailIntegrated.Boundary.ITEMS_RETURN_DURABLE;
            proof(returnStage?Arrays.equals(original,live.inventory()):live.empty(),"crash_live_state_verified");
            proof(durable?(returnStage?Arrays.equals(original,disk.inventory()):disk.empty()):(returnStage?disk.empty():Arrays.equals(original,disk.inventory())),"crash_disk_state_verified");
            writeReport(true,at.name());Runtime.getRuntime().halt(86);
        }});
    }
    static void guard()throws IOException {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))||!Boolean.getBoolean("uq.packedWallet")||!world.toRealPath().getFileName().toString().equals("jail_integrated_smoke"))throw new IOException("Not the synthetic hosted test world");
    }
    static void uncertain(boolean originalOnPlayer)throws Exception {
        stored(null);proof(JailCustodyService.quarantined(player),"ambiguous_save_remains_quarantined");
        proof(originalOnPlayer?Arrays.equals(original,items().current().inventory()):items().current().empty(),"ambiguous_save_resource_location_preserved");
        byte[] before=items().current().inventory();for(int i=0;i<3;i++)require(command("unjail Visitor")==0&&command("jailcustody recover "+tx)==0,"uncertain save replayed");
        proof(Arrays.equals(before,items().current().inventory()),"uncertain_save_never_replayed_or_granted");noDrops();
    }
    static void step()throws Exception {
        if(phase==1){init();if(scenario.equals("remove-unsaved"))haltAt(JailIntegrated.Boundary.ITEMS_MUTATED);if(scenario.equals("remove-saved"))haltAt(JailIntegrated.Boundary.ITEMS_DURABLE);admit();disconnect();return;}
        original=Files.readAllBytes(world.resolve("packed-wallet-original.nbt"));tx=UUID.fromString(Files.readString(world.resolve("packed-wallet-tx")));require(binding().id().equals(tx),"transaction binding changed");
        if(scenario.equals("lifecycle")){
            if(phase==2){held();SplitAssertions.enforcement(player);report.put("command_and_teleport_confinement",true);RetainedXpSmoke.admin=admin;RetainedXpSmoke.target=player;player.setInvulnerable(false);RetainedXpSmoke.cross();proof(!player.isAlive()&&player.getHealth()<=0&&items().current().empty(),"actual_jailed_escape_death_with_wallet_in_escrow");stored("HELD");noDrops();disconnect();}
            else if(phase==3){proof(!player.isAlive()&&items().current().empty(),"restart_while_dead_kept_wallet_in_escrow");player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));player=admin.server.getPlayerList().getPlayer(owner);proof(player!=null&&player.connection.player==player&&player.isAlive(),"actual_native_respawn_rebound_authoritative_player");held();var jail=JailData.get(admin.server).assignment(owner).jail();proof(player.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001,"respawn_inside_jail");disconnect();}
            else if(phase==4){held();proof(command("unjail Visitor")==1,"actual_release_after_death_disconnect_and_restart");returned();disconnect();}
            else if(phase==5){returned();proof(true,"released_state_survived_restart");ItemStack wallet=player.getInventory().removeItemNoUpdate(SLOT);player.getInventory().setItem(13,wallet);save();byte[] moved=items().current().inventory();proof(command("unjail Visitor")==0&&Arrays.equals(moved,items().current().inventory()),"completed_receipt_never_regrants_even_after_item_move");disconnect();}
            else throw new IOException("unknown lifecycle phase");
        }else if(scenario.equals("occupied")){
            if(phase==2){held();player.getInventory().setItem(SLOT,makeWallet("Synthetic competing destination wallet"));save();forceWrite(world.resolve("packed-wallet-conflict.nbt"),items().current().inventory());byte[] competing=items().current().inventory();proof(command("unjail Visitor")==0&&Arrays.equals(competing,items().current().inventory()),"occupied_wallet_destination_refused_without_overwrite");stored("HELD");disconnect();}
            else if(phase==3){byte[] competing=Files.readAllBytes(world.resolve("packed-wallet-conflict.nbt"));proof(Arrays.equals(competing,items().current().inventory())&&JailCustodyService.quarantined(player),"occupied_destination_preserved_across_restart");proof(command("unjail Visitor")==0&&command("jailcustody recover "+tx)==0&&Arrays.equals(competing,items().current().inventory()),"occupied_destination_repeat_recovery_refused");stored("HELD");
                // Explicitly move only the independent synthetic blocker; production never clears it.
                require(admin.getInventory().getItem(15).isEmpty(),"admin test destination occupied");admin.getInventory().setItem(15,player.getInventory().removeItemNoUpdate(SLOT));save();proof(command("unjail Visitor")==1,"release_only_after_explicit_external_conflict_resolution");returned();disconnect();}
            else if(phase==4){returned();proof(!admin.getInventory().getItem(15).isEmpty(),"competing_wallet_not_deleted");disconnect();}else throw new IOException("unknown conflict phase");
        }else if(scenario.equals("remove-unsaved")){uncertain(true);disconnect();}
        else if(scenario.equals("remove-saved")){
            if(phase==2){stored("HELD");proof(items().current().empty()&&tx.equals(items().current().removedBy()),"durable_removal_receipt_survived_abrupt_stop");proof(command("jailcustody recover "+tx)==1,"saved_removal_explicitly_recovered_without_removal_replay");held();proof(command("unjail Visitor")==1,"release_after_saved_removal_recovery");returned();disconnect();}else if(phase==3){returned();disconnect();}else throw new IOException("unknown saved removal phase");
        }else if(scenario.equals("return-unsaved")||scenario.equals("return-saved")){
            if(phase==2){held();haltAt(scenario.equals("return-saved")?JailIntegrated.Boundary.ITEMS_RETURN_DURABLE:JailIntegrated.Boundary.ITEMS_RETURN_MUTATED);command("unjail Visitor");throw new IOException("return interruption hook did not halt server");}
            else if(scenario.equals("return-unsaved")){uncertain(false);disconnect();}
            else if(phase==3){stored("RESTORE_STARTED");proof(Arrays.equals(original,items().current().inventory())&&tx.equals(items().current().restoredBy()),"saved_return_receipt_and_complete_wallet_survived_abrupt_stop");proof(command("jailcustody recover "+tx)==1,"saved_return_explicitly_confirmed_without_regrant");returned();disconnect();}
            else if(phase==4){returned();disconnect();}else throw new IOException("unknown saved return phase");
        }else throw new IOException("unknown scenario");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.packedWallet")||done)return;
        try{
            if(waiting){if(event.getServer().getPlayerList().getPlayer(owner)!=null)return;waiting=false;proof(true,"actual_disconnect_observed");if(JailCustodyService.ledger(admin.server).list().stream().anyMatch(b->b.owner().equals(owner)))offlineRefusal();writeReport(false,"");done=true;return;}
            if(started)return;admin=event.getServer().getPlayerList().getPlayerByName("Dev");player=event.getServer().getPlayerList().getPlayerByName("Visitor");if(admin==null||player==null||event.getServer().getTickCount()<120||player.isAlive()&&player.tickCount<100)return;
            started=true;owner=player.getUUID();world=event.getServer().getWorldPath(LevelResource.ROOT);scenario=System.getProperty("uq.walletScenario");phase=Integer.getInteger("uq.walletPhase",0);guard();
            CertifiedJailSmoke.admin=admin;CertifiedJailSmoke.target=player;CertifiedJailSmoke.world=world;step();
        }catch(Throwable failure){done=true;waiting=false;failure.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-packed-wallet-result"),"failed: "+failure+"\n");}catch(IOException ignored){}}
    }
}
