package dev.unyielding.jail;

/** XP fields are independent in vanilla (level commands need not update total). Never recompute them. */
public record JailXpState(int level,int total,float progress,int score) {
    public JailXpState {
        if(level<0||level>10000||total<0||!Float.isFinite(progress)||progress<0||progress>=1)
            throw new IllegalArgumentException("Unsupported XP tuple; XP was not changed");
    }
}
