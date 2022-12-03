package com.neocompounder.cneo;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.ContractTestExtension.GenesisAccount;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.ArrayUtils;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.neocompounder.cneo.mock.BNeoToken;
import com.neocompounder.cneo.mock.GasBneoSwapPair;

import java.math.BigInteger;
import java.util.List;

import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ContractTest(blockTime = 1, contracts = { CompoundingNeo.class, BNeoToken.class, GasBneoSwapPair.class }, configFile="default.neo-express")
public class CompoundingNeoTest {

    private static final String SET_OWNER = "setOwner";
    private static final String GET_OWNER = "getOwner";
    private static final String DECIMALS = "decimals";
    private static final String SYMBOL = "symbol";
    private static final String TOTAL_SUPPLY = "totalSupply";
    private static final String BALANCE_OF = "balanceOf";
    private static final String GET_COMPOUND_PERIOD = "getCompoundPeriod";
    private static final String SET_COMPOUND_PERIOD = "setCompoundPeriod";
    private static final String GET_LAST_COMPOUNDED = "getLastCompounded";
    private static final String GET_GAS_REWARD = "getGasReward";
    private static final String SET_GAS_REWARD = "setGasReward";
    private static final String GET_FEE_PERCENT = "getFeePercent";
    private static final String SET_FEE_PERCENT = "setFeePercent";
    private static final String GET_MAX_SUPPLY = "getMaxSupply";
    private static final String SET_MAX_SUPPLY = "setMaxSupply";
    private static final String SET_BNEO_SCRIPT_HASH = "setBneoScriptHash";
    private static final String SET_SWAP_PAIR_SCRIPT_HASH = "setSwapPairScriptHash";
    private static final String GET_BNEO_RESERVES = "getBneoReserves";
    private static final String TRANSFER = "transfer";
    private static final String COMPOUND = "compound";
    private static final String WITHDRAW_GAS = "withdrawGas";
    private static final String SET_TOKEN0 = "setToken0";
    private static final String SET_TOKEN1 = "setToken1";

    private static final String OWNER = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    private static final String OTHER = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";

