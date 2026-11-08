package dev.unyielding.bundlesmoke;

import com.mojang.brigadier.StringReader;
import com.uwustudio.cobblecurrency.Balance;
import com.uwustudio.cobblecurrency.BridgeConfig;
import com.uwustudio.cobblecurrency.CobbleDollarsCurrencyType;
import com.uwustudio.cobblecurrency.CobbleDollarsPlayerHandler;
import com.uwustudio.cobblecurrency.CobbleDollarsValue;
import com.uwustudio.cobblecurrency.CobbleDollarsValueParser;
import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Cloud-only assertions; this class is never added to the release JAR. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class CurrencyServerSmoke {
    private static final Path READY = Path.of("/tmp/uq-currency-balance-ready");
    private static final Path CLIENT = Path.of("/tmp/uq-currency-client-passed.json");
    private static Map<String, Object> report;
    private static BigInteger originalBalance;
    private static boolean started;
    private static boolean complete;
    public static final BigInteger SYNC_BALANCE = BigInteger.valueOf(73917);

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try {
            report = new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            require(MoneyAPI.getApi().GetRegisteredCurrencyType(CobbleDollarsCurrencyType.TYPE)
                    == CobbleDollarsCurrencyType.INSTANCE, "Currency was not registered with Lightman's API");
            BigInteger huge = new BigInteger("1234567890123456789012345678901234567890");
            MoneyValue value = CobbleDollarsValue.of(huge);
            amount(value, huge);
            amount(MoneyValue.load(value.save()), huge);
            amount(MoneyValue.loadFromJson(value.toJson()), huge);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                value.encode(buffer);
                amount(MoneyValue.decode(buffer), huge);
            } finally { buffer.release(); }
            require(value.getCoreValue() == Long.MAX_VALUE, "Large value overflowed Lightman's core-value limit");
            amount(MoneyValueParser.parse(new StringReader("cd;2,500"), false), BigInteger.valueOf(2500));
            amount(MoneyValueParser.ParseConfigString(MoneyValueParser.writeParsable(value), MoneyValue::empty), huge);
            MoneyValue coinFixture = CoinValue.fromNumber("main", 25);
            require(!coinFixture.isEmpty(), "Coin serialization fixture is empty");
            var bridgeWriter = CobbleDollarsValueParser.class.getDeclaredMethod("writeValueArgument", MoneyValue.class);
            bridgeWriter.setAccessible(true);
            for (MoneyValue foreign : List.of(coinFixture, MoneyValue.free(), MoneyValue.empty())) {
                require(bridgeWriter.invoke(CobbleDollarsValueParser.INSTANCE, foreign) == null,
                        "Bridge parser must decline foreign values independently of currency registration order");
                String encoded = MoneyValueParser.writeParsable(foreign);
                require(!encoded.startsWith("cd;"), "Bridge captured another currency's config serialization: " + encoded);
                require(MoneyValueParser.parse(new StringReader(encoded), true).equals(foreign),
                        "Foreign currency config roundtrip failed: " + encoded);
            }
            amount(CobbleDollarsValue.of(BigInteger.valueOf(-10)), BigInteger.ZERO);
            amount(cd(101).percentageOfValue(10, true), BigInteger.valueOf(11));
            amount(cd(101).percentageOfValue(10, false), BigInteger.TEN);
            amount(cd(101).multiplyValue(0.5), BigInteger.valueOf(51));
            amount(cd(100).subtractValue(cd(150)), BigInteger.ZERO);
            amount(cd(100).addValue(cd(150)), BigInteger.valueOf(250));
            require(!cd(2500).allowInterest(), "Bank interest unexpectedly enabled by default");
            require(cd(2500).getText(Component.empty()).getString().equals("2,500 CobbleDollars"), "Formatted currency label is incorrect");
            var commands = event.getServer().getCommands().getDispatcher().getRoot();
            var admin = commands.getChild("uwucd");
            require(admin != null && !admin.canUse(event.getServer().createCommandSourceStack().withPermission(0))
                    && admin.canUse(event.getServer().createCommandSourceStack().withPermission(2)), "Currency debug command is not admin-only");
            report.put("registration_parser_and_nbt_json_network_roundtrips", "passed");
            report.put("coin_free_and_empty_config_values_roundtrip_unchanged", "passed");
            report.put("large_amounts_arithmetic_rounding_and_admin_permissions", "passed");
        } catch (Throwable failure) {
            complete = true;
            BundleSmoke.fail("/tmp/uq-currency-failed.txt", failure);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (complete || report == null || !Files.exists(Path.of("/tmp/uq-admin-test-done"))) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        try {
            if (!started) {
                started = true;
                originalBalance = Balance.get(player);
                var handler = new CobbleDollarsPlayerHandler(player);
                Balance.set(player, BigInteger.valueOf(1000));
                require(handler.extractMoney(cd(250), true).isEmpty(), "Simulated debit left an affordable balance unpaid");
                balance(player, 1000);
                require(handler.insertMoney(cd(250), true).isEmpty(), "Simulated credit was unexpectedly rejected");
                balance(player, 1000);
                require(handler.extractMoney(cd(250), false).isEmpty(), "Debit failed");
                balance(player, 750);
                require(handler.insertMoney(cd(125), false).isEmpty(), "Credit failed");
                balance(player, 875);
                amount(handler.extractMoney(cd(1000), true), BigInteger.valueOf(125));
                balance(player, 875);
                amount(handler.extractMoney(cd(1000), false), BigInteger.valueOf(125));
                balance(player, 0);
                MoneyValue coins = CoinValue.fromNumber("main", 25);
                require(!coins.isEmpty(), "Coin fixture is empty");
                require(!handler.isMoneyTypeValid(coins), "Currency handler accepts coins as CobbleDollars");
                require(handler.insertMoney(coins, false) == coins && handler.extractMoney(coins, false) == coins,
                        "Foreign currency was consumed by the bridge");
                balance(player, 0);
                Balance.set(player, BigInteger.valueOf(1000));
                long originalLimit = BridgeConfig.MAX_BALANCE.get();
                try {
                    BridgeConfig.MAX_BALANCE.set(1100L);
                    amount(handler.insertMoney(cd(250), true), BigInteger.valueOf(150));
                    balance(player, 1000);
                    amount(handler.insertMoney(cd(250), false), BigInteger.valueOf(150));
                    balance(player, 1100);
                } finally { BridgeConfig.MAX_BALANCE.set(originalLimit); }
                var combined = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
                require(combined.extractMoney(cd(100), true).isEmpty(), "Lightman's combined handler does not recognize CobbleDollars");
                balance(player, 1100);
                require(combined.extractMoney(cd(100), false).isEmpty(), "Lightman's combined handler could not debit CobbleDollars");
                balance(player, 1000);
                require(combined.insertMoney(cd(250), false).isEmpty(), "Lightman's combined handler could not credit CobbleDollars");
                balance(player, 1250);
                Balance.set(player, SYNC_BALANCE);
                Files.writeString(READY, SYNC_BALANCE.toString());
                report.put("real_player_simulation_debit_credit_no_overdraft_and_cap", "passed");
                report.put("coins_remain_separate_and_combined_money_handler", "passed");
            } else if (Files.exists(CLIENT)) {
                Balance.set(player, originalBalance);
                report.put("cobbledollars_network_sync_to_real_client", "passed");
                report.put("scope", "Actual final JAR with real currency mods; focused API and client integration, not a complete auction/trader gameplay walkthrough");
                BundleSmoke.write("/tmp/uq-currency-server-passed.json", report);
                complete = true;
            }
        } catch (Throwable failure) {
            complete = true;
            if (originalBalance != null) Balance.set(player, originalBalance);
            BundleSmoke.fail("/tmp/uq-currency-failed.txt", failure);
        }
    }

    private static MoneyValue cd(long amount) { return CobbleDollarsValue.of(BigInteger.valueOf(amount)); }
    private static void balance(ServerPlayer player, long expected) {
        require(Balance.get(player).equals(BigInteger.valueOf(expected)), "Player balance mismatch: expected " + expected);
    }
    private static void amount(MoneyValue value, BigInteger expected) {
        require(value instanceof CobbleDollarsValue && ((CobbleDollarsValue)value).getAmount().equals(expected),
                "Currency amount mismatch: expected " + expected);
    }
}
