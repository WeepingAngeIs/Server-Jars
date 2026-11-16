package dev.unyielding.quests.jail;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailHealthShiftTest {
    @Test void fullAndDamagedHealthPreserveTheSameDeficit(){
        for(float hp:new float[]{22,20,8,3})try{float neutral=JailHealthShift.apply(hp,2,true);assertEquals(22-hp,20-neutral);assertEquals(hp,JailHealthShift.apply(neutral,2,false));}catch(IOException bad){fail(bad);}
    }
    @Test void legitimateHeldDamageAndHealingArePreserved()throws Exception {
        float neutral=JailHealthShift.apply(20,2,true);assertEquals(17,JailHealthShift.apply(neutral-3,2,false));assertEquals(21,JailHealthShift.apply(neutral+1,2,false));
    }
    @Test void noLethalOrInvalidShiftIsPermitted(){
        for(float hp:new float[]{0,1,2,Float.NaN,Float.POSITIVE_INFINITY})assertThrows(IOException.class,()->JailHealthShift.apply(hp,2,true));
        assertThrows(IOException.class,()->JailHealthShift.apply(20,-2,true));assertThrows(IOException.class,()->JailHealthShift.apply(20,20,true));
    }
}
