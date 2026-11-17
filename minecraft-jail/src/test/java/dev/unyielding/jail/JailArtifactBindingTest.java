package dev.unyielding.jail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JailArtifactBindingTest {
    @Test void exactArtifactHasStableBinding(){assertEquals(JailArtifactBinding.row("a".repeat(128)),JailArtifactBinding.row("a".repeat(128)));}
    @Test void differentBytesHaveDifferentBinding(){assertNotEquals(JailArtifactBinding.row("a".repeat(128)),JailArtifactBinding.row("b".repeat(128)));}
    @Test void policyIsPartOfBinding(){assertEquals("minecraftjail-implementation=player-only-autorespawn-v1:"+"0".repeat(128),JailArtifactBinding.row("0".repeat(128)));}
    @Test void absentHashRefused(){assertThrows(IllegalArgumentException.class,()->JailArtifactBinding.row(null));}
    @Test void emptyHashRefused(){assertThrows(IllegalArgumentException.class,()->JailArtifactBinding.row(""));}
    @Test void wrongLengthRefused(){assertThrows(IllegalArgumentException.class,()->JailArtifactBinding.row("a".repeat(64)));}
    @Test void nonHashRefused(){assertThrows(IllegalArgumentException.class,()->JailArtifactBinding.row("z".repeat(128)));}
    @Test void filenameIsNotApproval(){assertThrows(IllegalArgumentException.class,()->JailArtifactBinding.row("Minecraft-Jail-1.2.0.jar"));}
}
