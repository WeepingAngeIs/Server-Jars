package dev.unyielding.quests.jail;

import java.io.*;
import net.minecraft.nbt.*;

final class JailNbt {
    private JailNbt(){}
    static byte[] encode(CompoundTag tag)throws IOException {var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(10);out.writeUTF("");payload(tag,out);byte[] encoded=bytes.toByteArray();if(!tag.equals(decode(encoded)))throw new IOException("Custody NBT round-trip failed");return encoded;}
    static CompoundTag decode(byte[] bytes)throws IOException{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(4L*1024*1024));}
    static void payload(Tag tag,DataOutput out)throws IOException {
        if(tag instanceof CompoundTag compound){for(String key:new java.util.TreeSet<>(compound.getAllKeys())){Tag value=compound.get(key);out.writeByte(value.getId());out.writeUTF(key);payload(value,out);}out.writeByte(0);}
        else if(tag instanceof ListTag list){out.writeByte(list.getElementType());out.writeInt(list.size());for(Tag value:list)payload(value,out);}else tag.write(out);
    }
}
