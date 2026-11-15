package dev.unyielding.quests.essentials;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class PartyMeetingPolicyTest {
 @Test void everyAuthorizationGateMustPass() {
  assertTrue(PartyMeetingPolicy.permitted(true,true,true,true));
  for(int i=0;i<4;i++) { boolean[] f={true,true,true,true}; f[i]=false; assertFalse(PartyMeetingPolicy.permitted(f[0],f[1],f[2],f[3])); }
 }
 @Test void dimensionAllowlistNeverEnablesCrossDimensionTravel() {
  var allowed=List.of("minecraft:overworld","minecraft:the_end");
  assertTrue(PartyMeetingPolicy.dimensionAllowed("minecraft:overworld","minecraft:overworld",allowed));
  assertFalse(PartyMeetingPolicy.dimensionAllowed("minecraft:overworld","minecraft:the_end",allowed));
  assertFalse(PartyMeetingPolicy.dimensionAllowed("minecraft:overworld","minecraft:overworld",List.of()));
 }
 @Test void malformedAndOutOfBoundsCoordinatesAreRejected() {
  assertTrue(PartyMeetingPolicy.validCoordinates(0.5,64,0.5,0,0));
  assertFalse(PartyMeetingPolicy.validCoordinates(Double.NaN,64,0,0,0));
  assertFalse(PartyMeetingPolicy.validCoordinates(0,Double.POSITIVE_INFINITY,0,0,0));
  assertFalse(PartyMeetingPolicy.validCoordinates(30000000,64,0,0,0));
  assertFalse(PartyMeetingPolicy.validCoordinates(0,64,0,Float.NaN,0));
 }
}
