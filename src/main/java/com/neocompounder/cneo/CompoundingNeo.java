package com.neocompounder.cneo;

import com.neocompounder.cneo.interfaces.FlamingoSwapRouterContract;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.Transaction;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.Struct;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

// Change these for each token; substitution not possible
@DisplayName("CompoundingNeo")
@ManifestExtra(key = "Author", value = "Lyrebird Finance")
@ManifestExtra(key = "Email", value = "hello@lyrebird.finance")
@ManifestExtra(key = "Description", value = "An auto-compounding NEO")
@SupportedStandard(neoStandard = NeoStandard.NEP_17)
@ContractSourceCode("https://github.com/neocompounder/cneo-contract/blob/main/src/main/java/com/neocompounder/cneo/CompoundingNeo.java")
@Permission(contract = "*", methods = "*")
public class CompoundingNeo {
    private static StorageContext ctx = Storage.getStorageContext();
    private static StorageContext rtx = Storage.getReadOnlyContext();

    // Until the project is profitable, we may need to top up contract GAS reserves
    private static final String ACTION_TOP_UP_GAS = "TOP_UP_GAS";
    // 1e18
    private static int FLOAT_MULTIPLIER = StringLiteralHelper.stringToInt("1000000000000000000");
    // To redeem bNEO to NEO, we need to send 100000 GAS per 1 NEO
    private static final int GAS_FOR_NEO = 100000;
    private static final int PERCENT = 100;

    private static final int INITIAL_SUPPLY = 0;
    // 1 week in milliseconds
    private static final int INITIAL_COMPOUND_PERIOD = StringLiteralHelper.stringToInt("604800000");
    private static final int INITIAL_FEE_PERCENT = 5;
    // 1 million cNEO
    private static final int INITIAL_MAX_SUPPLY = StringLiteralHelper.stringToInt("100000000000000");
    // 1 GAS
    private static final int INITIAL_MAX_GAS_REWARD = 100000000;
    // 10%
    private static final int INITIAL_MAX_FEE_PERCENT = 10;
    private static final int DECIMALS = 8;
    private static final String SYMBOL = "cNEO";
    private static final String BALANCE_OF = "balanceOf";

    // Keys
    private static final byte[] OWNER_KEY = new byte[]{0x00};
    private static final byte[] SUPPLY_KEY = new byte[]{0x01};
    private static final byte[] NEO_RESERVES_KEY = new byte[]{0x02};
    private static final byte[] MAX_SUPPLY_KEY = new byte[]{0x03};
    private static final byte[] BNEO_RESERVES_KEY = new byte[]{0x04};
    private static final byte[] BNEO_HASH_KEY = new byte[]{0x05};
    private static final byte[] SWAP_PAIR_HASH_KEY = new byte[]{0x06};
    private static final byte[] SWAP_ROUTER_HASH_KEY = new byte[]{0x07};
    private static final byte[] LAST_COMPOUNDED_KEY = new byte[]{0x08};
    private static final byte[] COMPOUND_PERIOD_KEY = new byte[]{0x09};
    private static final byte[] FEE_PERCENT_KEY = new byte[]{0x0a};
    private static final byte[] GAS_REWARD_KEY = new byte[]{0x0b};

    private static StorageMap BALANCE_MAP = new StorageMap(Storage.getStorageContext(), new byte[]{0x0c});
    private static StorageMap BNEO_AGENT_MAP = new StorageMap(Storage.getStorageContext(), new byte[]{0x0d});

    private static final byte[] MAX_GAS_REWARD_KEY = new byte[]{0x0e};
    private static final byte[] MAX_FEE_PERCENT_KEY = new byte[]{0x0f};

    // Hex strings
    private static final ByteString COMPOUND = StringLiteralHelper.hexToBytes("636f6d706f756e64");
    private static final ByteString SYSTEM_CONTRACT_CALL = StringLiteralHelper.hexToBytes("627d5b52");

    // Events
    @DisplayName("Mint")
    private static Event2Args<Hash160, Integer> onMint;

    @DisplayName("Burn")
    private static Event2Args<Hash160, Integer> onBurn;
    
    @DisplayName("Transfer")
    private static Event3Args<Hash160, Hash160, Integer> onTransfer;

    @DisplayName("Compound")
    private static Event4Args<Hash160, Integer, Integer, Integer> onCompound;

    @DisplayName("TopUpGas")
    private static Event2Args<Hash160, Integer> onTopUpGas;

