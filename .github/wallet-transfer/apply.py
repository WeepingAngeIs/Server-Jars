from pathlib import Path
import subprocess
root=Path('minecraft-jail/src/main/java/dev/unyielding/jail')
for name in ('JailConfig.java','JailAdmissions.java','JailIntegrated.java','JailEquipmentPort.java','JailEquipmentEffects.java'):
    expected=subprocess.check_output(['git','show','9fc1f509000254a81ac3d6fc79a1dac4f51d323e:'+str(root/name)])
    assert (root/name).read_bytes()==expected,('Baseline changed',name)
def edit(name,old,new):
    p=root/name;s=p.read_text();assert s.count(old)==1,(name,old,s.count(old));p.write_text(s.replace(old,new))
edit('JailConfig.java','    static {','    public static final ModConfigSpec.BooleanValue PACKED_WALLET_ADMISSIONS;\n    static {')
edit('JailConfig.java','        b.pop();','        PACKED_WALLET_ADMISSIONS=b.comment("Separately opt in to the bounded packed leather-wallet contract. One non-selected main-inventory wallet, no upgrades or bank access, only audited gold coins. Equipped wallets and nonempty Pokemon parties remain refused. Recovery of existing records does not depend on this switch.").define("packed_wallet_admissions",false);\n        b.pop();')
edit('JailAdmissions.java','if(JailConfig.JAIL_CERTIFIED_ADMISSIONS.get())return auditedContract(p);','''if(JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()){
            String contract=auditedContract(p);
            if(contract.equals(JailIntegrated.PACKED_WALLET)&&!JailConfig.PACKED_WALLET_ADMISSIONS.get())throw new IOException("Packed-wallet admissions are disabled; this separate contract requires explicit console opt-in");
            return contract;
        }''')
edit('JailAdmissions.java','static String auditedContract(ServerPlayer p){return JailXp.nonzero(p)?JailIntegrated.RETAINED_XP:JailIntegrated.SUPPORTED;}','static String auditedContract(ServerPlayer p){return JailPackedWallet.present(p)?JailIntegrated.PACKED_WALLET:JailXp.nonzero(p)?JailIntegrated.RETAINED_XP:JailIntegrated.SUPPORTED;}')
edit('JailIntegrated.java','static final String SUPPORTED="native-supported-v2", RETAINED_XP="native-retained-xp-v3";','static final String SUPPORTED="native-supported-v2", RETAINED_XP="native-retained-xp-v3", PACKED_WALLET="native-packed-wallet-v4";')
edit('JailIntegrated.java','return contract.equals(SUPPORTED)||contract.equals(RETAINED_XP);','return contract.equals(SUPPORTED)||contract.equals(RETAINED_XP)||contract.equals(PACKED_WALLET);')
edit('JailIntegrated.java','new JailEquipmentPort(player,id,retired,audited(contract))','new JailEquipmentPort(player,id,retired,audited(contract),contract.equals(PACKED_WALLET))')
edit('JailIntegrated.java','contract.equals(SUPPORTED)?new JailCertifiedOriginPort','audited(contract)?new JailCertifiedOriginPort')
edit('JailIntegrated.java','Set.of("human-empty-v1",EXPANDED,SUPPORTED,RETAINED_XP)','Set.of("human-empty-v1",EXPANDED,SUPPORTED,RETAINED_XP,PACKED_WALLET)')
edit('JailIntegrated.java','return "Supported native combination; admissions "+','return "Supported native combination ("+ports.contract()+", packed-wallet opt-in "+JailConfig.PACKED_WALLET_ADMISSIONS.get()+"); admissions "+')
edit('JailIntegrated.java','.then(Commands.literal("disabled").executes(c->mode(c.getSource(),false))))','.then(Commands.literal("disabled").executes(c->mode(c.getSource(),false))).then(Commands.literal("packed-wallet").executes(c->packedWalletMode(c.getSource()))))')
edit('JailIntegrated.java','    private static int mode(CommandSourceStack actor,boolean enabled){','''    private static void returnCheckpoint(JailVanillaCustody.RestoreBoundary boundary)throws IOException {
        // Same hosted-disposable-only setter as other existing fault checkpoints.
        if(boundary==JailVanillaCustody.RestoreBoundary.MUTATED)checkpoint.at(Boundary.ITEMS_RETURN_MUTATED);
        if(boundary==JailVanillaCustody.RestoreBoundary.PLAYER_DURABLE)checkpoint.at(Boundary.ITEMS_RETURN_DURABLE);
    }
    private static int packedWalletMode(CommandSourceStack actor){
        if(mode(actor,true)!=1)return 0;
        try{JailConfig.PACKED_WALLET_ADMISSIONS.set(true);JailConfig.PACKED_WALLET_ADMISSIONS.save();actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Packed-wallet contract enabled; one packed leather wallet only. Equipped wallets, bank links, nonempty parties and uncertain recovery remain blocked."),true);return 1;}
        catch(RuntimeException bad){JailConfig.PACKED_WALLET_ADMISSIONS.set(false);return JailText.fail(actor,bad.getMessage());}
    }
    private static int mode(CommandSourceStack actor,boolean enabled){''')
