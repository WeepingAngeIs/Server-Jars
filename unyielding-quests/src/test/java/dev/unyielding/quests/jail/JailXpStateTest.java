package dev.unyielding.quests.jail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailXpStateTest {
    @Test void nativeFieldsRemainIndependentAndExact(){
        var s=new JailXpState(17,9,Math.nextDown(1f),9876);
        assertEquals(17,s.level());assertEquals(9,s.total());assertEquals(Math.nextDown(1f),s.progress());assertEquals(9876,s.score());
        assertEquals(s,new JailXpState(17,9,Math.nextDown(1f),9876));
    }
    @Test void rejectInvalidXpRanges(){
        for(float f:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-0.5f,1f})assertThrows(IllegalArgumentException.class,()->new JailXpState(1,1,f,0));
        assertThrows(IllegalArgumentException.class,()->new JailXpState(-1,1,0,0));
        assertThrows(IllegalArgumentException.class,()->new JailXpState(10001,1,0,0));
        assertThrows(IllegalArgumentException.class,()->new JailXpState(1,-1,0,0));
    }
}