    @DisplayName("CompoundReserves")
    private static Event2Args<Integer, Integer> onCompoundReserves;

    @DisplayName("WithdrawGas")
    private static Event2Args<Hash160, Integer> onWithdrawGas;

    @DisplayName("ConvertToNeo")
    private static Event1Arg<Integer> onConvertToNeo;

    @DisplayName("ConvertToBneo")
    private static Event1Arg<Integer> onConvertToBneo;

    /**
     * This event is intended to be fired before aborting the VM. The first argument should be a message and the
     * second argument should be the method name whithin which it has been fired.
     */
    @DisplayName("Error")
    private static Event2Args<String, String> onError;

    
    // Lifecycle Methods
    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            final Hash160 owner = (Hash160) data;
            validateHash160(owner, "owner");

            // Initialize the supply
            Storage.put(ctx, SUPPLY_KEY, INITIAL_SUPPLY);
            Storage.put(ctx, OWNER_KEY, owner);

            // Allocate all tokens to the contract owner.
            BALANCE_MAP.put(owner.toByteArray(), INITIAL_SUPPLY);
            onTransfer.fire(null, owner, INITIAL_SUPPLY);
        }
    }

    public static void update(ByteString script, String manifest) {
        validateOwner("update");
        (new ContractManagement()).update(script, manifest);
    }

    public static void destroy() {
        validateOwner("destroy");
        (new ContractManagement()).destroy();
    }
    
    @OnVerification
    public static boolean verify() throws Exception {
        // The owner is always verified
        if (Runtime.checkWitness(getOwner())) {
            return true;
        }

        Transaction tx = (Transaction) Runtime.getScriptContainer();
        ByteString script = tx.script;
        ByteString cneo = Runtime.getExecutingScriptHash().toByteString();

        // Otherwise, only allow compound() to be called with verification
        return  script.length() == 62
                && script.range(0, 1).equals(new ByteString(new byte[]{0x0c})) // PUSHDATA1
                && script.range(1, 1).equals(new ByteString(20)) // 20
                // script.range(2, 20) is the invoker's address, whose value we don't care much about
                && script.range(22, 1).equals(new ByteString(new byte[]{0x11})) // PUSH1
                && script.range(23, 1).equals(new ByteString(new byte[]{(byte) 0xc0})) // PACK
                && script.range(24, 1).equals(new ByteString(new byte[]{0x1f})) // PUSH15
                && script.range(25, 1).equals(new ByteString(new byte[]{0x0c})) // PUSHDATA1
                && script.range(27, 8).equals(COMPOUND) // compound
                && script.range(37, 20).equals(cneo) // cNEO script hash
                && script.range(57, 1).equals(new ByteString(new byte[]{0x41})) // SYSCALL
                && script.range(58, 4).equals(SYSTEM_CONTRACT_CALL) // System.Contract.Call
                // Allowing the sender to be the contract is dangerous because
                // an attacker could drain the contract's GAS through repeated FAULT transactions
                && !tx.sender.toByteString().equals(cneo); // Sender
    }

    // Contract Methods
    public static void setOwner(Hash160 owner) throws Exception {
        validateOwner("setOwner");
        validateAccount(owner, "setOwner");
        validateHash160(owner, "owner");

        Storage.put(ctx, OWNER_KEY, owner);
    }

    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(rtx, OWNER_KEY);
    }
    
    public static void setBneoScriptHash(Hash160 bneoHash) throws Exception {
        validateOwner("setBneoScriptHash");
        validateContract(bneoHash, "bneoHash");

        Storage.put(ctx, BNEO_HASH_KEY, bneoHash);
    }

    @Safe
    public static Hash160 getBneoScriptHash() {
        final Hash160 storageVal = Storage.getHash160(rtx, BNEO_HASH_KEY);
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void setSwapPairScriptHash(Hash160 swapPairHash) throws Exception {
        validateOwner("setSwapPairScriptHash");
        validateContract(swapPairHash, "swapPairHash");

        Storage.put(ctx, SWAP_PAIR_HASH_KEY, swapPairHash);
    }

    @Safe
    public static Hash160 getSwapPairScriptHash() {
        final Hash160 storageVal = Storage.getHash160(rtx, SWAP_PAIR_HASH_KEY);
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void setSwapRouterScriptHash(Hash160 swapRouterHash) throws Exception {
        validateOwner("setSwapRouterScriptHash");
        validateContract(swapRouterHash, "swapRouterHash");

        Storage.put(ctx, SWAP_ROUTER_HASH_KEY, swapRouterHash);
    }

    @Safe
    public static Hash160 getSwapRouterScriptHash() {
        final Hash160 storageVal = Storage.getHash160(rtx, SWAP_ROUTER_HASH_KEY);
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    private static void setLastCompounded(int lastCompounded) throws Exception {
        validatePositiveNumber(lastCompounded, "lastCompounded");

        Storage.put(ctx, LAST_COMPOUNDED_KEY, lastCompounded);
    }

    @Safe
    public static int getLastCompounded() {
        return Storage.getIntOrZero(rtx, LAST_COMPOUNDED_KEY);
    }

    public static void setCompoundPeriod(int compoundPeriod) throws Exception {
        validateOwner("setCompoundPeriod");
        validatePositiveNumber(compoundPeriod, "compoundPeriod");

        Storage.put(ctx, COMPOUND_PERIOD_KEY, compoundPeriod);
    }

    @Safe
    public static int getCompoundPeriod() {
        final Integer storageVal = Storage.getInt(rtx, COMPOUND_PERIOD_KEY);
        return storageVal == null ? INITIAL_COMPOUND_PERIOD : storageVal;
    }

    public static void setFeePercent(int feePercent) throws Exception {
        validateOwner("setFeePercent");
        validatePositiveNumber(feePercent, "feePercent");
        assert feePercent <= getMaxFeePercent();

        Storage.put(ctx, FEE_PERCENT_KEY, feePercent);
    }

    @Safe
    public static int getFeePercent() {
        final Integer storageVal = Storage.getInt(rtx, FEE_PERCENT_KEY);
        return storageVal == null ? INITIAL_FEE_PERCENT : storageVal;
    }

    public static void setMaxFeePercent(int maxFeePercent) throws Exception {
        validateOwner("setMaxFeePercent");
        validatePositiveNumber(maxFeePercent, "maxFeePercent");
        assert maxFeePercent < 100;

        Storage.put(ctx, MAX_FEE_PERCENT_KEY, maxFeePercent);
    }

    @Safe
    public static int getMaxFeePercent() {
        final Integer storageVal = Storage.getInt(rtx, MAX_FEE_PERCENT_KEY);
        return storageVal == null ? INITIAL_MAX_FEE_PERCENT : storageVal;
    }

    public static void setMaxSupply(int maxSupply) throws Exception {
        validateOwner("setMaxSupply");
        validatePositiveNumber(maxSupply, "maxSupply");
        assert maxSupply >= totalSupply();

        Storage.put(ctx, MAX_SUPPLY_KEY, maxSupply);
    }

    @Safe
    public static int getMaxSupply() {
        final Integer storageVal = Storage.getInt(rtx, MAX_SUPPLY_KEY);
        return storageVal == null ? INITIAL_MAX_SUPPLY : storageVal;
    }

    public static void setGasReward(int gasReward) throws Exception {
        validateOwner("setGasReward");
        validatePositiveNumber(gasReward, "gasReward");
        assert gasReward <= getMaxGasReward();

        Storage.put(ctx, GAS_REWARD_KEY, gasReward);
    }

    @Safe
    public static int getGasReward() {
        return Storage.getIntOrZero(rtx, GAS_REWARD_KEY);
    }

    public static void setMaxGasReward(int maxGasReward) throws Exception {
        validateOwner("setMaxGasReward");
        validatePositiveNumber(maxGasReward, "maxGasReward");

        Storage.put(ctx, MAX_GAS_REWARD_KEY, maxGasReward);
    }

    @Safe
    public static int getMaxGasReward() {
        final Integer storageVal = Storage.getInt(rtx, MAX_GAS_REWARD_KEY);
        return storageVal == null ? INITIAL_MAX_GAS_REWARD : storageVal;
    }

    public static void setBurgerAgentScriptHash(Hash160 burgerAgentHash) throws Exception {
        validateOwner("setBurgerAgentScriptHash");
        validateContract(burgerAgentHash, "burgerAgentHash");

        BNEO_AGENT_MAP.put(burgerAgentHash.toByteArray(), 1);
    }

    public static void unsetBurgerAgentScriptHash(Hash160 burgerAgentHash) throws Exception {
        validateOwner("unsetBurgerAgentScriptHash");
        validateContract(burgerAgentHash, "burgerAgentHash");

        BNEO_AGENT_MAP.delete(burgerAgentHash.toByteArray());
    }

    private static boolean isBurgerAgent(Hash160 burgerAgentHash) throws Exception {
        validateHash160(burgerAgentHash, "burgerAgentHash");

        final Boolean storageVal = BNEO_AGENT_MAP.getBoolean(burgerAgentHash.toByteArray());
        return storageVal == null ? false : storageVal;
    }

    @Safe
    public static String symbol() {
        return SYMBOL;
    }
    
    @Safe
    public static Integer decimals() {
        return DECIMALS;
    }
    
    @Safe
    public static int balanceOf(Hash160 account) throws Exception {
        validateHash160(account, "account");
        return getBalance(account);
    }

    @Safe
    public static int totalSupply() {
        return Storage.getIntOrZero(rtx, SUPPLY_KEY);
    }

    @Safe
    public static int getBneoReserves() {
        return Storage.getIntOrZero(rtx, BNEO_RESERVES_KEY);
    }

    @Safe
    public static int getNeoReserves() {
        return Storage.getIntOrZero(rtx, NEO_RESERVES_KEY);
    }

    @Safe
    public static int getTotalReserves() {
        return getBneoReserves() + (getNeoReserves() * getBneoMultiplier());
    }

    public static boolean transfer(Hash160 from, Hash160 to, int amount, Object data) throws Exception {
        validateHash160(from, "from");
        validateHash160(to, "to");
        validateNonNegativeNumber(amount, "amount");

        final int fromBalance = getBalance(from);
        if (fromBalance < amount) {
            return false;
        }
        if (!Runtime.checkWitness(from)) {
            return false;
        }

        // Skip balance changes if transferring to yourself or transferring 0
        if (!from.equals(to) && amount != 0) {
            if (fromBalance == amount) {
                BALANCE_MAP.delete(from.toByteArray());
            } else {
                deductFromBalance(from, amount);
            }
            addToBalance(to, amount);
        }

        onTransfer.fire(from, to, amount);
        postTransfer(from, to, amount, data);

        return true;
    }
    
    @Struct
    static class NEP17Payload {
        String action;
    }

    /**
     * Withdraw GAS profits from the contract
     *
     * @param gasQuantity the quantity of GAS to withdraw
     */
    public static void withdrawGas(Hash160 account, int withdrawQuantity) throws Exception {
        validateOwner("withdrawGas");
        validateAccount(account, "withdrawGas");

        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        GasToken gasContract = new GasToken();
        int curBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int remainingBalance = curBalance - withdrawQuantity;

        validatePositiveNumber(remainingBalance, "remainingBalance");

        boolean transferSuccess = gasContract.transfer(cneoHash, account, withdrawQuantity, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to withdraw GAS", "withdrawGas");
        }
        onWithdrawGas.fire(account, withdrawQuantity);
    }

    /**
     * Compound underlying bNEO reserves
     * This can only be called every COMPOUND_PERIOD
     *
     * @param account the address of the transaction signer
     */
    public static void compound(Hash160 account) throws Exception {
        validateAccount(account, "compound");
        validateCompoundTime();

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FungibleToken bneoContract = new FungibleToken(bneoHash);
        NeoToken neoContract = new NeoToken();
        GasToken gasContract = new GasToken();

        int beforeBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});

        // Send 0 NEO to self to claim GAS
        boolean transferSuccess = neoContract.transfer(cneoHash, cneoHash, 0, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to claim GAS from NEO", "compound");
        }

        // Send 0 bNEO to the bNEO contract to receive GAS
        transferSuccess = bneoContract.transfer(cneoHash, bneoHash, 0, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to claim GAS from bNEO", "compound");
        }

        int afterBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int gasQuantity = afterBalance - beforeBalance;
        int treasuryCut = (gasQuantity * getFeePercent()) / PERCENT;
        int gasToSwap = gasQuantity - treasuryCut;
        int bneoQuantity = gasToSwap > 0 ? swapGasForBneo(gasToSwap) : 0;

        // Reward the invoker for a job well done
        transferSuccess = gasContract.transfer(cneoHash, account, getGasReward(), null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to transfer GAS reward to invoker", "compound");
        }

        onCompound.fire(account, gasQuantity, bneoQuantity, treasuryCut);
    }

    /**
     * Compound a portion of the contract's GAS reserves
     * into more bNEO reserves
     *
     * @param gasQuantity the quantity of GAS reserves to compound
     */
    public static void compoundReserves(int gasQuantity) throws Exception {
        validateOwner("compoundReserves");
        validatePositiveNumber(gasQuantity, "gasQuantity");

        GasToken gasContract = new GasToken();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        int gasBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int remainingBalance = gasBalance - gasQuantity;

        validatePositiveNumber(remainingBalance, "remainingBalance");

        int bneoQuantity = gasQuantity > 0 ? swapGasForBneo(gasQuantity) : 0;
        onCompoundReserves.fire(gasQuantity, bneoQuantity);
    }

    public static void vote(ECPoint candidate) throws Exception {
        validateOwner("vote");
        validateECPoint(candidate, "vote");

        NeoToken neoContract = new NeoToken();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        neoContract.vote(cneoHash, candidate);
    }

    /**
     * This method swaps NEO reserves to bNEO reserves
     * but **does not** handle the NEO/bNEO reserves accounting
     *
     * @param neoQuantity the quantity of NEO desired
     */
    private static int convertToBneoInternal(int neoQuantity) throws Exception {
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        NeoToken neoContract = new NeoToken();

        int bneoQuantity = neoQuantity * getBneoMultiplier();

        if (neoQuantity > getNeoReserves()) {
            throw new Exception("The parameter 'neoQuantity' must be smaller than the NEO reserves");
        }

        // Swap NEO for bNEO
        int beforeBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        boolean transferSuccess = neoContract.transfer(cneoHash, bneoHash, neoQuantity, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to swap NEO for bNEO", "convertToBneoInternal");
        }

        int afterBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int actualBneoQuantity = afterBalance - beforeBalance;
        if (bneoQuantity != actualBneoQuantity) {
            fireErrorAndAbort("bneoQuantity != actualBneoQuantity", "convertToBneoInternal");
        }

        return bneoQuantity;
    }

    public static void convertToBneo(int neoQuantity) throws Exception {
        validateOwner("convertToBneo");
        validatePositiveNumber(neoQuantity, "neoQuantity");

        int bneoQuantity = convertToBneoInternal(neoQuantity);

        addToBneoReserves(bneoQuantity);
        deductFromNeoReserves(neoQuantity);

        onConvertToBneo.fire(neoQuantity);
    }

    public static void convertToNeo(int neoQuantity) throws Exception {
        validateOwner("convertToNeo");
        validatePositiveNumber(neoQuantity, "neoQuantity");

        NeoToken neoContract = new NeoToken();
        GasToken gasContract = new GasToken();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();

        int bneoQuantity = neoQuantity * getBneoMultiplier();

        if (bneoQuantity > getBneoReserves()) {
            throw new Exception("The parameter 'bneoQuantity' must be smaller than the bNEO reserves");
        }

        int gasQuantity = neoQuantity * GAS_FOR_NEO;
        int gasBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});

        if (gasQuantity > gasBalance) {
            throw new Exception("The parameter 'gasQuantity' must be smaller than the GAS reserves");
        }

        int beforeBalance = (int) Contract.call(neoContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        boolean transferSuccess = gasContract.transfer(cneoHash, bneoHash, gasQuantity, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to swap bNEO for NEO", "convertToNeo");
        }

        int afterBalance = (int) Contract.call(neoContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int actualNeoQuantity = afterBalance - beforeBalance;
        if (actualNeoQuantity != neoQuantity) {
            fireErrorAndAbort("neoQuantity != actualNeoQuantity", "convertToNeo");
        }

        deductFromBneoReserves(bneoQuantity);
        addToNeoReserves(neoQuantity);

        onConvertToNeo.fire(neoQuantity);
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        Hash160 tokenHash = Runtime.getCallingScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 swapPairHash = getSwapPairScriptHash();
        NeoToken neoContract = new NeoToken();
        GasToken gasContract = new GasToken();

        // Case 0: Mint
        if (from == null) {
            // 0a) bNEO mint - execution continues in handleNeoDeposit or convertToNeo
            if (tokenHash.equals(bneoHash)) {
                return;
            }
            // 0b) GAS mint (claim)
            if (tokenHash.equals(gasContract.getHash())) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from mint but not bNEO or GAS", "onNEP17Payment");
            }
        }

        // Case 1: Called by self
        if (from.equals(cneoHash)) {
            // 1a) NEO transfer (GAS claim) - execution continues in compound
            if (tokenHash.equals(neoContract.getHash())) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from self not NEO", "onNEP17Payment");
            }
        }

        // Case 2: Called by NeoBurger contract
        if (from.equals(bneoHash)) {
            // 2a) bNEO GAS claim - execution continues in compound
            if (tokenHash.equals(gasContract.getHash())) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from NeoBurger but not GAS", "onNEP17Payment");
            }
        }

        // Case 3: Called by the GAS-bNEO swap pair
        else if (from.equals(swapPairHash)) {
            // 3a) bNEO swap - execution continues in swapGasForBneo
            if (tokenHash.equals(bneoHash)) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from GAS-bNEO swap pair but not bNEO", "onNEP17Payment");
            }
        }

        // Case 4: Called by a bNEO agent
        else if (isBurgerAgent(from)) {
            // 4a) bNEO redemption - execution continues in convertToBneo
            if (tokenHash.equals(neoContract.getHash())) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from bNEO agent but not NEO", "onNEP17Payment");
            }
        }

        // Case 5: non-null payload - must satisfy (data instanceof List) && (data[0] instanceof String)
        else if (data != null) {
            NEP17Payload params = (NEP17Payload) data;
            String action = params.action;

            // 5a) GAS top-up for continued operations
            if (tokenHash.equals(gasContract.getHash()) && ACTION_TOP_UP_GAS.equals(action)) {
                onTopUpGas.fire(from, amount);
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer with non-null payload but unknown action", "onNEP17Payment");
            }
        }

        // Case 6: null payload, not from earlier cases
        else {
            // 6a) Handle incoming NEO: swap for bNEO and mint cNEO
            if (tokenHash.equals(neoContract.getHash())) {
                handleNeoDeposit(from, amount);
            }
            // 6b) Handle incoming bNEO: mint cNEO
            else if (tokenHash.equals(bneoHash)) {
                handleBneoDeposit(from, amount);
            }
            // 6c) Handle incoming cNEO: withdraw bNEO
            else if (tokenHash.equals(cneoHash)) {
                handleBneoWithdraw(from, amount);
            }
            else {
                fireErrorAndAbort("NEP17Transfer with null params must be one of NEO, bNEO, or cNEO", "onNEP17Payment");
            }
        }
    }

    // Helper Methods

    /**
     * 1. Burn the cNEO offered by the user
     * 2. Send the corresponding quantity of bNEO back to the requesting user
     * 
     * @param account      the account that is withdrawing bNEO
     * @param cneoQuantity the quantity of cNEO to be burned
     */
    private static void handleBneoWithdraw(Hash160 account, int cneoQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(cneoQuantity, "cneoQuantity");

        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FungibleToken bneoContract = new FungibleToken(getBneoScriptHash());

        int bneoQuantity = burnCneoForBneo(cneoQuantity, cneoHash);
        boolean transferSuccess = bneoContract.transfer(Runtime.getExecutingScriptHash(), account, bneoQuantity, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to transfer released bNEO to account", "handleBneoWithdraw");
        }
    }

    /**
     * 1. Convert the incoming NEO quantity into bNEO
     * 2. Mint and transfer the corresponding quantity of cNEO to the account
     * 
     * @param account     the account that is depositing NEO
     * @param neoQuantity the quantity of NEO to be converted into bNEO and locked
     */
    private static void handleNeoDeposit(Hash160 account, int neoQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(neoQuantity, "neoQuantity");

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        NeoToken neoContract = new NeoToken();

        // Swap incoming NEO for bNEO
        int beforeBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});

        boolean transferSuccess = neoContract.transfer(cneoHash, bneoHash, neoQuantity, null);
        if (!transferSuccess) {
            fireErrorAndAbort("Failed to swap NEO for bNEO", "handleNeoDeposit");
        }
        
        int afterBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int bneoQuantity = afterBalance - beforeBalance;

        mintCneoFromBneo(bneoQuantity, account);
    }

    /**
     * Mint and transfer the corresponding quantity of cNEO to the account
     * 
     * @param account      the account that is depositing bNEO
     * @param bneoQuantity the quantity of bNEO to be locked
     */
    private static void handleBneoDeposit(Hash160 account, int bneoQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(bneoQuantity, "bneoQuantity");

        mintCneoFromBneo(bneoQuantity, account);
    }

    /**
     * Compute the (bNEO + NEO):cNEO ratio in this contract
     *
     * @return the ratio (bNEO + NEO reserves) / (cNEO supply), multiplied by {@link #FLOAT_MULTIPLIER}
     */
    @Safe
    public static int getReserveRatio() {
        int totalReserves = getTotalReserves();
        int cneoSupply = totalSupply();

        // Special case when cNEO supply is 0
        if (cneoSupply == 0) {
            return FLOAT_MULTIPLIER;
        }

        // Otherwise, this is a valid number
        return (FLOAT_MULTIPLIER * totalReserves) / cneoSupply;
    }

    /**
     * Burn cNEO corresponding to outgoing bNEO
     *
     * @param cneoQuantity the quantity of cNEO to be burned
     * @param account      the account from which the cNEO is to be burned
     * @return the released bNeo quantity
     */
    private static int burnCneoForBneo(int cneoQuantity, Hash160 account) throws Exception {
        int bneoToCneoRatio = getReserveRatio();
        int computedBneoQuantity = (bneoToCneoRatio * cneoQuantity ) / FLOAT_MULTIPLIER;
        // bneoQuantity can be greater than bneoReserves due to float precision
        int bneoQuantity = Math.min(computedBneoQuantity, getTotalReserves());

        burn(account, cneoQuantity);

        // Convert NEO to bNEO if necessary
        int bneoReserves = getBneoReserves();
        int convertedBneoQuantity = 0;
        if (bneoQuantity > bneoReserves) {
            int missingBneoQuantity = bneoQuantity - bneoReserves;
            int bneoMultiplier = getBneoMultiplier();
            int floorNeoQuantity = missingBneoQuantity / bneoMultiplier;
            int neoQuantity = missingBneoQuantity % bneoMultiplier == 0 ? floorNeoQuantity : floorNeoQuantity + 1;

            convertedBneoQuantity = convertToBneoInternal(neoQuantity);
            deductFromNeoReserves(neoQuantity);
        }

        // Accounting
        int burnBneoQuantity = bneoQuantity - convertedBneoQuantity;
        int newBneoReserves = getBneoReserves();
        if (newBneoReserves < burnBneoQuantity) {
            throw new Exception("Attempted to burn more bNEO than the contract reserves");
        }
        deductFromBneoReserves(bneoQuantity - convertedBneoQuantity);

        return bneoQuantity;
    }

    /**
     * Mint cNEO corresponding to incoming bNEO
     *
     * @param bneoQuantity the quantity of bNEO to be locked
     * @param account      the account to which the cNEO is to be minted
     */
    private static void mintCneoFromBneo(int bneoQuantity, Hash160 account) throws Exception {
        int reserveRatio = getReserveRatio();
        // As total reserves increase, the quantity of cNEO minted per bNEO decreases
        int cneoQuantity = (FLOAT_MULTIPLIER * bneoQuantity) / reserveRatio;

        int newSupply = totalSupply() + cneoQuantity;
        int maxSupply = getMaxSupply();
        if (newSupply > maxSupply) {
            StdLib stdLib = new StdLib();
            fireErrorAndAbort("New supply=" + stdLib.itoa(newSupply, 10) + " > max supply=" + stdLib.itoa(maxSupply, 10), "mintCneoFromBneo");
        }
        
        mint(account, cneoQuantity);

        // Accounting
        addToBneoReserves(bneoQuantity);
    }

    /**
     * Swap the incoming quantity of GAS to bNEO on Flamingo
     *
     * @param gasQuantity the quantity of incoming GAS
     * @return the quantity of bNEO added to the reserves
     */
    private static int swapGasForBneo(int gasQuantity) throws Exception {
        validateNonNegativeNumber(gasQuantity, "gasQuantity");

        GasToken gasContract = new GasToken();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FlamingoSwapRouterContract swapRouterContract = new FlamingoSwapRouterContract(getSwapRouterScriptHash());

        int beforeBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        Hash160[] paths = new Hash160[]{ gasContract.getHash(), bneoHash };
        int deadline = Runtime.getTime() + 30;
        boolean swapSuccess = swapRouterContract.swapTokenInForTokenOut(cneoHash, gasQuantity, 0, paths, deadline);
        if (!swapSuccess) {
            fireErrorAndAbort("Failed to swap GAS for bNEO", "swapGasForBneo");
        }
        int afterBalance = (int) Contract.call(bneoHash, BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});
        int bneoQuantity = afterBalance - beforeBalance;

        addToBneoReserves(bneoQuantity);

        return bneoQuantity;
    }

    private static void mint(Hash160 account, int mintQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(mintQuantity, "mintQuantity");

        if (mintQuantity != 0) {
            addToSupply(mintQuantity);
            addToBalance(account, mintQuantity);
    
            onTransfer.fire(null, account, mintQuantity);
            postTransfer(null, account, mintQuantity, null);
        }
        onMint.fire(account, mintQuantity);
    }
    
    private static void burn(Hash160 account, int burnQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(burnQuantity, "burnQuantity");

        final int supply = totalSupply();
        final int accountBalance = getBalance(account);
        if (supply < burnQuantity) {
            throw new Exception("The parameter 'burnQuantity' must be smaller than the total supply");
        }
        if (accountBalance < burnQuantity) {
            throw new Exception("The parameter 'burnQuantity' must be smaller than the account balance");
        }

        if (burnQuantity != 0) {
            deductFromSupply(burnQuantity);
            deductFromBalance(account, burnQuantity);
    
            onTransfer.fire(account, null, burnQuantity);
        }
        onBurn.fire(account, burnQuantity);
    }

    private static void postTransfer(Hash160 from, Hash160 to, int quantity, Object data) {
        if ((new ContractManagement()).getContract(to) != null) {
            Contract.call(to, "onNEP17Payment", CallFlags.All, new Object[]{from, quantity, data});
        }
    }

    private static void fireErrorAndAbort(String msg, String method) {
        onError.fire(msg, method);
        Helper.abort();
    }

    private static void addToSupply(int value) {
        Storage.put(ctx, SUPPLY_KEY, totalSupply() + value);
    }

    private static void deductFromSupply(int value) {
        addToSupply(-value);
    }

    private static void addToBalance(Hash160 key, int value) {
        BALANCE_MAP.put(key.toByteArray(), getBalance(key) + value);
    }

    private static void deductFromBalance(Hash160 key, int value) {
        addToBalance(key, -value);
    }

    private static void addToBneoReserves(int value) {
        Storage.put(ctx, BNEO_RESERVES_KEY, getBneoReserves() + value);
    }

    private static void deductFromBneoReserves(int value) {
        addToBneoReserves(-value);
    }

    private static void addToNeoReserves(int value) {
        Storage.put(ctx, NEO_RESERVES_KEY, getNeoReserves() + value);
    }

    private static void deductFromNeoReserves(int value) {
        addToNeoReserves(-value);
    }

    private static int getBalance(Hash160 key) {
        return BALANCE_MAP.getIntOrZero(key.toByteArray());
    }

    private static int getBneoMultiplier() {
        Hash160 bneoHash = getBneoScriptHash();
        int bneoDecimals = (int) Contract.call(bneoHash, "decimals", CallFlags.ReadOnly, new Object[]{});
        return Helper.pow(10, bneoDecimals);
    }

    private static void validateCompoundTime() throws Exception {
        int curTime = Runtime.getTime();
        int nextCompound = getLastCompounded() + getCompoundPeriod();
        if (nextCompound > curTime) {
            StdLib stdLib = new StdLib();
            throw new Exception("Cannot compound until time=" + stdLib.itoa(nextCompound, 10) + ", curTime=" + stdLib.itoa(curTime, 10));
        }
        setLastCompounded(curTime);
    }
    
    private static void validateHash160(Hash160 hash, String hashName) throws Exception {
        if (!Hash160.isValid(hash) || hash.isZero()) {
            throw new Exception("The parameter '" + hashName + "' must be a 20-byte address");
        }
    }

    private static void validateContract(Hash160 hash, String hashName) throws Exception {
        validateHash160(hash, hashName);
        if ((new ContractManagement()).getContract(hash) == null) {
            throw new Exception("The parameter '" + hashName + "' must be a contract hash");
        }
    }

    private static void validateECPoint(ECPoint ecPoint, String hashName) throws Exception {
        if (!ECPoint.isValid(ecPoint)) {
            throw new Exception("The parameter '" + hashName + "' must be a 33-byte address");
        }
    }

    private static void validatePositiveNumber(int number, String numberName) throws Exception {
        if (number <= 0) {
            StdLib stdLib = new StdLib();
            throw new Exception("The parameter '" + numberName + "'=" + stdLib.itoa(number, 10) + " must be positive");
        }
    }

    private static void validateNonNegativeNumber(int number, String numberName) throws Exception {
        if (number < 0) {
            StdLib stdLib = new StdLib();
            throw new Exception("The parameter '" + numberName + "'=" + stdLib.itoa(number, 10) + " must be non-negative");
        }
    }

    private static void validateOwner(String method) {
        if (!Runtime.checkWitness(getOwner())) {
            fireErrorAndAbort("Not authorized", method);
        }
    }

    private static void validateAccount(Hash160 account, String method) {
        if (!Runtime.checkWitness(account)) {
            fireErrorAndAbort("Invalid witness", method);
        }
    }
}