edit('JailIntegrated.java','dev.unyielding.jail.JailConfig.JAIL_CERTIFIED_ADMISSIONS.set(enabled);','JailConfig.PACKED_WALLET_ADMISSIONS.set(false);dev.unyielding.jail.JailConfig.JAIL_CERTIFIED_ADMISSIONS.set(enabled);')
edit('JailIntegrated.java','ORIGIN_RETURNED, ITEMS_RETURNED,','ORIGIN_RETURNED, ITEMS_RETURN_MUTATED, ITEMS_RETURN_DURABLE, ITEMS_RETURNED,')
p=root/'JailIntegrated.java';s=p.read_text();old='JailVanillaCustody.restoreForDisposableTest(items,b.id(),guarded(items,origins),phase->authority());';assert s.count(old)==2;p.write_text(s.replace(old,'JailVanillaCustody.restoreForDisposableTest(items,b.id(),guarded(items,origins),phase->{authority();returnCheckpoint(phase);});'))
edit('JailEquipmentPort.java','    final boolean certified;','    final boolean certified;\n    final boolean packedWallet;')
edit('JailEquipmentPort.java','''        this.player=player;this.transaction=transaction;this.vanilla=new JailVanillaPort(player,transaction,retired);''','''        this(player,transaction,retired,certified,false);
    }
    public JailEquipmentPort(ServerPlayer player, UUID transaction, UUID retired, boolean certified, boolean packedWallet) throws IOException {
        if(packedWallet&&!certified)throw new IOException("Packed wallets require the audited contract");
        this.packedWallet=packedWallet;
        this.player=player;this.transaction=transaction;this.vanilla=new JailVanillaPort(player,transaction,retired);''')
edit('JailEquipmentPort.java','return certified?"native-default-equipment-packed-coins-v2":"native-default-equipment-v1";','return packedWallet?"native-default-equipment-packed-wallet-v1":certified?"native-default-equipment-packed-coins-v2":"native-default-equipment-v1";')
edit('JailEquipmentPort.java','        JailEquipmentEffects.supported(player,certified);','        JailEquipmentEffects.supported(player,certified,packedWallet);\n        if(packedWallet)JailPackedWallet.validateInventory(player,player.getInventory());')
edit('JailEquipmentPort.java','        vanilla.planRestore(decode(snapshot).getByteArray("vanilla"),id);','        if(packedWallet)JailPackedWallet.validateSnapshot(player,decode(snapshot).getByteArray("vanilla"));\n        vanilla.planRestore(decode(snapshot).getByteArray("vanilla"),id);')
edit('JailEquipmentEffects.java','    static void supported(ServerPlayer player,boolean packedCoins)throws IOException {\n','    static void supported(ServerPlayer player,boolean packedCoins)throws IOException {supported(player,packedCoins,false);}\n    static void supported(ServerPlayer player,boolean packedCoins,boolean packedWallet)throws IOException {\n')
edit('JailEquipmentEffects.java','(!vanilla&&!(packedCoins&&JailPackedItems.coin(player,i,stack)))','(!vanilla&&!(packedCoins&&JailPackedItems.coin(player,i,stack))&&!(packedWallet&&JailPackedWallet.accepts(player,i,stack)))')
edit('JailEquipmentEffects.java','Only vanilla equipment and audited unequipped coins are supported','Only vanilla equipment and the explicitly selected audited packed-item contract are supported')