    private static final String ABORT_MESSAGE = "The vm exited due to the following exception: ABORT is executed.";

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract cNeo;
    private static SmartContract bNeo;
    private static SmartContract swapPair;
    private static Account owner;
    private static Account other;
    private static GenesisAccount genesis;
    private static NeoToken neoToken;
    private static GasToken gasToken;

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        cNeo = ext.getDeployedContract(CompoundingNeo.class);
        bNeo = ext.getDeployedContract(BNeoToken.class);
        swapPair = ext.getDeployedContract(GasBneoSwapPair.class);
        neoToken = new NeoToken(neow3j);
        gasToken = new GasToken(neow3j);
        transferFromGenesis(neoToken, hash160(genesis.getMultiSigAccount().getScriptHash()),
                hash160(owner.getScriptHash()), integer(10000), any(null));
        transferFromGenesis(gasToken, hash160(genesis.getMultiSigAccount().getScriptHash()),
                hash160(owner.getScriptHash()), integer(new BigInteger("1000000000000")), any(null));
        transferFromGenesis(gasToken, hash160(genesis.getMultiSigAccount().getScriptHash()),
                hash160(other.getScriptHash()), integer(new BigInteger("1000000000000")), any(null));
    }

    @DeployConfig(CompoundingNeo.class)
    public static DeployConfiguration configureCneo() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
        other = ext.getAccount(OTHER);
        genesis = ext.getGenesisAccount();
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @DeployConfig(BNeoToken.class)
    public static DeployConfiguration configureBneo() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @DeployConfig(GasBneoSwapPair.class)
    public static DeployConfiguration configureRouter() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @Order(0)
    @Test
    public void invokeMintWithBneo() throws Throwable {
        setBneoScriptHash(owner, bNeo.getScriptHash());
        setSwapPairScriptHash(owner, swapPair.getScriptHash());

        NeoInvokeFunction result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        // Mint using bNEO
        Hash256 txHash = transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("100000000000")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("Mint", n2.getEventName());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("100000000000"), stackItems.get(1).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("99900000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(1)
    @Test
    public void invokeBurnWithBneo() throws Throwable {
        // Burn by transferring cNEO
        Hash256 txHash = transfer(cNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("Burn", n2.getEventName());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(cNeo.getScriptHash(), new Hash160(ArrayUtils.reverseArray(stackItems.get(0).getByteArray())));
        assertEquals(new BigInteger("10000000000"), stackItems.get(1).getInteger());

        NeoInvokeFunction result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("99910000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(2)
    @Test
    public void invokeGasTopUp() throws Throwable {
        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        // Transferring GAS with an unknown action fails
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                    integer(new BigInteger("10000000000")), array(string("NOT_AN_ACTION")));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        Hash256 txHash = transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), array(string("GAS")));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n1 = execution.getNotifications().get(1);
        assertEquals("TopUpGas", n1.getEventName());
        List<StackItem> stackItems = n1.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("10000000000"), stackItems.get(1).getInteger());

        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }


    @Order(3)
    @Test
    public void invokeTransfer() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        Hash256 txHash = transfer(cNeo, owner, hash160(owner.getScriptHash()), hash160(other.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("Transfer", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(other.getAddress(), stackItems.get(1).getAddress());
        assertEquals(new BigInteger("10000000000"), stackItems.get(2).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("80000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // Transferring from a wallet that is not owned by the caller doesn't do anything
        transfer(cNeo, other, hash160(owner.getScriptHash()), hash160(other.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("80000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }


    @Order(4)
    @Test
    public void invokeOwner() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_OWNER);
        assertEquals(result.getInvocationResult().getStack().get(0).getAddress(), owner.getAddress());

        // Setting the owner requires a witness from both the current and new owners
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            cNeo.invokeFunction(SET_OWNER, hash160(owner.getScriptHash()))
                    .signers(AccountSigner.calledByEntry(other))
                    .getUnsignedTransaction();
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);
        
        setOwner(other, owner);

        result = cNeo.callInvokeFunction(GET_OWNER);
        assertEquals(result.getInvocationResult().getStack().get(0).getAddress(), other.getAddress());

        setOwner(owner, other);

        result = cNeo.callInvokeFunction(GET_OWNER);
        assertEquals(result.getInvocationResult().getStack().get(0).getAddress(), owner.getAddress());
    }
    

    @Order(5)
    @Test
    public void invokeSymbol() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(SYMBOL);
        assertEquals("cNEO", result.getInvocationResult().getStack().get(0).getString());
    }

    @Order(6)
    @Test
    public void invokeDecimals() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(DECIMALS);
        assertEquals(new BigInteger("8"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(7)
    @Test
    public void invokeTotalSupply() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(8)
    @Test
    public void invokeCompoundPeriod() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_COMPOUND_PERIOD);
        assertEquals(new BigInteger("604800000"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setCompoundPeriod(other, new BigInteger("3600000"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        setCompoundPeriod(owner, new BigInteger("3600000"));

        result = cNeo.callInvokeFunction(GET_COMPOUND_PERIOD);
        assertEquals(new BigInteger("3600000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(9)
    @Test
    public void invokeGasReward() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_GAS_REWARD);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setGasReward(other, new BigInteger("500000000"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        setGasReward(owner, new BigInteger("500000000"));

        result = cNeo.callInvokeFunction(GET_GAS_REWARD);
        assertEquals(new BigInteger("500000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(10)
    @Test
    public void invokeFeePercent() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_FEE_PERCENT);
        assertEquals(new BigInteger("5"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setFeePercent(other, new BigInteger("10"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        setFeePercent(owner, new BigInteger("10"));

        result = cNeo.callInvokeFunction(GET_FEE_PERCENT);
        assertEquals(new BigInteger("10"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(11)
    @Test
    public void invokeCompound() throws Throwable {
        setToken0(gasToken.getScriptHash());
        setToken1(bNeo.getScriptHash());

        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(swapPair.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        // Our fake bNEO sends all GAS it has upon receiving any amount of bNEO
        transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(bNeo.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(bNeo.getScriptHash())));
        assertEquals(new BigInteger("1000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_LAST_COMPOUNDED, List.of());
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        BigInteger beforeGas = result.getInvocationResult().getStack().get(0).getInteger();

        Hash256 txHash = compound(owner);
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n5 = execution.getNotifications().get(5);
        assertEquals("Compound", n5.getEventName());
        List<StackItem> stackItems = n5.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("1000000000"), stackItems.get(1).getInteger());
        assertEquals(new BigInteger("1000000000"), stackItems.get(2).getInteger());
        assertEquals(new BigInteger("100000000"), stackItems.get(3).getInteger());

        // Since 5 GAS in rewards was paid out, the owner's balance must be greater after invocation
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        BigInteger afterGas = result.getInvocationResult().getStack().get(0).getInteger();
        assertTrue(afterGas.compareTo(beforeGas) > 0);

        result = cNeo.callInvokeFunction(GET_LAST_COMPOUNDED, List.of());
        assertEquals(true, result.getInvocationResult().getStack().get(0).getInteger().compareTo(new BigInteger("0")) > 0);

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("91000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("91000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        // We started with 100 GAS
        // Compounding claimed 10 GAS, so we kept 10% of 10 == 1 GAS in fees
        // We also paid out 5 GAS in rewards, so we are left with 6 GAS
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("9600000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // 9 GAS was swapped for bNEO
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(swapPair.getScriptHash())));
        assertEquals(new BigInteger("900000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(12)
    @Test
    public void invokeMintWithNeo() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("80000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // Mint using NEO
        Hash256 txHash = transfer(neoToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("100")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n5 = execution.getNotifications().get(5);
        assertEquals("Mint", n5.getEventName());
        List<StackItem> stackItems = n5.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("9890109890"), stackItems.get(1).getInteger());

        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(bNeo.getScriptHash())));
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("101000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("101000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // We originally had 90000000000 cNEO backed by 91000000000 bNEO
        // New cNEO is issued at 90000000000 / 91000000000 per bNEO
        // Thus, 100 NEO == 10000000000 bNEO == 10000000000 * 90 / 91 == 9890109890 cNEO
        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("89890109890"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("99890109890"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(13)
    @Test
    public void invokeBurnWithNeo() throws Throwable {
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(swapPair.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        // Cannot compound until 3600000000ms have passed
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            compound(owner);
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        ext.fastForwardOneBlock(7200);
        compound(owner);

        // Now we have 99890109890 cNEO backed by 102000000000 bNEO
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("102000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("89890109890"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("9900"), result.getInvocationResult().getStack().get(0).getInteger());

        // Burn by transferring GAS
        Hash256 txHash = transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("Burn", n2.getEventName());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        // We are burning the cNEO equivalent of 100 NEO
        // This is 100 * 99890109890 / 102000000000 with 8 decimal places, rounded up == 9793148029
        assertEquals(new BigInteger("9793148029"), stackItems.get(1).getInteger());

        // Since we have 99890109890 cNEO backed by 102000000000 bNEO,
        // redeeming 100 NEO == 10000000000 bNEO means we burn 10000000000 * (99890109890 / 102000000000) == 9793148029 cNEO
        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("80096961861"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("10000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("92000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("92000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(TOTAL_SUPPLY, List.of());
        assertEquals(new BigInteger("90096961861"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(14)
    @Test
    public void invokeWithdrawGas() throws Throwable {
        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("9100000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        BigInteger beforeGas = result.getInvocationResult().getStack().get(0).getInteger();

        Hash256 txHash = withdrawGas(owner, new BigInteger("100000000"));
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n1 = execution.getNotifications().get(1);
        assertEquals("WithdrawGas", n1.getEventName());
        List<StackItem> stackItems = n1.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("100000000"), stackItems.get(1).getInteger());

        // Since 1 GAS was withdrawn, the owner's balance must be greater after invocation
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        BigInteger afterGas = result.getInvocationResult().getStack().get(0).getInteger();
        assertTrue(afterGas.compareTo(beforeGas) > 0);

        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("9000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(15)
    @Test
    public void invokeMaxSupply() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_MAX_SUPPLY);
        assertEquals(new BigInteger("100000000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setMaxSupply(other, new BigInteger("9000000000"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        setMaxSupply(owner, new BigInteger("9000000000"));

        result = cNeo.callInvokeFunction(GET_MAX_SUPPLY);
        assertEquals(new BigInteger("9000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("90096961861"), result.getInvocationResult().getStack().get(0).getInteger());

        // Cannot mint any more cNEO since we hit the max supply
        exception = assertThrows(TransactionConfigurationException.class, () -> {
            transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                    integer(new BigInteger("10000000000")), any(null));
        });
        actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        // Once we reset the max supply, we can mint more cNEO
        setMaxSupply(owner, new BigInteger("100000000000000"));
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("99890109889"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    private static Hash256 transferFromGenesis(SmartContract token, ContractParameter... params) throws Throwable {
        Account[] accounts = genesis.getSignerAccounts();
        Transaction tx = token.invokeFunction(TRANSFER, params)
                .signers(AccountSigner.none(accounts[0]), AccountSigner.calledByEntry(genesis.getMultiSigAccount()))
                .getUnsignedTransaction();

        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), accounts[0].getECKeyPair()))
                .addMultiSigWitness(genesis.getMultiSigAccount().getVerificationScript(), accounts[0])
                .send().getSendRawTransaction().getHash();

        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        return txHash;
    }

    private static Hash256 invoke(SmartContract token, Account caller, String method, ContractParameter... params) throws Throwable {
        Transaction tx = token.invokeFunction(method, params)
                .signers(AccountSigner.calledByEntry(caller))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), caller.getECKeyPair()))
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        return txHash;
    }

    private static Hash256 transfer(SmartContract token, Account caller, ContractParameter... params) throws Throwable {
        return invoke(token, caller, TRANSFER, params);
    }

    private static void setBneoScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        invoke(cNeo, caller, SET_BNEO_SCRIPT_HASH, hash160(contractHash));
    }

    private static void setSwapPairScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        invoke(cNeo, caller, SET_SWAP_PAIR_SCRIPT_HASH, hash160(contractHash));
    }

    private static void setToken0(Hash160 token0) throws Throwable {
        invoke(swapPair, owner, SET_TOKEN0, hash160(token0));
    }

    private static void setToken1(Hash160 token1) throws Throwable {
        invoke(swapPair, owner, SET_TOKEN1, hash160(token1));
    }

    private static void setOwner(Account newOwner, Account oldOwner) throws Throwable {
        Transaction tx = cNeo.invokeFunction(SET_OWNER, hash160(newOwner.getScriptHash()))
                .signers(AccountSigner.calledByEntry(oldOwner), AccountSigner.calledByEntry(newOwner))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), oldOwner.getECKeyPair()))
                .addWitness(Witness.create(tx.getHashData(), newOwner.getECKeyPair()))
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    private static void setCompoundPeriod(Account caller, BigInteger compoundPeriod) throws Throwable {
        invoke(cNeo, caller, SET_COMPOUND_PERIOD, integer(compoundPeriod));
    }

    private static void setGasReward(Account caller, BigInteger gasReward) throws Throwable {
        invoke(cNeo, caller, SET_GAS_REWARD, integer(gasReward));
    }

    private static void setFeePercent(Account caller, BigInteger feePercent) throws Throwable {
        invoke(cNeo, caller, SET_FEE_PERCENT, integer(feePercent));
    }

    private static void setMaxSupply(Account caller, BigInteger maxSupply) throws Throwable {
        invoke(cNeo, caller, SET_MAX_SUPPLY, integer(maxSupply));
    }

    private static Hash256 compound(Account caller) throws Throwable {
        return invoke(cNeo, caller, COMPOUND, hash160(caller.getScriptHash()));
    }

    private static Hash256 withdrawGas(Account caller, BigInteger withdrawQuantity) throws Throwable {
        return invoke(cNeo, caller, WITHDRAW_GAS, hash160(caller.getScriptHash()), integer(withdrawQuantity));
    }
}
