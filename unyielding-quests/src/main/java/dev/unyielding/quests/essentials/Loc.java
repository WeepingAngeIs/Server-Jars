package dev.unyielding.quests.essentials;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** A saved position (dimension + coordinates + rotation). */
public record Loc(String dim, double x, double y, double z, float yaw, float pitch) {
    public static Loc of(ServerPlayer p) {
        return new Loc(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
    }

    public static Loc of(ServerLevel l, double x, double y, double z, float yaw, float pitch) {
        return new Loc(l.dimension().location().toString(), x, y, z, yaw, pitch);
    }

    public ServerLevel level(MinecraftServer s) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return null;
        return s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** Teleports; returns false if the dimension is gone. */
    public boolean teleport(ServerPlayer p) {
        ServerLevel l = level(p.server);
        if (l == null) return false;
        p.teleportTo(l, x, y, z, yaw, pitch);
        return true;
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("dim", dim);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putFloat("yaw", yaw);
        t.putFloat("pitch", pitch);
        return t;
    }

    public static Loc fromTag(CompoundTag t) {
        return new Loc(t.getString("dim"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
    }

    public String pretty() {
        return String.format("%s %.0f %.0f %.0f", dim, x, y, z);
    }

    public static boolean isOverworld(ServerLevel l) {
        return l.dimension() == Level.OVERWORLD;
    }
}
