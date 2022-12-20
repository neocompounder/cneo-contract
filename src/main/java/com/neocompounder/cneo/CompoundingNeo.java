package com.neocompounder.cneo;

import com.neocompounder.cneo.interfaces.FlamingoSwapRouterContract;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
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
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

// Change these for each token; substitution not possible
@DisplayName("cNEO")
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
    private static final String ACTION_GAS_TOP_UP = "GAS";
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
    private static final int DECIMALS = 8;
    private static final String SYMBOL = "cNEO";
    private static final String BALANCE_OF = "balanceOf";

    // Keys
    private static final byte[] OWNER_KEY = new byte[]{0x00};
    private static final byte[] SUPPLY_KEY = new byte[]{0x01};
    private static final byte[] BNEO_RESERVES_KEY = new byte[]{0x02};
    private static final byte[] BNEO_HASH_KEY = new byte[]{0x03};
    private static final byte[] SWAP_PAIR_HASH_KEY = new byte[]{0x04};
    private static final byte[] SWAP_ROUTER_HASH_KEY = new byte[]{0x05};
    private static final byte[] LAST_COMPOUNDED_KEY = new byte[]{0x06};
    private static final byte[] COMPOUND_PERIOD_KEY = new byte[]{0x07};
    private static final byte[] FEE_PERCENT_KEY = new byte[]{0x08};
    private static final byte[] GAS_REWARD_KEY = new byte[]{0x09};
    private static final byte[] MAX_SUPPLY_KEY = new byte[]{0x0a};

    private static StorageMap BALANCE_MAP = new StorageMap(Storage.getStorageContext(), new byte[]{0x0b});

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

    @DisplayName("WithdrawGas")
    private static Event2Args<Hash160, Integer> onWithdrawGas;

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
                && tx.sender.toByteString().equals(cneo); // Sender
    }

    // Parameter Methods
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

    private static void setLastCompounded(int lastCompounded) throws Exception {
        validatePositiveNumber(lastCompounded, "lastCompounded");

        Storage.put(ctx, LAST_COMPOUNDED_KEY, lastCompounded);
    }

    public static void setFeePercent(int feePercent) throws Exception {
        validateOwner("setFeePercent");
        validatePositiveNumber(feePercent, "feePercent");

        if (feePercent > 100) {
            throw new Exception("The parameter 'feePercent' must be <= 100");
        }

        Storage.put(ctx, FEE_PERCENT_KEY, feePercent);
    }

    @Safe
    public static int getFeePercent() {
        final Integer storageVal = Storage.getInt(rtx, FEE_PERCENT_KEY);
        return storageVal == null ? INITIAL_FEE_PERCENT : storageVal;
    }

    public static void setMaxSupply(int maxSupply) throws Exception {
        validateOwner("setMaxSupply");
        validatePositiveNumber(maxSupply, "maxSupply");

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

        Storage.put(ctx, GAS_REWARD_KEY, gasReward);
    }

    @Safe
    public static int getGasReward() {
        return Storage.getIntOrZero(rtx, GAS_REWARD_KEY);
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

    public static boolean transfer(Hash160 from, Hash160 to, int amount, Object[] data) throws Exception {
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

    // Contract Methods

    /**
     * Withdraw GAS profits from the contract
     *
     * @param gasQuantity the quantity of GAS to withdraw
     */
    public static void withdrawGas(Hash160 account, int withdrawQuantity) throws Exception {
        validateOwner("setFeePercent");

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
     * The method expects the cNEO contract to be the sender
     * who pays the GAS
     *
     * @param account the address of the transaction signer
     */
    public static void compound(Hash160 account) throws Exception {
        validateAccount(account, "compound");

        int curTime = Runtime.getTime();
        int nextCompound = getLastCompounded() + getCompoundPeriod();
        if (nextCompound > curTime) {
            fireErrorAndAbort("Cannot compound until time=" + (new StdLib()).itoa(nextCompound, 10), "compound");
        }
        setLastCompounded(curTime);

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FungibleToken bneoContract = new FungibleToken(bneoHash);
        GasToken gasContract = new GasToken();

        int beforeBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF, CallFlags.ReadOnly, new Object[]{cneoHash});

        // Send 0 bNEO to the bNEO contract to receive GAS
        boolean transferSuccess = bneoContract.transfer(cneoHash, bneoHash, 0, null);
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
            // 0a) bNEO mint - execution continues in handleNeoDeposit
            if (tokenHash.equals(bneoHash)) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from NeoBurger but not bNEO or GAS", "onNEP17Payment");
            }
        }

        // Case 1: Called by NeoBurger contract
        if (from.equals(bneoHash)) {
            // 1a) GAS claim - execution continues in compound
            if (tokenHash.equals(gasContract.getHash())) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from NeoBurger but not GAS", "onNEP17Payment");
            }
        }

        // Case 2: Called by the GAS-bNEO swap pair
        else if (from.equals(swapPairHash)) {
            // 2a) bNEO swap - execution continues in swapGasForBneo
            if (tokenHash.equals(bneoHash)) {
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer from GAS-bNEO swap pair but not bNEO", "onNEP17Payment");
            }
        }

        // Case 3: non-null payload - must satisfy (data instanceof List) && (data[0] instanceof String)
        else if (data != null) {
            NEP17Payload params = (NEP17Payload) data;
            String action = params.action;

            // 3a) GAS top-up for continued operations
            if (tokenHash.equals(gasContract.getHash()) && ACTION_GAS_TOP_UP.equals(action)) {
                onTopUpGas.fire(from, amount);
                return;
            }
            else {
                fireErrorAndAbort("NEP17Transfer with non-null payload but unknown action", "onNEP17Payment");
            }
        }

        // Case 4: null payload, not from NeoBurger contract or mint
        else {
            // 4a) Handle incoming NEO: swap for bNEO and mint cNEO
            if (tokenHash.equals(neoContract.getHash())) {
                handleNeoDeposit(from, amount);
            }
            // 4b) Handle incoming bNEO: mint cNEO
            else if (tokenHash.equals(bneoHash)) {
                handleBneoDeposit(from, amount);
            }
            // 4c) Handle incoming cNEO: withdraw bNEO (default option)
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
     * Compute the bNEO:cNEO ratio in this contract
     *
     * @return the ratio (bNEO reserves) / (cNEO supply), multiplied by {@link #FLOAT_MULTIPLIER}
     */
    private static int getBneoToCneoRatio() {
        int bneoReserves = getBneoReserves();
        int cneoSupply = totalSupply();

        // Special case when cNEO supply is 0
        if (cneoSupply == 0) {
            return FLOAT_MULTIPLIER;
        }

        // Otherwise, this is a valid number
        return (FLOAT_MULTIPLIER * bneoReserves) / cneoSupply;
    }

    /**
     * Burn cNEO corresponding to outgoing bNEO
     *
     * @param cneoQuantity the quantity of cNEO to be burned
     * @param account      the account from which the cNEO is to be burned
     * @return the released bNeo quantity
     */
    private static int burnCneoForBneo(int cneoQuantity, Hash160 account) throws Exception {
        int bneoToCneoRatio = getBneoToCneoRatio();
        int computedBneoQuantity = (bneoToCneoRatio * cneoQuantity ) / FLOAT_MULTIPLIER;
        // bneoQuantity can be greater than bneoReserves due to float precision
        int bneoQuantity = Math.min(computedBneoQuantity, getBneoReserves());
        burn(account, cneoQuantity);

        // Accounting
        deductFromBneoReserves(bneoQuantity);
        return bneoQuantity;
    }

    /**
     * Mint cNEO corresponding to incoming bNeo
     *
     * @param bneoQuantity the quantity of bNEO to be locked
     * @param account      the account to which the cNEO is to be minted
     */
    private static void mintCneoFromBneo(int bneoQuantity, Hash160 account) throws Exception {
        int bneoToCneoRatio = getBneoToCneoRatio();
        // As bNEO reserves increases, the quantity of cNEO minted per bNEO decreases
        int cneoQuantity = (FLOAT_MULTIPLIER * bneoQuantity) / bneoToCneoRatio;

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
        swapRouterContract.swapTokenInForTokenOut(cneoHash, gasQuantity, 0, paths, deadline);
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
    
    private static void postTransfer(Hash160 from, Hash160 to, int quantity, Object[] data) {
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

    private static int getBalance(Hash160 key) {
        return BALANCE_MAP.getIntOrZero(key.toByteArray());
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

    private static void validatePositiveNumber(int number, String numberName) throws Exception {
        if (number <= 0) {
            throw new Exception("The parameter '" + numberName + "' must be positive");
        }
    }

    private static void validateNonNegativeNumber(int number, String numberName) throws Exception {
        if (number < 0) {
            throw new Exception("The parameter '" + numberName + "' must be non-negative");
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
