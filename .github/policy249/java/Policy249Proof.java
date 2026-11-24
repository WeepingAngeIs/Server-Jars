package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.anya.storage.BackpackAccess;
import dev.anya.storage.BagInventory;
import dev.anya.storage.Upgrades;
import dev.unyielding.jail.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** Per-admission policy proof. Input changes are restricted to the disposable Visitor fixture. */
final class Policy249Proof {
    private static int escapedAt;
    private static long escapedNanos;
    private static boolean replacementSeen;
    private static int lightBefore;
    private Policy249Proof() {}
    static boolean retained() { return "off_to_on".equals(System.getenv("UQ_POLICY_CASE")); }
    static void defaults(ServerPlayer player) throws Exception {
        require(System.getenv("GITHUB_ACTIONS").equals("true")
                && System.getenv("RUNNER_ENVIRONMENT").equals("github-hosted")
                && player.getGameProfile().getName().equals("Visitor") && !player.hasDisconnected(),
                "Policy proof requires the hosted connected Visitor");
        PolicySettingsProof.defaults(player);
        proof.put("policy_case",System.getenv("UQ_POLICY_CASE"));
    }

    static void beforeOnAdmission(ServerPlayer player) throws Exception {
        PolicyTeleportProof.run(player);
        savedStateControls(player);
        Policy249Writes.arm(player.getUUID());
    }
    static void admittedOn(ServerPlayer player) throws Exception {
        var a=JailData.get(player.server).assignment(player.getUUID());
        require(a!=null && a.confiscateInventory() && !JailRetainedCustody.active(player),"On admission did not snapshot On");
        var before=inventory(player);
        var writes=Policy249Writes.snapshot();
        PolicySettingsProof.set(player,false);
        require(before.equals(inventory(player)) && writes.equals(Policy249Writes.snapshot()),"Turning Off changed held inventory");
        require(JailData.get(player.server).assignment(player.getUUID()).confiscateInventory(),"Turning Off rewrote an active On policy");
        flag("on_admission_snapshots_policy_before_off_flip");
        flag("off_flip_keeps_existing_escrow_and_inventory_unchanged");
    }
    static void afterOnRespawn(ServerPlayer player) throws Exception {
        PolicyTeleportProof.afterEscape(player);
        require(!JailConfig.JAIL_CONFISCATE_INVENTORY.get()
                && JailData.get(player.server).assignment(player.getUUID()).confiscateInventory(),"On snapshot followed the flipped config");
        var observation=Policy249Writes.snapshot();
        require(!((Map<?,?>)observation.get("confiscation_or_return_calls")).isEmpty(),"On positive control did not exercise mutation observers");
        proof.put("inventory_write_observations",observation);
        flag("on_escape_kept_escrow_after_policy_off");
    }
    static void resumedOn(ServerPlayer player) throws Exception {
        require(!JailConfig.JAIL_CONFISCATE_INVENTORY.get()
                && JailData.get(player.server).assignment(player.getUUID()).confiscateInventory(),"On snapshot or Off setting changed across restart");
        Policy249Writes.arm(player.getUUID());
        PolicyTeleportProof.holdIncoming(player);
        flag("on_to_off_active_policy_survived_restart");
    }
    static void completedOn(ServerPlayer player) throws Exception {
        require(!JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Release changed the server's Off setting");
        var observation=Policy249Writes.snapshot();
        require(!((Map<?,?>)observation.get("confiscation_or_return_calls")).isEmpty(),"Held On items were not returned through the production port");
        proof.put("inventory_write_observations",observation);
        require(cmd("unjail Visitor")==0,"Duplicate On release was accepted");
        CombinedStorageProof.returned(player);
        flag("existing_on_custody_returns_once_while_global_off");
    }

    private static CompoundTag inventory(ServerPlayer player) {
        var tag=new CompoundTag();tag.put("slots",player.getInventory().save(new ListTag()).copy());
        tag.putInt("selected",player.getInventory().selected);
        return tag;
    }
    private static CompoundTag inventoryReceipts(ServerPlayer player) {
        var result=new CompoundTag();
        for(String key:new String[]{JailVanillaPort.REMOVED,JailVanillaPort.RESTORED})
            if(player.getPersistentData().contains(key))result.put(key,player.getPersistentData().get(key).copy());
        return result;
    }
    private static void exact(ServerPlayer player) throws Exception {
        var expected=OriginCustodyPort.decode(Files.readAllBytes(evidence(player,"retained-inventory")));
        require(expected.equals(inventory(player)),"Retained inventory slots/components/selection changed");
        require(!player.getPersistentData().contains(JailVanillaPort.REMOVED)
                && !player.getPersistentData().contains(JailVanillaPort.RESTORED),"Retained session acquired an inventory custody receipt");
        var encoded=OriginCustodyPort.decode(Files.readAllBytes(evidence(player,"storage-bag")));
        var wanted=ItemStack.parse(player.registryAccess(),encoded).orElseThrow();
        var bagId=BagInventory.data(wanted).getUUID("BagId");
        require(player.getInventory().getItem(12).save(player.registryAccess()).equals(encoded),"Marked backpack changed or moved from slot12");
        int identities=0;
        for(var source:BackpackAccess.all(player)) {
            var data=BagInventory.data(source.stack());
            if(data.hasUUID("BagId") && data.getUUID("BagId").equals(bagId))identities++;
        }
        require(identities==1 && Upgrades.installed(new BagInventory(player,12),"feeding"),"Retained bag or marked upgrade duplicated/lost");
        var shards=new BagInventory(player,12).getItem(3);
        require(shards.is(Items.AMETHYST_SHARD) && shards.getCount()==13
                && shards.getHoverName().getString().equals("Marked custody shards"),"The thirteen marked shards changed");
        noDuplicateDrops(player);
        for(var level:player.server.getAllLevels()) for(var entity:level.getAllEntities()) {
            if(entity instanceof ItemEntity drop) {
                var data=BagInventory.data(drop.getItem());
                require(!data.hasUUID("BagId") || !data.getUUID("BagId").equals(bagId),"A retained bag copy was dropped");
            }
        }
        PackedWalletProof.returned(player);
        Policy249Writes.noJailWrites();
    }
    private static void noDuplicateDrops(ServerPlayer player) throws Exception {
        var protectedStacks=new java.util.ArrayList<ItemStack>();
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var stack=player.getInventory().getItem(slot);
            if(!stack.isEmpty())protectedStacks.add(stack.copy());
        }
        protectedStacks.add(new BagInventory(player,12).getItem(3).copy());
        var upgrades=BagInventory.data(player.getInventory().getItem(12)).getList("InstalledUpgrades",Tag.TAG_COMPOUND);
        for(Tag raw:upgrades)protectedStacks.add(ItemStack.parseOptional(player.registryAccess(),((CompoundTag)raw).getCompound("Stack")));
        protectedStacks.addAll(PackedWalletProof.coins(player.getInventory().getItem(PackedWalletProof.SLOT)));
        for(var level:player.server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof ItemEntity drop) {
            for(var expected:protectedStacks)if(!expected.isEmpty())
                require(!ItemStack.isSameItemSameComponents(expected,drop.getItem()),
                        "A marked retained item, nested coin, shard or upgrade was duplicated into a world drop");
        }
    }
    private static JailEscrowJournal ownedPlans(ServerPlayer player,Class<?> type,String fieldName) throws Exception {
        // Read through the production owner's already locked journal; never open a
        // second writer or close an owned handle merely to collect evidence.
        var field=type.getDeclaredField(fieldName);field.setAccessible(true);
        Object journal=((Map<?,?>)field.get(null)).get(player.server);
        require(journal instanceof JailEscrowJournal,"Missing authoritative journal owner: "+type.getSimpleName());
        return (JailEscrowJournal)journal;
    }
    private static void noStaging(ServerPlayer player) throws Exception {
        UUID tx=JailIntegrated.activeTransaction(player.server,player.getUUID());
        require(tx!=null && JailRetainedCustody.active(player),"Missing retained confinement transaction");
        require(JailCustodyService.ledger(player.server).list().isEmpty(),"Off session created a custody binding");
        require(ownedPlans(player,JailIntegrated.class,"plans").list().isEmpty(),"Off session created an inventory custody plan");
        var directory=CertifiedJailSmoke.world.resolve("retained-jail-v1").resolve(tx.toString());
        for(String name:new String[]{"items","remove-effects","return-effects"})
            require(Files.notExists(directory.resolve(name)),"Off session created an item/staging journal: "+name);
        var entry=ownedPlans(player,JailRetainedCustody.class,"RECORDS").read(tx);require(entry!=null,"Retained plan missing");
        var plan=OriginCustodyPort.decode(entry.snapshot());
        require(plan.getString("contract").equals("native-retained-inventory-v1")
                && plan.contains("confiscate_inventory",Tag.TAG_BYTE) && !plan.getBoolean("confiscate_inventory")
                && plan.getByteArray("retained_inventory_digest").length==32,"Retained plan lost its exact policy/digest");
    }
    private static void retainedCell(ServerPlayer player) throws Exception {
        var a=JailData.get(player.server).assignment(player.getUUID());
        require(a!=null && a.geometry()==4 && !a.confiscateInventory(),"Lost retained cell assignment or policy snapshot");
        var first=JailData.get(player.server).location();var jail=a.jail();
        require(jail.dim().equals(first.dim()) && jail.x()==first.x()+12 && jail.z()==first.z()+12
                && player.serverLevel().dimension().location().toString().equals(jail.dim())
                && player.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001,"Retained player escaped assigned cell6");
        require(Files.readString(evidence(player,"transaction")).equals(JailIntegrated.activeTransaction(player.server,player.getUUID()).toString()),
                "Retained transaction changed across custody");
        require(JailCustodyService.quarantined(player),"Retained policy lost quarantine");
        exact(player);noStaging(player);
    }
    private static void recovery(ServerPlayer player,String label) throws Exception {
        exact(player);
        var original=inventory(player);
        var ids=Files.readString(evidence(player,"storage-loss"));
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            require(player.server.getCommands().getDispatcher().execute("anya backpack history restore "+ids,
                    player.createCommandSourceStack())==1,"Real recovery command did not execute");
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        require(original.equals(inventory(player)),"Recovery changed retained inventory");
        exact(player);flag(label);
    }
    private static void seedOff(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();player.getInventory().selected=3;
        var diamonds=new ItemStack(Items.DIAMOND,23);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Retained diamonds slot8"));
        player.getInventory().setItem(8,diamonds);
        var shards=new ItemStack(Items.AMETHYST_SHARD,5);shards.set(DataComponents.CUSTOM_NAME,Component.literal("Retained loose shards slot18"));
        player.getInventory().setItem(18,shards);
        var coin=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),7);
        coin.set(DataComponents.CUSTOM_NAME,Component.literal("Retained exact gold coin stack"));player.getInventory().setItem(23,coin);
        player.setHealth(player.getMaxHealth());
        PackedWalletProof.seed(player);CombinedStorageProof.seed(player);
        CertifiedJailSmoke.flush();CertifiedJailSmoke.saved();
    }
    private static void savedStateControls(ServerPlayer player) throws Exception {
        require(cmd("jailintegrated check Visitor")==1,"Neutral source is ineligible before saved-state controls");
        var persistent=player.getPersistentData();
        require(!persistent.contains("policy_unreviewed_saved_state"),"Fixture key already present");
        persistent.putString("policy_unreviewed_saved_state","retain me on refusal");
        try {
            Custody249Proof.refused(player,"unrelated_saved_state");
            require(persistent.getString("policy_unreviewed_saved_state").equals("retain me on refusal"),"Refusal stripped unrelated saved state");
        } finally { persistent.remove("policy_unreviewed_saved_state"); }
        var original=persistent.getCompound("insanelib").copy();
        require(!original.contains("in_combat"),"Pre-existing combat fixture collision");
        var stale=new CompoundTag();stale.putLong("last_timestamp",0L);
        persistent.getCompound("insanelib").put("in_combat",stale);
        try {
            Custody249Proof.refused(player,"preexisting_combat_not_owned_by_jail");
            require(persistent.getCompound("insanelib").getCompound("in_combat").equals(stale),"Refusal cleared pre-existing combat");
        } finally { persistent.put("insanelib",original.copy()); }
        persistent.getCompound("insanelib").getCompound("nbt_tags").putInt("unreviewed_light_neighbor",1);
        var unrelated=persistent.getCompound("insanelib").copy();
        try {
            Custody249Proof.refused(player,"unrelated_insanelib_state");
            require(unrelated.equals(persistent.getCompound("insanelib")),"Refusal altered unrelated InsaneLib saved state");
        }
        finally { persistent.put("insanelib",original); }
        require(cmd("jailintegrated check Visitor")==1,"Neutral source did not recover after saved-state controls");
    }
    private static void confinementControls(ServerPlayer player) throws Exception {
        var first=JailData.get(player.server).location();
        var cell=new Loc(first.dim(),first.x()+12,first.y(),first.z()+12,first.yaw(),first.pitch());
        var corner=BlockPos.containing(cell.x()-3.5,cell.y()-1,cell.z()-3.5);
        var level=cell.level(player.server);var state=level.getBlockState(corner);
        level.setBlock(corner,Blocks.AIR.defaultBlockState(),3);
        try {
            require(!JailPlacement.intact(player.server,cell,4),"Fixture did not break selected cell integrity");
            Custody249Proof.refused(player,"off_unsafe_confinement");
        } finally { level.setBlock(corner,state,3); }
        require(JailPlacement.intact(player.server,cell,4),"Fixture did not restore the selected cell");
    }
    private static void restrictions(ServerPlayer player) throws Exception {
        int[] calls={0};player.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("uq_policy_forbidden").executes(c->++calls[0]));
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"uq_policy_forbidden");
        require(calls[0]==0,"Off policy permitted prisoner commands");
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),"uq_policy_forbidden");
        require(calls[0]==1,"Console command control failed");
        var j=JailData.get(player.server).assignment(player.getUUID()).jail();
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),"tp Visitor "+(j.x()+50)+" "+j.y()+" "+j.z());
        require(player.isAlive() && player.distanceToSqr(j.x(),j.y(),j.z())<0.0001,"Off policy permitted external teleport");
        float health=player.getHealth();player.invulnerableTime=0;player.hurt(player.damageSources().generic(),2);
        require(player.getHealth()==health,"Off policy lost external-damage protection");
        flag("off_commands_teleport_and_external_damage_still_blocked");
    }

    static void beginOff(ServerPlayer player) throws Exception {
        Custody249Proof.runtime();CenterBuildProof.place(player,player);player.server.getPlayerList().deop(player.getGameProfile());
        defaults(player);
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get() && !JailConfig.JAIL_PACKED_WALLETS.get(),"Other admission defaults changed");
        require(CertifiedJailSmoke.choices().equals(Map.of(ResourceLocation.parse("neoorigins:origin"),ResourceLocation.parse("neoorigins:human"),
                ResourceLocation.parse("neoorigins:class"),ResourceLocation.parse("neoorigins:class_warrior"))),"Native onboarding did not select Human/Warrior");
        FullPackSession.noClaims(player);seedOff(player);
        require(cmd("jailcustody init")==1 && cmd("jailintegrated init")==1,"Cannot initialize disposable journals");
        require(cmd("jail Visitor 6")==0,"Default-disabled admission accepted");
        require(cmd("jailintegrated mode supported")==1 && cmd("jailintegrated check Visitor")==1,"Neutral On baseline failed");
        var copper=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_copper")),9);
        copper.set(DataComponents.CUSTOM_NAME,Component.literal("On-only provider refusal control"));
        require(!copper.isEmpty() && player.getInventory().getItem(27).isEmpty(),"Copper control unavailable");
        player.getInventory().setItem(27,copper);
        Custody249Proof.refused(player,"on_only_copper_inventory_provider");
        require(cmd("jailintegrated check Visitor")==0,"On preflight accepted the unsupported copper provider");
        PolicySettingsProof.set(player,false);
        require(cmd("jailintegrated check Visitor")==1,"Off did not admit the safe retained copper control");
        flag("off_resolves_only_reviewed_inventory_compatibility_refusal");
        PolymorphControls.run(player);flag("polymorph_native_controls");
        CombinedStorageProof.activePocketControl(player);CombinedStorageProof.activeCombatControl(player);
        savedStateControls(player);PolicyTeleportProof.run(player);confinementControls(player);
        player.getInventory().setItem(28,new ItemStack(Items.ENDER_PEARL));
        try { Custody249Proof.refused(player,"off_unreviewed_active_item"); }
        finally { player.getInventory().setItem(28,ItemStack.EMPTY); }
        String progress=UUID.randomUUID().toString();
        player.getPersistentData().getCompound("PlayerPersisted");
        var progression=player.getPersistentData().getCompound("PlayerPersisted").copy();
        progression.putString("uq_disposable_progress",progress);player.getPersistentData().put("PlayerPersisted",progression);
        Files.writeString(evidence(player,"progress"),progress);
        CertifiedJailSmoke.flush();CertifiedJailSmoke.saved();
        Files.write(evidence(player,"retained-inventory"),OriginCustodyPort.encode(inventory(player)));
        Policy249Writes.arm(player.getUUID());
        require(cmd("jail Visitor 6")==1,"Eligible Off admission failed");
        UUID tx=JailIntegrated.activeTransaction(player.server,player.getUUID());require(tx!=null,"Off transaction missing");
        Files.writeString(evidence(player,"transaction"),tx.toString());
        retainedCell(player);flag("off_admission_has_no_confiscation_inventory_write_or_return_staging");
        recovery(player,"off_held_recovery_did_not_create_copy");
        PolicySettingsProof.set(player,true);
        retainedCell(player);flag("on_flip_did_not_retroactively_confiscate_active_off_session");
        player.setInvulnerable(false);restrictions(player);
        require(!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN)
                && !player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY),"Global death gamerules changed");
        PolicyTeleportProof.holdIncoming(player);
        CombinedStorageProof.seedLightTransition(player);
        lightBefore=player.getPersistentData().getCompound("insanelib").getCompound("nbt_tags").getInt("sky_light");
        corpse=player;firstDeaths=deaths(player);escapedAt=player.server.getTickCount();escapedNanos=System.nanoTime();
        deadline=escapedAt+2400;
        PolicyTeleportProof.beforeEscape(player);
        CertifiedJailSmoke.cross();
        require(!player.isAlive() && deaths(player)==firstDeaths+1,"Off escape did not produce one actual death");
        exact(player);flag("off_actual_escape_death_retained_original_inventory");
    }
    static void traceOff(ServerPlayer player) throws Exception {
        if(!replacementSeen && player!=corpse && player.isAlive() && player.connection.player==player) {
            replacementSeen=true;
            var timing=new LinkedHashMap<String,Object>();timing.put("server_ticks",player.server.getTickCount()-escapedAt);
            timing.put("elapsed_ms",(System.nanoTime()-escapedNanos)/1_000_000L);
            Files.writeString(results.resolve("full249-respawn-timing.json"),new GsonBuilder().setPrettyPrinting().create().toJson(timing));
        }
        if(!player.isAlive()) {
            var row=new LinkedHashMap<String,Object>();row.put("game_time",player.serverLevel().getGameTime());
            row.put("light_before",lightBefore);
            row.put("live_light",player.getPersistentData().getCompound("insanelib").getCompound("nbt_tags").toString());
            Files.writeString(results.resolve("retained-corpse-light.json"),new GsonBuilder().setPrettyPrinting().create().toJson(row));
        }
    }
    static void afterRespawnOff(ServerPlayer player) throws Exception {
        if(!player.isAlive())return;
        require(player!=corpse && player.connection.player==player && deaths(player)==firstDeaths+1,"Off did not replace the real dead player once");
        require(ProductionObserver.automaticCalls==1 && ProductionObserver.automaticCompletions==1
                && ProductionObserver.clientRespawnRequests==0,"Off respawn did not use the no-button server path");
        var path=results.resolve("player-only-client-Visitor-1.json");if(!Files.isRegularFile(path))return;
        com.google.gson.JsonObject view;
        try { view=JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
        catch(RuntimeException partial){return;}
        if(view.getAsJsonArray("verified_respawns").isEmpty())return;
        if(view.get("kill_packets").getAsInt()!=1 || view.get("respawn_packets_after_kill").getAsInt()!=1)return;
        require(view.get("respawn_actions_sent").getAsInt()==0,"Off client sent a Respawn action");
        retainedCell(player);
        PolicyTeleportProof.afterEscape(player);
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get(),"Off snapshot reverted the current On config");
        require(!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                && !player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),"Off changed global death gamerules");
        int after=player.getPersistentData().getCompound("insanelib").getCompound("nbt_tags").getInt("sky_light");
        require(after!=lightBefore,"Required native corpse lighting churn was not observed");
        proof.put("retained_light_transition",Map.of("seeded",lightBefore,"after_native_respawn",after));
        var observation=Policy249Writes.snapshot();
        var clones=(Map<?,?>)observation.get("native_respawn_inventory_copies");
        require(Integer.valueOf(1).equals(clones.get("replaceWith")),"Expected exactly one native inventory replaceWith during respawn: "+clones);
        proof.put("inventory_write_observations",observation);
        flag("off_no_button_respawn_preserved_inventory_with_lighting_churn");
        flag("escape_native_respawn_in_cell_six_without_client_action");
        require(cmd("jailintegrated mode disabled")==1,"Could not disable new admissions before restart");
        pass();
    }
    static void resumedOff(ServerPlayer player) throws Exception {
        Custody249Proof.runtime();Policy249Writes.arm(player.getUUID());
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get() && !JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Policy/admission configuration changed at restart");
        retainedCell(player);
        PolicyTeleportProof.holdIncoming(player);
        require(Files.readString(evidence(player,"progress")).equals(player.getPersistentData().getCompound("PlayerPersisted").getString("uq_disposable_progress")),
                "Native persisted progression changed across Off death/restart");
        flag("off_to_on_active_policy_survived_restart_without_confiscation");
        recovery(player,"off_held_recovery_after_restart_did_not_create_copy");
        PolicyTeleportProof.beforeRelease(player);
        require(cmd("unjail Visitor")==1,"Off release after restart failed");
        PolicyTeleportProof.afterRelease(player);
        exact(player);
        require(JailData.get(player.server).assignment(player.getUUID())==null
                && JailIntegrated.activeTransaction(player.server,player.getUUID())==null && !JailCustodyService.quarantined(player),"Off release left confinement active");
        require(CertifiedJailSmoke.choices().get(ResourceLocation.parse("neoorigins:class")).equals(ResourceLocation.parse("neoorigins:class_warrior"))
                && player.getMaxHealth()==22,"Off release did not restore independent origin safeguards");
        require(cmd("unjail Visitor")==0,"Duplicate Off release accepted");
        recovery(player,"off_stale_recovery_after_release_did_not_duplicate");
        recovery(player,"off_duplicate_recovery_after_release_did_not_duplicate");
        require(JailCustodyService.ledger(player.server).list().isEmpty(),"Off release created return custody");
        proof.put("inventory_write_observations",Policy249Writes.snapshot());
        flag("off_release_kept_same_inventory_wallet_and_marked_upgraded_bag_in_original_slots");
        flag("off_release_has_no_inventory_write_or_return_staging");
        sequentialReceipts(player);
        pass();
    }
    private static UUID onRoundTrip(ServerPlayer player,CompoundTag expected,String label) throws Exception {
        require(JailConfig.JAIL_CONFISCATE_INVENTORY.get() && cmd("jailintegrated check Visitor")==1,
                "Following On preflight rejected the completed receipt chain: "+label);
        require(cmd("jail Visitor 6")==1,"Following On admission failed: "+label);
        var assignment=JailData.get(player.server).assignment(player.getUUID());
        UUID tx=JailIntegrated.activeTransaction(player.server,player.getUUID());
        require(assignment!=null && assignment.confiscateInventory() && tx!=null
                && player.getInventory().isEmpty(),"Following On session did not actually confiscate: "+label);
        require(cmd("unjail Visitor")==1,"Following On release failed: "+label);
        require(expected.equals(inventory(player)) && !JailCustodyService.quarantined(player)
                && JailData.get(player.server).assignment(player.getUUID())==null
                && JailIntegrated.activeTransaction(player.server,player.getUUID())==null,
                "Following On session lost, duplicated or moved inventory: "+label);
        PackedWalletProof.returned(player);noDuplicateDrops(player);
        require(cmd("unjail Visitor")==0,"Duplicate following On release succeeded: "+label);
        flag(label);
        return tx;
    }
    private static void sequentialReceipts(ServerPlayer player) throws Exception {
        // This independent control runs only after the required Off death/restart/
        // release proof and its zero-write observation have already been recorded.
        var copper=player.getInventory().getItem(27);
        require(BuiltInRegistries.ITEM.getKey(copper.getItem()).toString().equals("lightmanscurrency:coin_copper")
                && copper.getCount()==9 && copper.getHoverName().getString().equals("On-only provider refusal control"),
                "Only the test-created unsupported copper may be removed between free sessions");
        player.getInventory().setItem(27,ItemStack.EMPTY);
        CertifiedJailSmoke.flush();CertifiedJailSmoke.saved();
        var expected=inventory(player);
        require(cmd("jailintegrated mode supported")==1,"Could not enable the separate sequential admission control");
        UUID firstOn=onRoundTrip(player,expected,"on_after_completed_off_returns_same_slots_once");
        var receipts=inventoryReceipts(player);
        require(receipts.contains(JailVanillaPort.RESTORED),"Completed On session did not retain its return receipt");
        int bindings=JailCustodyService.ledger(player.server).list().size();
        int itemPlans=ownedPlans(player,JailIntegrated.class,"plans").list().size();
        PolicySettingsProof.set(player,false);
        require(cmd("jailintegrated check Visitor")==1,"Off preflight rejected a completed On return receipt");
        Policy249Writes.arm(player.getUUID());
        require(cmd("jail Visitor 6")==1,"Off admission after a completed On return failed");
        UUID off=JailIntegrated.activeTransaction(player.server,player.getUUID());
        require(off!=null && !off.equals(firstOn) && JailRetainedCustody.active(player)
                && !JailData.get(player.server).assignment(player.getUUID()).confiscateInventory(),
                "Sequential Off admission used the wrong policy/transaction");
        require(expected.equals(inventory(player)) && receipts.equals(inventoryReceipts(player)),
                "Sequential Off admission altered prior On receipts or inventory");
        require(JailCustodyService.ledger(player.server).list().size()==bindings
                && ownedPlans(player,JailIntegrated.class,"plans").list().size()==itemPlans,
                "Sequential Off admission created an inventory binding or plan");
        require(cmd("unjail Visitor")==1,"Sequential Off release failed");
        require(expected.equals(inventory(player)) && receipts.equals(inventoryReceipts(player))
                && !JailCustodyService.quarantined(player),"Sequential Off release altered prior On receipts or inventory");
        Policy249Writes.noJailWrites();PackedWalletProof.returned(player);noDuplicateDrops(player);
        proof.put("sequential_off_inventory_write_observations",Policy249Writes.snapshot());
        flag("on_return_receipts_survive_later_off_admission_and_release_unchanged");
        PolicySettingsProof.set(player,true);
        UUID lastOn=onRoundTrip(player,expected,"on_after_on_off_receipt_chain_returns_same_slots_once");
        require(!lastOn.equals(firstOn) && !lastOn.equals(off),"Sequential admission reused an earlier transaction");
        proof.put("sequential_policy_transactions",Map.of("first_on",firstOn.toString(),"off",off.toString(),"last_on",lastOn.toString()));
        require(cmd("jailintegrated mode disabled")==1,"Could not close the disposable sequential admission control");
    }
}
