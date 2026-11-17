package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

/** Only copied into the independent hosted test mod, never the production JAR. */
public final class PackedWalletProof {
    static final int SLOT=32;
    static final String DATA="io.github.lightman314.lightmanscurrency.common.items.data.WalletData";
    static final Map<String,Object> evidence=new LinkedHashMap<>();
    static void require(boolean v,String why){if(!v)throw new IllegalStateException(why);}
    static Object call(Object target,String method)throws Exception{return target.getClass().getMethod(method).invoke(target);}
    @SuppressWarnings({"rawtypes","unchecked"}) static void data(ItemStack wallet,List<ItemStack> coins,boolean exchange,int bonus)throws Exception{
        Object value=Class.forName(DATA).getConstructor(List.class,boolean.class,int.class).newInstance(List.copyOf(coins),exchange,bonus);
        wallet.set((DataComponentType)BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("lightmanscurrency:wallet_data")),value);
    }
    static Object data(ItemStack wallet){return wallet.get(BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("lightmanscurrency:wallet_data")));}
    @SuppressWarnings("unchecked") static List<ItemStack> coins(ItemStack wallet)throws Exception{return ((List<ItemStack>)call(data(wallet),"items")).stream().map(ItemStack::copy).toList();}
    static CompoundTag encoded(ServerPlayer p,ItemStack stack){return (CompoundTag)stack.save(p.registryAccess());}
    static void seed(ServerPlayer p)throws Exception{
        require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")),"Not hosted");
        require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Packed-wallet option must default off in a fresh fixture");
        require(p.getInventory().getItem(SLOT).isEmpty(),"Fixture slot already occupied");
        ItemStack wallet=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:wallet_leather")));
        wallet.set(DataComponents.CUSTOM_NAME,Component.literal("Escrow wallet "+UUID.randomUUID()));
        wallet.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Preserve the whole stack, not a balance"))));
        List<ItemStack> coins=new ArrayList<>();for(int i=0;i<6;i++)coins.add(ItemStack.EMPTY);
        for(int slot:new int[]{0,5}){
            ItemStack coin=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),slot==0?11:7);
            coin.set(DataComponents.CUSTOM_NAME,Component.literal("Marked coin slot "+slot));
            coin.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Exact physical denomination"))));coins.set(slot,coin);
        }
        data(wallet,coins,false,0);p.getInventory().setItem(SLOT,wallet);
        Files.write(CertifiedJailSmoke.world.resolve("packed-wallet-expected.nbt"),OriginCustodyPort.encode(encoded(p,wallet)));
        JailConfig.JAIL_PACKED_WALLETS.set(true);JailConfig.JAIL_PACKED_WALLETS.save();
        evidence.put("fresh_default_off",true);
    }
    static CompoundTag expected()throws Exception{return OriginCustodyPort.decode(Files.readAllBytes(CertifiedJailSmoke.world.resolve("packed-wallet-expected.nbt")));}
    static void returned(ServerPlayer p)throws Exception{
        require(encoded(p,p.getInventory().getItem(SLOT)).equals(expected()),"Wallet or nested coin state/slot changed");
        int copies=0;for(int i=0;i<p.getInventory().getContainerSize();i++){
            var stack=p.getInventory().getItem(i);if(!stack.isEmpty()&&encoded(p,stack).equals(expected()))copies++;
        }
        require(copies==1,"Duplicate or missing physical wallet");evidence.put("exact_wallet_and_nested_slots_returned",true);
    }
    static void held(ServerPlayer p,UUID id)throws Exception{
        require(p.getInventory().isEmpty(),"Wallet remains player-accessible during held custody");
        try(var journal=new JailEscrowJournal(CertifiedJailSmoke.world.resolve("integrated-jail-v1").resolve(id.toString()).resolve("items"))){
            var record=journal.read(id);require(record!=null&&record.owner().equals(p.getUUID()),"Foreign/missing item owner");
            require(record.state()==JailEscrowJournal.State.HELD&&Arrays.equals(record.snapshot(),CertifiedJailSmoke.original),"Full wallet escrow snapshot changed");
        }
        try(var plans=new JailEscrowJournal(CertifiedJailSmoke.world.resolve("integrated-jail-v1"))){
            var plan=OriginCustodyPort.decode(plans.read(id).snapshot());
            require("native-packed-wallet-v1".equals(plan.getString("contract")),"Wallet mislabelled as old custody contract");
        }
        evidence.put("complete_wallet_owned_by_bound_escrow",true);
    }
    static void refuse(String label)throws Exception{
        var p=CertifiedJailSmoke.target;byte[] before=CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory();
        var party=Cobblemon.INSTANCE.getStorage().getParty(p).saveToNBT(new CompoundTag(),p.registryAccess()).copy();
        int size=JailCustodyService.ledger(p.server).list().size();
        require(CertifiedJailSmoke.command("jail Visitor")==0,"Unsupported wallet admission: "+label);
        require(Arrays.equals(before,CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory()),"Refusal changed inventory: "+label);
        require(party.equals(Cobblemon.INSTANCE.getStorage().getParty(p).saveToNBT(new CompoundTag(),p.registryAccess())),"Refusal changed Pokemon");
        require(size==JailCustodyService.ledger(p.server).list().size()&&JailData.get(p.server).assignment(p.getUUID())==null,"Refusal created custody: "+label);
        evidence.put("refused_"+label,true);
    }
    static void negatives()throws Exception{
        var p=CertifiedJailSmoke.target;ItemStack good=p.getInventory().getItem(SLOT).copy();
        JailConfig.JAIL_PACKED_WALLETS.set(false);refuse("disabled_wallet_option");JailConfig.JAIL_PACKED_WALLETS.set(true);
        ItemStack bad=good.copy();data(bad,coins(good),true,0);p.getInventory().setItem(SLOT,bad);refuse("auto_exchange");
        bad=good.copy();data(bad,coins(good),false,1);p.getInventory().setItem(SLOT,bad);refuse("upgraded_wallet");
        bad=good.copy();var nested=new ArrayList<>(coins(good));nested.set(0,new ItemStack(Items.DIAMOND));data(bad,nested,false,0);p.getInventory().setItem(SLOT,bad);refuse("noncoin_contents");
        bad=good.copy();nested=new ArrayList<>(coins(good));nested.add(new ItemStack(Items.GOLD_INGOT));data(bad,nested,false,0);p.getInventory().setItem(SLOT,bad);refuse("extra_hidden_slot");
        bad=good.copy();nested=new ArrayList<>(coins(good));nested.getFirst().set(DataComponents.CUSTOM_DATA,CustomData.of(new CompoundTag()));data(bad,nested,false,0);p.getInventory().setItem(SLOT,bad);refuse("unknown_coin_component");
        bad=good.copy();bad.set(DataComponents.CUSTOM_DATA,CustomData.of(new CompoundTag()));p.getInventory().setItem(SLOT,bad);refuse("unknown_wallet_component");
        bad=good.copy();bad.setCount(2);p.getInventory().setItem(SLOT,bad);refuse("wallet_stack_count");
        p.getInventory().setItem(SLOT,good.copy());require(p.getInventory().getItem(33).isEmpty(),"Extra fixture slot occupied");p.getInventory().setItem(33,good.copy());refuse("multiple_wallets");p.getInventory().setItem(33,ItemStack.EMPTY);
        require(p.getInventory().getItem(8).isEmpty(),"Hotbar fixture slot occupied");p.getInventory().setItem(SLOT,ItemStack.EMPTY);p.getInventory().setItem(8,good.copy());refuse("hotbar_wallet");p.getInventory().setItem(8,ItemStack.EMPTY);p.getInventory().setItem(SLOT,good.copy());
        p.totalExperience=1;refuse("retained_xp_combination");p.totalExperience=0;
        var store=Cobblemon.INSTANCE.getStorage().getParty(p);var pokemon=PokemonProperties.Companion.parse("eevee level=7 shiny=yes"," ","=").create(p);require(store.add(pokemon),"Synthetic party setup failed");refuse("nonempty_party");require(store.remove(pokemon),"Synthetic party cleanup failed");
        // Native provider remains unapproved while equipped. Use native setters only on synthetic data.
        Object handler=Class.forName("io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler").getMethod("get",net.minecraft.world.entity.LivingEntity.class).invoke(null,p);
        var setter=handler.getClass().getMethod("setWallet",ItemStack.class);
        p.getInventory().setItem(SLOT,ItemStack.EMPTY);setter.invoke(handler,good.copy());CertifiedJailSmoke.flush();refuse("equipped_wallet");setter.invoke(handler,ItemStack.EMPTY);CertifiedJailSmoke.flush();p.getInventory().setItem(SLOT,good.copy());
        CertifiedJailSmoke.saved();evidence.put("negative_cases_complete",true);
    }
    static void occupiedDestination(ServerPlayer p)throws Exception{
        require(p.getInventory().isEmpty(),"Not held before destination probe");
        ItemStack conflict=new ItemStack(Items.NETHERITE_INGOT,3);conflict.set(DataComponents.CUSTOM_NAME,Component.literal("Do not overwrite this destination"));
        p.getInventory().setItem(SLOT,conflict);CertifiedJailSmoke.saved();byte[] before=CertifiedJailSmoke.items(CertifiedJailSmoke.last,null).current().inventory();
        require(CertifiedJailSmoke.command("unjail Visitor")==0&&CertifiedJailSmoke.command("unjail Visitor")==0,"Occupied destination accepted");
        require(Arrays.equals(before,CertifiedJailSmoke.items(CertifiedJailSmoke.last,null).current().inventory())&&ItemStack.matches(conflict,p.getInventory().getItem(SLOT)),"Conflict overwritten or wallet duplicated");
        require(JailCustodyService.quarantined(p),"Conflict removed quarantine");
        // Remove only the test-created conflict, not production contents or journal evidence.
        p.getInventory().setItem(SLOT,ItemStack.EMPTY);CertifiedJailSmoke.saved();evidence.put("occupied_destination_refused_without_overwrite",true);
    }
    static void report(int phase,ServerPlayer p,UUID transaction)throws IOException{
        evidence.put("commit",System.getenv("GITHUB_SHA"));evidence.put("phase",phase);evidence.put("transaction",String.valueOf(transaction));
        evidence.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));evidence.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));
        evidence.put("actual_nonempty_wallet_admission",true);evidence.put("party_admission_enabled",false);
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/packed-wallet-phase"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
    }
}
