package dev.unyielding.jail;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PackedWalletPolicyTest {
    void envelope(int slot,int selected,int count,int capacity,int upgrades,Set<String> components,Set<String> data) throws IOException {
        PackedWalletPolicy.envelope(slot,selected,count,capacity,upgrades,components,data);
    }
    void valid(int slot,int selected,int count,int capacity,int upgrades) throws IOException {
        envelope(slot,selected,count,capacity,upgrades,Set.of(PackedWalletPolicy.DATA,"minecraft:custom_name"),PackedWalletPolicy.DATA_KEYS);
    }
    @Test void acceptsFinitePackedEnvelope() {assertDoesNotThrow(()->valid(12,0,1,3,0));}
    @Test void rejectsSelectedHand() {assertThrows(IOException.class,()->valid(0,0,1,3,0));}
    @Test void rejectsEquipmentAndInvalidSlots() {for(int slot:new int[]{-1,36,40,41})assertThrows(IOException.class,()->valid(slot,0,1,3,0));}
    @Test void rejectsInvalidSelection() {for(int slot:new int[]{-1,9,36})assertThrows(IOException.class,()->valid(12,slot,1,3,0));}
    @Test void refusesStackedWallets() {for(int n:new int[]{0,2,64})assertThrows(IOException.class,()->valid(12,0,n,3,0));}
    @Test void refusesUpgradesAndCapacity() {assertThrows(IOException.class,()->valid(12,0,1,3,1));assertThrows(IOException.class,()->valid(12,0,1,0,0));assertThrows(IOException.class,()->valid(12,0,1,17,0));}
    @Test void refusesUnknownAndRemovedComponents() {for(String c:Set.of("other:storage","!lightmanscurrency:wallet_capacity","minecraft:container"))assertThrows(IOException.class,()->envelope(12,0,1,3,0,Set.of(PackedWalletPolicy.DATA,c),PackedWalletPolicy.DATA_KEYS));}
    @Test void requiresInitializedExactDataSchema() {assertThrows(IOException.class,()->envelope(12,0,1,3,0,Set.of(),PackedWalletPolicy.DATA_KEYS));assertThrows(IOException.class,()->envelope(12,0,1,3,0,Set.of(PackedWalletPolicy.DATA),Set.of("Items","AutoExchange","BonusSlots","Bank")));}
    @Test void acceptsNamedPhysicalGoldCoins() {assertDoesNotThrow(()->PackedWalletPolicy.coin("lightmanscurrency:coin_gold",11,64,Set.of("minecraft:custom_name")));}
    @Test void rejectsOtherCoinProviders() {assertThrows(IOException.class,()->PackedWalletPolicy.coin("minecraft:gold_ingot",11,64,Set.of()));}
    @Test void rejectsNestedStorage() {assertThrows(IOException.class,()->PackedWalletPolicy.coin("lightmanscurrency:coin_gold",11,64,Set.of("minecraft:container")));}
    @Test void rejectsCoinOverflow() {for(int n:new int[]{0,65})assertThrows(IOException.class,()->PackedWalletPolicy.coin("lightmanscurrency:coin_gold",n,64,Set.of()));}
    @Test void requiresSingleWalletOwner() {assertDoesNotThrow(()->PackedWalletPolicy.single(1));for(int n:new int[]{0,2})assertThrows(IOException.class,()->PackedWalletPolicy.single(n));}
}
