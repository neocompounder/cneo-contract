package com.neocompounder.cneo;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
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
import io.neow3j.transaction.ContractSigner;
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
import com.neocompounder.cneo.mock.BurgerAgent;
import com.neocompounder.cneo.mock.FlamingoSwapFactoryRouterPair;

import java.math.BigInteger;
import java.util.List;

import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ContractTest(blockTime = 1, contracts = { CompoundingNeo.class, CompoundingNeoVoter.class, BNeoToken.class, BurgerAgent.class, FlamingoSwapFactoryRouterPair.class }, configFile="default.neo-express")
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
    private static final String GET_FEE_BASIS_POINTS = "getFeeBasisPoints";
    private static final String SET_FEE_BASIS_POINTS = "setFeeBasisPoints";
    private static final String GET_MAX_SUPPLY = "getMaxSupply";
    private static final String SET_MAX_SUPPLY = "setMaxSupply";
    private static final String GET_GAS_REWARD = "getGasReward";
    private static final String SET_GAS_REWARD = "setGasReward";
    private static final String GET_MAX_SWAP_GAS = "getMaxSwapGas";
    private static final String SET_MAX_SWAP_GAS = "setMaxSwapGas";
    private static final String GET_EXIT_FEE = "getExitFee";
    private static final String SET_EXIT_FEE = "setExitFee";
    private static final String SET_BNEO_SCRIPT_HASH = "setBneoScriptHash";
    private static final String SET_CNEO_SCRIPT_HASH = "setCneoScriptHash";
    private static final String SET_VOTER_SCRIPT_HASH = "setVoterScriptHash";
    private static final String SET_SWAP_FACTORY_SCRIPT_HASH = "setSwapFactoryScriptHash";
    private static final String SET_SWAP_ROUTER_SCRIPT_HASH = "setSwapRouterScriptHash";
    private static final String SET_BURGER_AGENT_SCRIPT_HASH = "setBurgerAgentScriptHash";
    private static final String GET_TOTAL_RESERVES = "getTotalReserves";
    private static final String GET_BNEO_RESERVES = "getBneoReserves";
    private static final String GET_NEO_RESERVES = "getNeoReserves";
    private static final String GET_RESERVE_RATIO = "getReserveRatio";
    private static final String TRANSFER = "transfer";
    private static final String VOTE = "vote";
    private static final String COMPOUND = "compound";
    private static final String COMPOUND_RESERVES = "compoundReserves";
    private static final String WITHDRAW_GAS = "withdrawGas";
    private static final String CONVERT_TO_NEO = "convertToNeo";
    private static final String CONVERT_TO_BNEO = "convertToBneo";
    private static final String SET_TOKEN0 = "setToken0";
    private static final String SET_TOKEN1 = "setToken1";
    private static final String SET_RESERVES = "setReserves";

    private static final String OWNER = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    private static final String OTHER = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";

    private static final String ABORT_MESSAGE = "The vm exited due to the following exception: ABORT is executed.";
    private static final String ASSERT_MESSAGE = "The vm exited due to the following exception: ASSERT is executed with false result.";

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract cNeo;
    private static SmartContract voter;
    private static SmartContract bNeo;
    private static SmartContract burgerAgent;
    private static SmartContract swapRouter;
    private static Account owner;
    private static Account other;
    private static GenesisAccount genesis;
    private static NeoToken neoToken;
    private static GasToken gasToken;

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        cNeo = ext.getDeployedContract(CompoundingNeo.class);
        voter = ext.getDeployedContract(CompoundingNeoVoter.class);
        bNeo = ext.getDeployedContract(BNeoToken.class);
        burgerAgent = ext.getDeployedContract(BurgerAgent.class);
        swapRouter = ext.getDeployedContract(FlamingoSwapFactoryRouterPair.class);
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

    @DeployConfig(CompoundingNeoVoter.class)
    public static DeployConfiguration configureVoter() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
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

    @DeployConfig(BurgerAgent.class)
    public static DeployConfiguration configureBurgerAgent() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @DeployConfig(FlamingoSwapFactoryRouterPair.class)
    public static DeployConfiguration configureRouter() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        owner = ext.getAccount(OWNER);
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @Order(0)
    @Test
    public void invokeMintWithBneo() throws Throwable {
        setToken0(owner, gasToken.getScriptHash());
        setToken1(owner, bNeo.getScriptHash());
        setBneoScriptHash(owner, cNeo, bNeo.getScriptHash());
        setBneoScriptHash(owner, voter, bNeo.getScriptHash());
        setCneoScriptHash(owner, cNeo.getScriptHash());
        setVoterScriptHash(owner, voter.getScriptHash());
        setSwapRouterScriptHash(owner, swapRouter.getScriptHash());
        setSwapFactoryScriptHash(owner, swapRouter.getScriptHash());
        setBurgerAgentScriptHash(owner, bNeo, burgerAgent.getScriptHash());

        NeoInvokeFunction result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
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

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("100000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_RESERVE_RATIO, List.of());
        assertEquals(new BigInteger("1000000000000000000"), result.getInvocationResult().getStack().get(0).getInteger());
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
        assertEquals(new BigInteger("99909950000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("90050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("90050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("90050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_RESERVE_RATIO, List.of());
        assertEquals(new BigInteger("1000555555555555555"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(2)
    @Test
    public void invokeTopUpGas() throws Throwable {
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
                integer(new BigInteger("10000000000")), any(null));

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

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
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

        Hash256 txHash = setOwner(owner, other);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetOwner", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());

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

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setCompoundPeriod(owner, new BigInteger("-1"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        Hash256 txHash = setCompoundPeriod(owner, new BigInteger("3600000"));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetCompoundPeriod", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(new BigInteger("3600000"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_COMPOUND_PERIOD);
        assertEquals(new BigInteger("3600000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(9)
    @Test
    public void invokeFeeBasisPoints() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_FEE_BASIS_POINTS);
        assertEquals(new BigInteger("500"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setFeeBasisPoints(other, new BigInteger("1000"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setFeeBasisPoints(owner, new BigInteger("10100"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setFeeBasisPoints(owner, new BigInteger("-100"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        Hash256 txHash = setFeeBasisPoints(owner, new BigInteger("1000"));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetFeeBasisPoints", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(new BigInteger("1000"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_FEE_BASIS_POINTS);
        assertEquals(new BigInteger("1000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(10)
    @Test
    public void invokeGasReward() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_GAS_REWARD);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setGasReward(other, new BigInteger("10"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setGasReward(owner, new BigInteger("100000001"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setGasReward(owner, new BigInteger("-1"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        Hash256 txHash = setGasReward(owner, new BigInteger("10000000"));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetGasReward", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(new BigInteger("10000000"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_GAS_REWARD);
        assertEquals(new BigInteger("10000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(11)
    @Test
    public void invokeMaxSwapGas() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_MAX_SWAP_GAS);
        assertEquals(new BigInteger("500000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setMaxSwapGas(other, new BigInteger("10"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setGasReward(owner, new BigInteger("-1"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        Hash256 txHash = setMaxSwapGas(owner, new BigInteger("100"));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetMaxSwapGas", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(new BigInteger("100"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_MAX_SWAP_GAS);
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());

        setMaxSwapGas(owner, new BigInteger("500000000000"));
    }

    @Order(12)
    @Test
    public void invokeExitFee() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_EXIT_FEE);
        assertEquals(new BigInteger("50"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setExitFee(other, new BigInteger("10"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        exception = assertThrows(TransactionConfigurationException.class, () -> {
            setExitFee(owner, new BigInteger("-1"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        Hash256 txHash = setExitFee(owner, new BigInteger("100"));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetExitFee", n0.getEventName());
        List<StackItem> stackItems = n0.getState().getList();
        assertEquals(new BigInteger("100"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_EXIT_FEE);
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());

        setExitFee(owner, new BigInteger("50"));
    }

    @Order(13)
    @Test
    public void invokeCompound() throws Throwable {
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(swapRouter.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        // Our fake bNEO sends all GAS it has upon receiving any amount of bNEO
        transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(bNeo.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(bNeo.getScriptHash())));
        assertEquals(new BigInteger("1000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("90050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("90050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_LAST_COMPOUNDED);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        BigInteger beforeGas = result.getInvocationResult().getStack().get(0).getInteger();

        setMaxSwapGas(owner, new BigInteger("800000000"));
        setReserves(owner, 1, 1);
        InvokeResult invokeResult = compound(other);
        Hash256 txHash = invokeResult.txHash;

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n8 = execution.getNotifications().get(8);
        assertEquals("Compound", n8.getEventName());
        List<StackItem> stackItems = n8.getState().getList();
        assertEquals(other.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("1000000000"), stackItems.get(1).getInteger());
        assertEquals(new BigInteger("1000000000"), stackItems.get(2).getInteger());
        assertEquals(new BigInteger("200000000"), stackItems.get(3).getInteger());
        setMaxSwapGas(owner, new BigInteger("500000000000"));

        // The GAS balance of the invoker decreases by the execution cost and increases by the GAS reward
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        BigInteger afterGas = result.getInvocationResult().getStack().get(0).getInteger();
        BigInteger gasFee = BigInteger.valueOf(invokeResult.tx.getNetworkFee() + invokeResult.tx.getSystemFee());
        BigInteger expectedGas = beforeGas.subtract(gasFee).add(new BigInteger("10000000"));
        assertTrue(afterGas.compareTo(expectedGas) == 0);

        result = cNeo.callInvokeFunction(GET_LAST_COMPOUNDED);
        assertEquals(true, result.getInvocationResult().getStack().get(0).getInteger().compareTo(new BigInteger("0")) > 0);

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("91050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("91050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_RESERVE_RATIO, List.of());
        assertEquals(new BigInteger("1011666666666666666"), result.getInvocationResult().getStack().get(0).getInteger());

        // We started with 100 GAS
        // Compounding claimed 10 GAS, so we kept 10% of 10 == 1 GAS in fees
        // Due to the maxGasSwap, we also retained an extra 1 GAS to start with 102 GAS
        BigInteger gasBalance = new BigInteger("10200000000").subtract(new BigInteger("10000000"));
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(gasBalance, result.getInvocationResult().getStack().get(0).getInteger());

        // 8 GAS was swapped for bNEO
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(swapRouter.getScriptHash())));
        assertEquals(new BigInteger("800000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(swapRouter.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(14)
    @Test
    public void invokeMintWithNeo() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("90000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("80000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // Mint using NEO
        Hash256 txHash = transfer(neoToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("100")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n4 = execution.getNotifications().get(4);
        assertEquals("Mint", n4.getEventName());
        List<StackItem> stackItems = n4.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("9884678747"), stackItems.get(1).getInteger());

        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(bNeo.getScriptHash())));
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("101050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("101050000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // We originally had 90000000000 cNEO backed by 91050000000 bNEO
        // New cNEO is issued at 90000000000 / 91050000000 per bNEO
        // Thus, 100 NEO == 10000000000 bNEO == 10000000000 * 9000 / 9105 == 9884678747 cNEO
        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("89884678747"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("99884678747"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(15)
    @Test
    public void invokeWithdrawGas() throws Throwable {
        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("10190000000"), result.getInvocationResult().getStack().get(0).getInteger());

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
        assertEquals(new BigInteger("10090000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(16)
    @Test
    public void invokeMaxSupply() throws Throwable {
        Hash256 txHash = transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n1 = execution.getNotifications().get(1);
        assertEquals("TopUpGas", n1.getEventName());
        List<StackItem> stackItems = n1.getState().getList();
        assertEquals(owner.getAddress(), stackItems.get(0).getAddress());
        assertEquals(new BigInteger("10000000000"), stackItems.get(1).getInteger());
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_MAX_SUPPLY);
        assertEquals(new BigInteger("100000000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            setMaxSupply(other, new BigInteger("9000000000"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        txHash = setMaxSupply(owner, new BigInteger("99884678747"));

        execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n0 = execution.getNotifications().get(0);
        assertEquals("SetMaxSupply", n0.getEventName());
        stackItems = n0.getState().getList();
        assertEquals(new BigInteger("99884678747"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_MAX_SUPPLY);
        assertEquals(new BigInteger("99884678747"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("99884678747"), result.getInvocationResult().getStack().get(0).getInteger());

        // Cannot mint any more cNEO since we hit the max supply
        exception = assertThrows(TransactionConfigurationException.class, () -> {
            transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                    integer(new BigInteger("10000000000")), any(null));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        // Once we reset the max supply, we can mint more cNEO
        setMaxSupply(owner, new BigInteger("100000000000000"));
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("109769357494"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(17)
    @Test
    public void invokeCompoundReserves() throws Throwable {
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(swapRouter.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));

        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("111050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("111050000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // Cannot compound more than maxSwapGas
        setMaxSwapGas(owner, new BigInteger("100000000000"));
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            compoundReserves(owner, new BigInteger("100000000001"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        // Cannot compound more than the GAS reserves
        setMaxSwapGas(owner, new BigInteger("500000000000"));
        exception = assertThrows(TransactionConfigurationException.class, () -> {
            compoundReserves(owner, new BigInteger("100000000000"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        setReserves(owner, 10, 1);
        InvokeResult invokeResult = compoundReserves(owner, new BigInteger("10000000000"));
        Hash256 txHash = invokeResult.txHash;

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("CompoundReserves", n2.getEventName());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(new BigInteger("10000000000"), stackItems.get(0).getInteger());
        assertEquals(new BigInteger("1000000000"), stackItems.get(1).getInteger());

        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(18)
    @Test
    public void invokeConvertToNeo() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        // Cannot convert more bNEO than the reserves
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            convertToNeo(owner, new BigInteger("1500"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        // Only owner can convert to NEO
        exception = assertThrows(TransactionConfigurationException.class, () -> {
            convertToNeo(other, new BigInteger("100"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        Hash256 txHash = convertToNeo(owner, new BigInteger("100"));
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n7 = execution.getNotifications().get(7);
        assertEquals("ConvertToNeo", n7.getEventName());
        List<StackItem> stackItems = n7.getState().getList();
        assertEquals(new BigInteger("100"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("102050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("102050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(19)
    @Test
    public void invokeConvertToBneo() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("102050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("102050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("100"), result.getInvocationResult().getStack().get(0).getInteger());

        // Cannot convert more NEO than the reserves
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            convertToBneo(owner, new BigInteger("101"));
        });
        String actualMessage = exception.getMessage();
        assertEquals(ASSERT_MESSAGE, actualMessage);

        // Only owner can convert to bNEO
        exception = assertThrows(TransactionConfigurationException.class, () -> {
            convertToBneo(other, new BigInteger("90"));
        });
        actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);

        Hash256 txHash = convertToBneo(owner, new BigInteger("90"));
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n4 = execution.getNotifications().get(4);
        assertEquals("ConvertToBneo", n4.getEventName());
        List<StackItem> stackItems = n4.getState().getList();
        assertEquals(new BigInteger("90"), stackItems.get(0).getInteger());

        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES);
        assertEquals(new BigInteger("111050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("111050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("10"), result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Order(20)
    @Test
    public void invokeVote() throws Throwable {
        transfer(neoToken, owner, hash160(owner.getScriptHash()), hash160(bNeo.getScriptHash()),
                integer(new BigInteger("2000")), any(null));

        transfer(gasToken, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        // Empty out GAS of bNEO contract
        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(bNeo.getScriptHash()),
                integer(new BigInteger("0")), any(null));

        // Vote for a candidate
        ECPublicKey candidate = neoToken.getCommittee().get(0);
        // Only owner can vote
        Exception exception = assertThrows(TransactionConfigurationException.class, () -> {
            vote(other, candidate);
        });
        String actualMessage = exception.getMessage();
        assertEquals(ABORT_MESSAGE, actualMessage);
        vote(owner, candidate);

        NeoInvokeFunction result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(bNeo.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(swapRouter.getScriptHash())));
        assertEquals(new BigInteger("10800000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES);
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());

        convertToNeo(owner, new BigInteger("1000"));

        ext.fastForward(3600, 100);

        setReserves(owner, 1, 0);
        // No GAS in bNEO contract, so we only have GAS claim from our NEO that is sent to the swap router
        InvokeResult invokeResult = compound(owner);
        Hash256 txHash = invokeResult.txHash;
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("Transfer", n2.getEventName());
        assertEquals(gasToken.getScriptHash(), n2.getContract());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(cNeo.getScriptHash(), new Hash160(ArrayUtils.reverseArray(stackItems.get(1).getByteArray())));
        assertEquals(new BigInteger("51585"), stackItems.get(2).getInteger());
        
        result = gasToken.callInvokeFunction(BALANCE_OF, List.of(hash160(swapRouter.getScriptHash())));
        assertEquals(new BigInteger("10890050189"), result.getInvocationResult().getStack().get(0).getInteger());

        transfer(bNeo, owner, hash160(owner.getScriptHash()), hash160(swapRouter.getScriptHash()),
                integer(new BigInteger("1000000000")), any(null));
    }

    @Order(21)
    @Test
    public void invokeMixedBurn() throws Throwable {
        NeoInvokeFunction result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("99769357494"), result.getInvocationResult().getStack().get(0).getInteger());

        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("1010"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES, List.of());
        assertEquals(new BigInteger("1010"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("11050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("11050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES, List.of());
        assertEquals(new BigInteger("112050000000"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("109769357494"), result.getInvocationResult().getStack().get(0).getInteger());

        // Burn by transferring cNEO
        Hash256 txHash = transfer(cNeo, owner, hash160(owner.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("99769357494")), any(null));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n2 = execution.getNotifications().get(2);
        assertEquals("Burn", n2.getEventName());
        List<StackItem> stackItems = n2.getState().getList();
        assertEquals(cNeo.getScriptHash(), new Hash160(ArrayUtils.reverseArray(stackItems.get(0).getByteArray())));
        assertEquals(new BigInteger("99769357494"), stackItems.get(1).getInteger());


        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(owner.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("107"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES, List.of());
        assertEquals(new BigInteger("107"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("16977939"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("16977939"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES, List.of());
        assertEquals(new BigInteger("10716977939"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        assertEquals(new BigInteger("10000000000"), result.getInvocationResult().getStack().get(0).getInteger());

        // Burn by transferring cNEO
        txHash = transfer(cNeo, other, hash160(other.getScriptHash()), hash160(cNeo.getScriptHash()),
                integer(new BigInteger("10000000000")), any(null));

        execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        n2 = execution.getNotifications().get(2);
        assertEquals("Burn", n2.getEventName());
        stackItems = n2.getState().getList();
        assertEquals(cNeo.getScriptHash(), new Hash160(ArrayUtils.reverseArray(stackItems.get(0).getByteArray())));
        assertEquals(new BigInteger("10000000000"), stackItems.get(1).getInteger());

        result = cNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(other.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());

        result = neoToken.callInvokeFunction(BALANCE_OF, List.of(hash160(voter.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_NEO_RESERVES, List.of());
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = bNeo.callInvokeFunction(BALANCE_OF, List.of(hash160(cNeo.getScriptHash())));
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_BNEO_RESERVES, List.of());
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(GET_TOTAL_RESERVES, List.of());
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
        result = cNeo.callInvokeFunction(TOTAL_SUPPLY);
        assertEquals(new BigInteger("0"), result.getInvocationResult().getStack().get(0).getInteger());
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

    private static InvokeResult invoke(SmartContract token, Account caller, String method, ContractParameter... params) throws Throwable {
        Transaction tx = token.invokeFunction(method, params)
                .signers(AccountSigner.calledByEntry(caller))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), caller.getECKeyPair()))
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        return new InvokeResult(tx, txHash);
    }

    private static Hash256 transfer(SmartContract token, Account caller, ContractParameter... params) throws Throwable {
        return invoke(token, caller, TRANSFER, params).txHash;
    }

    private static Hash256 setBneoScriptHash(Account caller, SmartContract contract, Hash160 contractHash) throws Throwable {
        return invoke(contract, caller, SET_BNEO_SCRIPT_HASH, hash160(contractHash)).txHash;
    }

    private static Hash256 setCneoScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        return invoke(voter, caller, SET_CNEO_SCRIPT_HASH, hash160(contractHash)).txHash;
    }

    private static Hash256 setVoterScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        return invoke(cNeo, caller, SET_VOTER_SCRIPT_HASH, hash160(contractHash)).txHash;
    }

    private static Hash256 setSwapFactoryScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        return invoke(cNeo, caller, SET_SWAP_FACTORY_SCRIPT_HASH, hash160(contractHash)).txHash;
    }

    private static Hash256 setSwapRouterScriptHash(Account caller, Hash160 contractHash) throws Throwable {
        return invoke(cNeo, caller, SET_SWAP_ROUTER_SCRIPT_HASH, hash160(contractHash)).txHash;
    }

    private static Hash256 setOwner(Account newOwner, Account oldOwner) throws Throwable {
        Transaction tx = cNeo.invokeFunction(SET_OWNER, hash160(newOwner.getScriptHash()))
                .signers(AccountSigner.calledByEntry(oldOwner), AccountSigner.calledByEntry(newOwner))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), oldOwner.getECKeyPair()))
                .addWitness(Witness.create(tx.getHashData(), newOwner.getECKeyPair()))
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        return txHash;
    }

    private static Hash256 setCompoundPeriod(Account caller, BigInteger compoundPeriod) throws Throwable {
        return invoke(cNeo, caller, SET_COMPOUND_PERIOD, integer(compoundPeriod)).txHash;
    }

    private static Hash256 setFeeBasisPoints(Account caller, BigInteger feePercent) throws Throwable {
        return invoke(cNeo, caller, SET_FEE_BASIS_POINTS, integer(feePercent)).txHash;
    }

    private static Hash256 setMaxSupply(Account caller, BigInteger maxSupply) throws Throwable {
        return invoke(cNeo, caller, SET_MAX_SUPPLY, integer(maxSupply)).txHash;
    }

    private static Hash256 setGasReward(Account caller, BigInteger gasReward) throws Throwable {
        return invoke(cNeo, caller, SET_GAS_REWARD, integer(gasReward)).txHash;
    }

    private static Hash256 setMaxSwapGas(Account caller, BigInteger maxSwapGas) throws Throwable {
        return invoke(cNeo, caller, SET_MAX_SWAP_GAS, integer(maxSwapGas)).txHash;
    }

    private static Hash256 setExitFee(Account caller, BigInteger exitFee) throws Throwable {
        return invoke(cNeo, caller, SET_EXIT_FEE, integer(exitFee)).txHash;
    }

    private static Hash256 setBurgerAgentScriptHash(Account caller, SmartContract contract, Hash160 burgerAgent) throws Throwable {
        return invoke(contract, caller, SET_BURGER_AGENT_SCRIPT_HASH, hash160(burgerAgent)).txHash;
    }

    private static class InvokeResult {
        Transaction tx;
        Hash256 txHash;

        public InvokeResult(Transaction tx, Hash256 txHash) {
            this.tx = tx;
            this.txHash = txHash;
        }
    }

    private static InvokeResult vote(Account caller, ECPublicKey candidate) throws Throwable {
        return invoke(voter, caller, VOTE, publicKey(candidate));
    }

    private static InvokeResult compound(Account caller) throws Throwable {
        return invoke(cNeo, caller, COMPOUND, hash160(caller.getScriptHash()));
    }

    private static InvokeResult compoundReserves(Account caller, BigInteger gasQuantity) throws Throwable {
        return invoke(cNeo, caller, COMPOUND_RESERVES, integer(gasQuantity));
    }

    private static Hash256 convertToNeo(Account caller, BigInteger neoQuantity) throws Throwable {
        return invoke(cNeo, caller, CONVERT_TO_NEO, integer(neoQuantity)).txHash;
    }

    private static Hash256 convertToBneo(Account caller, BigInteger neoQuantity) throws Throwable {
        return invoke(cNeo, caller, CONVERT_TO_BNEO, integer(neoQuantity)).txHash;
    }

    private static Hash256 withdrawGas(Account caller, BigInteger withdrawQuantity) throws Throwable {
        return invoke(cNeo, caller, WITHDRAW_GAS, hash160(caller.getScriptHash()), integer(withdrawQuantity)).txHash;
    }

    private static Hash256 setToken0(Account caller, Hash160 token0) throws Throwable {
        return invoke(swapRouter, caller, SET_TOKEN0, hash160(token0)).txHash;
    }

    private static Hash256 setToken1(Account caller, Hash160 token1) throws Throwable {
        return invoke(swapRouter, caller, SET_TOKEN1, hash160(token1)).txHash;
    }

    private static Hash256 setReserves(Account caller, int reserve0, int reserve1) throws Throwable {
        return invoke(swapRouter, caller, SET_RESERVES, integer(reserve0), integer(reserve1)).txHash;
    }
}
