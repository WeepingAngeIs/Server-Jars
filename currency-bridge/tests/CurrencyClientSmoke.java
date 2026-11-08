package dev.unyielding.bundlesmoke;

import com.uwustudio.cobblecurrency.Balance;
import com.uwustudio.cobblecurrency.CobbleDollarsCurrencyType;
import com.uwustudio.cobblecurrency.CobbleDollarsValue;
import com.uwustudio.cobblecurrency.client.ClientCobbleDollarsType;
import com.uwustudio.cobblecurrency.client.CobbleDollarsInputHandler;
import io.github.lightman314.lightmanscurrency.api.money.client.ClientMoneyAPI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class CurrencyClientSmoke {
    private static boolean complete;
    private static Map<String, Object> report;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (complete || mc.player == null || mc.getOverlay() != null) return;
        try {
            if (report == null) {
                report = BundleSmoke.verifyProductionJar();
                var registered = ClientMoneyAPI.getApi().GetClientType(CobbleDollarsCurrencyType.TYPE);
                require(registered instanceof ClientCobbleDollarsType, "Currency is missing from Lightman's client registry");
                require(ClientMoneyAPI.getApi().GetMoneyInputs(mc.player).stream().anyMatch(x -> x instanceof CobbleDollarsInputHandler),
                        "CobbleDollars is missing from the trader's currency input list");
                var inputs = registered.getInputHandlers(mc.player);
                require(inputs.size() == 1 && inputs.getFirst() instanceof CobbleDollarsInputHandler, "Unexpected currency input widget");
                var input = (CobbleDollarsInputHandler)inputs.getFirst();
                require(input.inputName().getString().equals("CobbleDollars"), "Currency selector label is incorrect");
                var decimals = CobbleDollarsInputHandler.class.getDeclaredMethod("getRelevantDecimals");
                decimals.setAccessible(true);
                require((Integer)decimals.invoke(input) == 0, "Currency input permits fractional precision");
                var convert = CobbleDollarsInputHandler.class.getDeclaredMethod("getValueFromInput", double.class);
                convert.setAccessible(true);
                var entered = (CobbleDollarsValue)convert.invoke(input, 2500.75);
                require(entered.getAmount().equals(BigInteger.valueOf(2500)), "Currency input does not produce whole dollars");
                require(registered.getDisplayEntry(entered, List.of(), true) != null, "Trading price display is missing");
                report.put("client_currency_registration_selector_input_and_price_display", "passed");
            }
            if (Files.exists(Path.of("/tmp/uq-currency-balance-ready"))
                    && Balance.get(mc.player).equals(CurrencyServerSmoke.SYNC_BALANCE)) {
                report.put("received_server_balance", CurrencyServerSmoke.SYNC_BALANCE.toString());
                BundleSmoke.write("/tmp/uq-currency-client-passed.json", report);
                complete = true;
            }
        } catch (Throwable failure) {
            complete = true;
            BundleSmoke.fail("/tmp/uq-currency-failed.txt", failure);
        }
    }
}
