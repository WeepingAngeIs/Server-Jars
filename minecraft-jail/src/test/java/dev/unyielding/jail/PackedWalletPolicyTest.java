package dev.unyielding.jail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class PackedWalletPolicyTest {
    private boolean accepts(int slot,int selected,int count,int wallets,int capacity,int encoded,int bonus,boolean exchange,boolean occupied) {
        return PackedWalletPolicy.accepts(new PackedWalletPolicy.Shape(slot,selected,count,wallets,capacity,encoded,bonus,exchange,occupied));
    }
    @Test void reviewedBounds() { for(int slot=9;slot<36;slot++) assertTrue(accepts(slot,2,1,1,6,6,0,false,true)); }
    @Test void excludesHotbarAndEquipment() { for(int slot:new int[]{-1,0,8,36,40}) assertFalse(accepts(slot,2,1,1,6,6,0,false,true)); }
    @Test void excludesSelected() { assertFalse(accepts(32,32,1,1,6,6,0,false,true)); }
    @Test void singletonOnly() { for(int count:new int[]{0,2,64}) assertFalse(accepts(32,2,count,1,6,6,0,false,true)); }
    @Test void oneWalletOnly() { for(int wallets:new int[]{0,2,5}) assertFalse(accepts(32,2,1,wallets,6,6,0,false,true)); }
    @Test void exactCapacity() { for(int capacity:new int[]{0,5,7,78}) assertFalse(accepts(32,2,1,1,capacity,6,0,false,true)); }
    @Test void noTruncatedOrHiddenSlots() { for(int encoded:new int[]{0,5,7,78}) assertFalse(accepts(32,2,1,1,6,encoded,0,false,true)); }
    @Test void noUpgrades() { for(int bonus:new int[]{-1,1,6}) assertFalse(accepts(32,2,1,1,6,6,bonus,false,true)); }
    @Test void noAutoExchange() { assertFalse(accepts(32,2,1,1,6,6,0,true,true)); }
    @Test void occupiedContractOnly() { assertFalse(accepts(32,2,1,1,6,6,0,false,false)); assertFalse(PackedWalletPolicy.accepts(null)); }
}
