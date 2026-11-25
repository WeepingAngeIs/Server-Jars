package dev.unyielding.bundlesmoke;

import dev.anya.storage.BackpackAccess;
import dev.anya.storage.BagInventory;
import dev.anya.storage.Upgrades;
import dev.unyielding.jail.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** One ordinary connected player, two server sessions, and no production substitutions. */
final class Release249Proof {
    private static int admittedAt;
    private static UUID retainedTransaction;
    private Release249Proof() {}

    private static void hosted(ServerPlayer p) throws Exception {
        require("true".equals(System.getenv("GITHUB_ACTIONS"))
                && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))
                && p.getGameProfile().getName().equals("Visitor") && !p.hasDisconnected()
                && CertifiedJailSmoke.world.toRealPath().getFileName().toString().equals("jail_certified_smoke"),
                "Release proof requires the connected disposable hosted Visitor");
    }
    private static boolean savedConfig(String stage) throws Exception {
        var path=Path.of("config/minecraftjail-common.toml");
        var text=Files.readString(path);
        var matcher=Pattern.compile("(?m)^\\s*confiscate_inventory\\s*=\\s*(true|false)\\s*(?:#.*)?$").matcher(text);
        require(matcher.find(),"Saved confiscation config is absent: "+stage);
        boolean value=Boolean.parseBoolean(matcher.group(1));
        require(!matcher.find(),"Multiple confiscation settings in saved config");
        require(value==JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Saved/live policy disagree: "+stage);
        Files.writeString(results.resolve("release-config-"+stage+".toml"),text);
        return value;
    }
    private static CompoundTag inventory(ServerPlayer p) {
        var value=new CompoundTag();
        value.put("slots",p.getInventory().save(new ListTag()).copy());
        value.putInt("selected",p.getInventory().selected);
        return value;
    }
    private static void exact(ServerPlayer p) throws Exception {
        require(OriginCustodyPort.decode(Files.readAllBytes(evidence(p,"release-inventory"))).equals(inventory(p)),
                "Original inventory slots, components, counts or selected slot changed");
        var encoded=OriginCustodyPort.decode(Files.readAllBytes(evidence(p,"storage-bag")));
        var bag=ItemStack.parse(p.registryAccess(),encoded).orElseThrow();
        UUID id=BagInventory.data(bag).getUUID("BagId");
        require(p.getInventory().getItem(12).save(p.registryAccess()).equals(encoded),"Marked backpack changed or left slot12");
        int copies=0;
        for(var source:BackpackAccess.all(p)) {
            var data=BagInventory.data(source.stack());
            if(data.hasUUID("BagId") && data.getUUID("BagId").equals(id))copies++;
        }
        require(copies==1 && Upgrades.installed(new BagInventory(p,12),"feeding"),"Marked upgraded backpack missing or duplicated");
        var shards=new BagInventory(p,12).getItem(3);
        require(shards.is(Items.AMETHYST_SHARD) && shards.getCount()==13
                && shards.getHoverName().getString().equals("Marked custody shards"),"Thirteen marked nested shards changed");
        var marked=new java.util.ArrayList<ItemStack>();
        for(int slot=0;slot<p.getInventory().getContainerSize();slot++)
            if(!p.getInventory().getItem(slot).isEmpty())marked.add(p.getInventory().getItem(slot).copy());
        marked.add(shards.copy());
        for(Tag raw:BagInventory.data(p.getInventory().getItem(12)).getList("InstalledUpgrades",Tag.TAG_COMPOUND))
            marked.add(ItemStack.parseOptional(p.registryAccess(),((CompoundTag)raw).getCompound("Stack")));
        marked.addAll(PackedWalletProof.coins(p.getInventory().getItem(PackedWalletProof.SLOT)));
        for(var level:p.server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof ItemEntity drop)
            for(var expected:marked)require(expected.isEmpty() || !ItemStack.isSameItemSameComponents(expected,drop.getItem()),
                    "A marked item, bag, shard, upgrade or coin was duplicated into a world drop");
        PackedWalletProof.returned(p);
    }
    private static JailEscrowJournal plans(ServerPlayer p,Class<?> type,String field) throws Exception {
        var reflected=type.getDeclaredField(field);reflected.setAccessible(true);
        var owned=((Map<?,?>)reflected.get(null)).get(p.server);
        require(owned instanceof JailEscrowJournal,"Missing authoritative journal owner");
        return (JailEscrowJournal)owned;
    }
    private static void noStaging(ServerPlayer p) throws Exception {
        require(JailCustodyService.ledger(p.server).list().isEmpty()
                && plans(p,JailIntegrated.class,"plans").list().isEmpty(),"Default Off created inventory custody/staging");
        require(!p.getPersistentData().contains(JailVanillaPort.REMOVED)
                && !p.getPersistentData().contains(JailVanillaPort.RESTORED),"Default Off wrote an inventory receipt");
        var directory=CertifiedJailSmoke.world.resolve("retained-jail-v1").resolve(retainedTransaction.toString());
        for(String name:new String[]{"items","remove-effects","return-effects"})
            require(Files.notExists(directory.resolve(name)),"Default Off created item or return journal: "+name);
        var entry=plans(p,JailRetainedCustody.class,"RECORDS").read(retainedTransaction);
        require(entry!=null,"Retained admission record missing");
        var plan=OriginCustodyPort.decode(entry.snapshot());
        require(plan.getString("contract").equals("native-retained-inventory-v1")
                && plan.contains("confiscate_inventory",Tag.TAG_BYTE) && !plan.getBoolean("confiscate_inventory")
                && plan.getByteArray("retained_inventory_digest").length==32,"Default Off policy/digest was not recorded");
        Policy249Writes.noJailWrites();
    }
    private static void retainedCell(ServerPlayer p) throws Exception {
        var assignment=JailData.get(p.server).assignment(p.getUUID());
        require(assignment!=null && assignment.geometry()==4 && !assignment.confiscateInventory()
                && JailRetainedCustody.active(p) && JailCustodyService.quarantined(p),"Fresh Off did not retain its confinement contract");
        var first=JailData.get(p.server).location();var jail=assignment.jail();
        require(jail.dim().equals(first.dim()) && jail.x()==first.x()+12 && jail.z()==first.z()+12
                && p.serverLevel().dimension().location().toString().equals(jail.dim())
                && p.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001 && p.isAlive(),"Retained player left intact cell6");
        require(retainedTransaction.equals(JailIntegrated.activeTransaction(p.server,p.getUUID())),"Default Off transaction changed");
        exact(p);noStaging(p);
    }
    private static void place(ServerPlayer p) throws Exception {
        var level=p.serverLevel();
        BlockPos min=new BlockPos(p.getBlockX()-9,Math.min(level.getMaxBuildHeight()-20,p.getBlockY()+20),p.getBlockZ()+4);
        for(int x=min.getX()>>4;x<=(min.getX()+19)>>4;x++)
            for(int z=(min.getZ()-5)>>4;z<=(min.getZ()+30)>>4;z++)level.getChunk(x,z);
        for(int x=0;x<19;x++)for(int z=-5;z<30;z++) {
            for(int y=-1;y<10;y++)require(level.getBlockState(min.offset(x,y,z)).isAir(),"Disposable fixture site is occupied");
            level.setBlock(min.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        }
        p.server.getPlayerList().op(p.getGameProfile());
        try {
            p.teleportTo(level,min.getX()+9.5,min.getY(),min.getZ()-3.5,0,0);
            require(p.server.getCommands().getDispatcher().execute("jailbuild preview",p.createCommandSourceStack())==1
                    && p.server.getCommands().getDispatcher().execute("jailbuild place",p.createCommandSourceStack())==1,
                    "Normal Jail placement commands failed");
        } finally { p.server.getPlayerList().deop(p.getGameProfile()); }
        var first=JailData.get(p.server).location();
        require(JailData.get(p.server).geometry()==4 && JailPlacement.intact(p.server,
                new Loc(first.dim(),first.x()+12,first.y(),first.z()+12,first.yaw(),first.pitch()),4),"Cell6 is not intact");
    }
    private static void seed(ServerPlayer p) throws Exception {
        p.getInventory().clearContent();p.getInventory().selected=3;
        var diamonds=new ItemStack(Items.DIAMOND,23);
        diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Default Off diamonds slot8"));p.getInventory().setItem(8,diamonds);
        var shards=new ItemStack(Items.AMETHYST_SHARD,5);
        shards.set(DataComponents.CUSTOM_NAME,Component.literal("Default Off loose shards slot18"));p.getInventory().setItem(18,shards);
        var coin=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),7);
        coin.set(DataComponents.CUSTOM_NAME,Component.literal("Default Off gold coins slot23"));p.getInventory().setItem(23,coin);
        p.setHealth(p.getMaxHealth());PackedWalletProof.seed(p);CombinedStorageProof.seed(p);
        CertifiedJailSmoke.flush();CertifiedJailSmoke.saved();
        Files.write(evidence(p,"release-inventory"),OriginCustodyPort.encode(inventory(p)));
        Files.writeString(results.resolve("release-inventory-expected.snbt"),inventory(p).toString());
    }
    static void begin(ServerPlayer p) throws Exception {
        hosted(p);Custody249Proof.runtime();
        require(!JailConfig.JAIL_CONFISCATE_INVENTORY.get() && !savedConfig("fresh"),"A genuinely fresh config did not default Off");
        proof.put("fresh_config_value",false);flag("fresh_config_loaded_default_off");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get() && !JailConfig.JAIL_PACKED_WALLETS.get(),"Independent admission defaults changed");
        require(CertifiedJailSmoke.choices().equals(Map.of(ResourceLocation.parse("neoorigins:origin"),ResourceLocation.parse("neoorigins:human"),
                ResourceLocation.parse("neoorigins:class"),ResourceLocation.parse("neoorigins:class_warrior"))),"Native onboarding did not select Human/Warrior");
        FullPackSession.noClaims(p);place(p);seed(p);
        require(!p.hasPermissions(2),"Fixture player retained administrative privilege");
        require(cmd("jailcustody init")==1 && cmd("jailintegrated init")==1,"Could not initialize disposable journals");
        require(cmd("jail Visitor 6")==0 && JailData.get(p.server).assignment(p.getUUID())==null,
                "Changing the confiscation default enabled master admission");
        flag("master_admission_default_remains_disabled");
        require(cmd("jailintegrated mode supported")==1 && cmd("jailintegrated check Visitor")==1,"Reviewed default Off fixture is ineligible");
        Policy249Writes.arm(p.getUUID());
        require(cmd("jail Visitor 6")==1,"Fresh default Off refused normal admission");
        retainedTransaction=JailIntegrated.activeTransaction(p.server,p.getUUID());
        require(retainedTransaction!=null,"Fresh Off lacks transaction");
        proof.put("fresh_off_transaction",retainedTransaction.toString());retainedCell(p);
        flag("default_off_normal_entry_admission");
        int[] calls={0};p.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("uq_release_forbidden").executes(c->++calls[0]));
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"uq_release_forbidden");
        require(calls[0]==0,"Default Off permitted prisoner commands");
        p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(),"uq_release_forbidden");
        require(calls[0]==1,"Console command control failed");
        var j=JailData.get(p.server).assignment(p.getUUID()).jail();
        p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(),"tp Visitor "+(j.x()+50)+" "+j.y()+" "+j.z());
        p.setInvulnerable(false);p.invulnerableTime=0;float health=p.getHealth();p.hurt(p.damageSources().generic(),2);
        require(p.getHealth()==health,"Default Off lost external damage protection");retainedCell(p);
        flag("default_off_commands_teleport_and_damage_blocked");
        admittedAt=p.server.getTickCount();deadline=admittedAt+400;
    }
    static void observe(ServerPlayer p) throws Exception { retainedCell(p); }
    static void tick(ServerPlayer p) throws Exception {
        if(p.server.getTickCount()-admittedAt<40)return;
        retainedCell(p);proof.put("default_off_confined_server_ticks",p.server.getTickCount()-admittedAt);
        flag("default_off_inventory_untouched_during_confinement");
        PolicySettingsProof.set(p,true);
        require(savedConfig("explicit-on"),"Admin opt-in did not save On");
        proof.put("saved_config_value_before_restart",true);flag("explicit_on_saved_by_admin_ui");
        retainedCell(p);flag("off_admission_policy_preserved_after_opt_in");
        require(cmd("unjail Visitor")==1,"Stored default Off release failed");exact(p);noStaging(p);
        Files.writeString(results.resolve("release-inventory-after-off.snbt"),inventory(p).toString());
        require(!JailCustodyService.quarantined(p) && JailData.get(p.server).assignment(p.getUUID())==null
                && JailIntegrated.activeTransaction(p.server,p.getUUID())==null,"Default Off release left confinement active");
        proof.put("default_off_inventory_write_observations",Policy249Writes.snapshot());
        flag("off_release_without_inventory_writes_or_return_staging");
        require(cmd("jailintegrated check Visitor")==1,"Explicit On preflight rejected the same eligible inventory");
        var port=CertifiedJailSmoke.items(UUID.randomUUID(),null);port.validateRemoval();
        Files.write(evidence(p,"equipment"),port.current().inventory());
        Policy249Writes.arm(p.getUUID());
        require(cmd("jail Visitor 6")==1,"Explicit On normal admission failed");remember(p);cell(p,6);
        require(JailData.get(p.server).assignment(p.getUUID()).confiscateInventory() && p.getInventory().isEmpty(),
                "Explicit On failed to record On and actually confiscate");
        require(!retainedTransaction.equals(JailIntegrated.activeTransaction(p.server,p.getUUID())),"On reused a completed Off transaction");
        CombinedStorageProof.held(p);CombinedStorageProof.attemptRecovery(p,true);
        proof.put("on_held_transaction",JailIntegrated.activeTransaction(p.server,p.getUUID()).toString());
        proof.put("on_admission_inventory_write_observations",Policy249Writes.snapshot());
        flag("explicit_on_actual_confiscation");
        require(cmd("jailintegrated mode disabled")==1,"Cannot freeze admissions before restart");
        require(savedConfig("before-restart"),"Disabling admissions changed saved On");
        flag("on_held_transaction_saved_for_restart");pass();
    }
    static void resumed(ServerPlayer p) throws Exception {
        hosted(p);Custody249Proof.runtime();
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get() && savedConfig("after-restart")
                && !JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Explicit saved On or independent admission switch changed on restart");
        proof.put("saved_config_value_after_restart",true);flag("explicit_saved_on_preserved_across_restart");
        require(JailData.get(p.server).assignment(p.getUUID()).confiscateInventory(),"Held On admission policy changed across restart");
        proof.put("on_held_transaction",JailIntegrated.activeTransaction(p.server,p.getUUID()).toString());
        cell(p,6);CombinedStorageProof.held(p);Policy249Writes.arm(p.getUUID());
        CombinedStorageProof.attemptRecovery(p,false);flag("recorded_on_and_held_items_survived_restart");
        var before=inventory(p);PolicySettingsProof.set(p,false);
        require(!savedConfig("global-off-before-release") && before.equals(inventory(p))
                && JailData.get(p.server).assignment(p.getUUID()).confiscateInventory(),"Global Off rewrote existing On custody");
        flag("recorded_on_preserved_after_global_off");
        release(p);exact(p);CombinedStorageProof.returned(p);FullPackSession.noClaims(p);
        Files.writeString(results.resolve("release-inventory-after-on-restart.snbt"),inventory(p).toString());
        require(!JailCustodyService.quarantined(p) && JailData.get(p.server).assignment(p.getUUID())==null
                && JailIntegrated.activeTransaction(p.server,p.getUUID())==null,"Held release did not retire confinement");
        var writes=Policy249Writes.snapshot();
        require(Integer.valueOf(1).equals(((Map<?,?>)writes.get("confiscation_or_return_calls")).get("restoreExact")),
                "Held transaction did not restore exactly once: "+writes);
        proof.put("on_release_inventory_write_observations",writes);
        flag("already_held_transaction_returned_once_while_global_off");flag("exact_original_inventory_slots_after_restart_release");
        require(cmd("unjail Visitor")==0,"Duplicate release succeeded");
        CombinedStorageProof.returned(p);exact(p);
        require(writes.equals(Policy249Writes.snapshot()),"Duplicate/stale recovery repeated a Jail inventory return");
        proof.put("inventory_return_count_after_duplicate_release",1);flag("duplicate_release_did_not_repeat_inventory_return");
        pass();
    }
}
