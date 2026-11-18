package dev.unyielding.jail;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailNbtTest {
    @Test void nestedFloatingPayloadsRoundTripWithAllIeeeBitsIntact() throws Exception {
        int floatPayload=0xffc43210;
        long doublePayload=0x7ff8000000012345L;
        var root=new CompoundTag();
        var nested=new CompoundTag();
        var list=new ListTag();
        list.add(DoubleTag.valueOf(Double.longBitsToDouble(doublePayload)));
        nested.put("amount",list);
        nested.putFloat("base",Float.intBitsToFloat(floatPayload));
        root.put("attributes",nested);
        var decoded=JailNbt.decode(JailNbt.encode(root)).getCompound("attributes");
        assertEquals(floatPayload,Float.floatToRawIntBits(decoded.getFloat("base")));
        assertEquals(doublePayload,Double.doubleToRawLongBits(decoded.getList("amount",Tag.TAG_DOUBLE).getDouble(0)));
        assertNull(JailNbt.mismatch(nested,decoded,"root"));
        decoded.putFloat("base",Float.intBitsToFloat(floatPayload+1));
        assertNotNull(JailNbt.mismatch(nested,decoded,"root"));
    }

    @Test void originCallbacksRetainExactNaNsAndRejectOneBitOrAnUnplannedField() throws Exception {
        var plan=new CompoundTag();
        plan.putDouble("base",Double.longBitsToDouble(0x7ff8000000000012L));
        var actual=JailNbt.decode(JailNbt.encode(plan));
        JailNativeOriginPort.requireCallbackState(plan,actual,"before lease release");
        actual.putDouble("base",Double.longBitsToDouble(0x7ff8000000000013L));
        assertThrows(java.io.IOException.class,()->JailNativeOriginPort.requireCallbackState(plan,actual,"before receipt"));
        var extra=JailNbt.decode(JailNbt.encode(plan));
        extra.putInt("private player data",1);
        String error=assertThrows(java.io.IOException.class,()->JailNativeOriginPort.requireCallbackState(plan,extra,"before receipt")).getMessage();
        assertTrue(error.contains("expected absent, observed INT")); assertFalse(error.contains("private player data"));
    }

    @Test void wrongTypeOrderArrayKeyAndMissingDataCannotPassAnEqualityCheck() throws Exception {
        var source=TagParser.parseTag("{a:[1,2],b:[I;3,4],c:1.0d}");
        for (String changed:java.util.List.of("{a:[2,1],b:[I;3,4],c:1.0d}","{a:[1,2],b:[I;4,3],c:1.0d}",
                "{a:[1,2],b:[I;3,4],c:1.0f}","{a:[1,2],b:[I;3,4],d:1.0d}","{a:[1,2],b:[I;3,4]}")) {
            assertNotNull(JailNbt.mismatch(source,TagParser.parseTag(changed),"root"));
        }
        assertNull(JailNbt.mismatch(source,JailNbt.decode(JailNbt.encode(source)),"root"));
    }
}
