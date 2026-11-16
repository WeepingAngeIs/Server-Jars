package dev.unyielding.quests.jail;

import java.io.IOException;

/** Preserve missing health, rather than discard health or repeatedly heal on class swaps. */
public final class JailHealthShift {
    private JailHealthShift(){}
    public static float apply(float health,float maximumBonus,boolean temporary)throws IOException {
        if(!Float.isFinite(health)||!Float.isFinite(maximumBonus)||health<=0||maximumBonus<0||maximumBonus>10)throw new IOException("Unsupported health shift");
        float result=temporary?health-maximumBonus:health+maximumBonus;
        if(result<=0||!Float.isFinite(result))throw new IOException("Health must be greater than the origin's "+maximumBonus+" bonus before jailing; no health or items were transferred");
        return result;
    }
}
