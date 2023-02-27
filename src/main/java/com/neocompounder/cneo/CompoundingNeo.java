package com.neocompounder.cneo;

import com.neocompounder.cneo.interfaces.CompoundingNeoVoterContract;
import com.neocompounder.cneo.interfaces.FlamingoSwapPairContract;
import com.neocompounder.cneo.interfaces.FlamingoSwapRouterContract;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.List;
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
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

@DisplayName("CompoundingNeo")
@ManifestExtra(key = "Author", value = "Lyrebird Finance")
@ManifestExtra(key = "Email", value = "hello@lyrebird.finance")
@ManifestExtra(key = "Description", value = "An auto-compounding NEO")
@SupportedStandard(neoStandard = NeoStandard.NEP_17)
@ContractSourceCode("https://github.com/neocompounder/cneo-contract/blob/main/src/main/java/com/neocompounder/cneo/CompoundingNeo.java")
@Permission(contract = "*", methods = "*")
public class CompoundingNeo {
    private static StorageContext CTX() { return Storage.getStorageContext(); }
    private static StorageContext RTX() { return Storage.getReadOnlyContext(); }

    // Until the project is profitable, we may need to top up contract GAS reserves
    private static final String ACTION_TOP_UP_GAS() { return "TOP_UP_GAS"; }
    // 1e18
    private static int FLOAT_MULTIPLIER() { return StringLiteralHelper.stringToInt("1000000000000000000"); }
    // To redeem bNEO to NEO, we need to send 100000 GAS per 1 NEO
    private static final int GAS_FOR_NEO() { return 100000; }
    private static final int PERCENT() { return 100; }

    private static final int INITIAL_SUPPLY() { return 0; }
    // 1 week in milliseconds
    private static final int INITIAL_COMPOUND_PERIOD() { return 604800000; }
    private static final int INITIAL_FEE_PERCENT() { return 5; }
    // 1 million cNEO
    private static final int INITIAL_MAX_SUPPLY() { return StringLiteralHelper.stringToInt("100000000000000"); }
    // 1 GAS
    private static final int INITIAL_MAX_GAS_REWARD() { return 100000000; }
    // 10%
    private static final int INITIAL_MAX_FEE_PERCENT() { return 10; }
    // 10%
    private static final int INITIAL_MAX_SLIPPAGE() { return 10; }
    // 5000 GAS
    private static final int INITIAL_MAX_SWAP_GAS() { return StringLiteralHelper.stringToInt("500000000000"); }
    private static final int DECIMALS() { return 8; }
    private static final String SYMBOL() { return "cNEO"; }
    private static final String BALANCE_OF() { return "balanceOf"; }

    // Keys
    private static final byte[] OWNER_KEY() { return new byte[]{0x00}; }
    private static final byte[] SUPPLY_KEY() { return new byte[]{0x01}; }
    private static final byte[] MAX_SUPPLY_KEY() { return new byte[]{0x03}; }
    private static final byte[] VOTER_HASH_KEY() { return new byte[]{0x04}; }
    private static final byte[] BNEO_HASH_KEY() { return new byte[]{0x05}; }
    private static final byte[] SWAP_PAIR_HASH_KEY() { return new byte[]{0x06}; }
    private static final byte[] SWAP_ROUTER_HASH_KEY() { return new byte[]{0x07}; }
    private static final byte[] LAST_COMPOUNDED_KEY() { return new byte[]{0x08}; }
    private static final byte[] COMPOUND_PERIOD_KEY() { return new byte[]{0x09}; }
    private static final byte[] FEE_PERCENT_KEY() { return new byte[]{0x0a}; }
    private static final byte[] GAS_REWARD_KEY() { return new byte[]{0x0b}; }

    private static StorageMap BALANCE_MAP() { return new StorageMap(Storage.getStorageContext(), new byte[]{0x0c}); }

    private static final byte[] MAX_GAS_REWARD_KEY() { return new byte[]{0x0e}; }
    private static final byte[] MAX_FEE_PERCENT_KEY() { return new byte[]{0x0f}; }
    private static final byte[] MAX_SLIPPAGE_KEY() { return new byte[]{0x10}; }
    private static final byte[] APPROVED_SWAP_QUANTITY_KEY() { return new byte[]{0x11}; }
    private static final byte[] SWAP_PAIR_GAS_INDEX_KEY() { return new byte[]{0x12}; }
    private static final byte[] MAX_SWAP_GAS_KEY() { return new byte[]{0x13}; }

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

    // State Changes
    @DisplayName("SetFeePercent")
    private static Event1Arg<Integer> onSetFeePercent;

    @DisplayName("SetGasReward")
    private static Event1Arg<Integer> onSetGasReward;

    @DisplayName("SetMaxSwapGas")
    private static Event1Arg<Integer> onSetMaxSwapGas;

    @DisplayName("SetMaxSupply")
    private static Event1Arg<Integer> onSetMaxSupply;

    @DisplayName("SetCompoundPeriod")
    private static Event1Arg<Integer> onSetCompoundPeriod;

    @DisplayName("SetOwner")
    private static Event1Arg<Hash160> onSetOwner;

    @DisplayName("Update")
    private static Event1Arg<Boolean> onUpdate;

    @DisplayName("SetSwapRouterScriptHash")
    private static Event1Arg<Hash160> onSetSwapRouterScriptHash;

    @DisplayName("SetSwapPairScriptHash")
    private static Event1Arg<Hash160> onSetSwapPairScriptHash;

    @DisplayName("SetBneoScriptHash")
    private static Event1Arg<Hash160> onSetBneoScriptHash;
    
    @Struct
    static class NEP17Payload {
        String action;
    }
    
    // Lifecycle Methods
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            final Hash160 owner = (Hash160) data;
            validateHash160(owner, "owner");

            // Initialize the supply
            StorageContext ctx = CTX();
            int initialSupply = INITIAL_SUPPLY();
            Storage.put(ctx, SUPPLY_KEY(), initialSupply);
            Storage.put(ctx, OWNER_KEY(), owner);

            // Allocate all tokens to the contract owner.
            BALANCE_MAP().put(owner.toByteArray(), initialSupply);
            onTransfer.fire(null, owner, initialSupply);
        }
    }

    public static void update(ByteString script, String manifest) {
        validateOwner("update");
        (new ContractManagement()).update(script, manifest);
        onUpdate.fire(true);
    }

    public static void destroy() {
        validateOwner("destroy");
        // Total reserves may not be 0 due to int division
        assert getTotalReserves() == 0 || totalSupply() == 0;

        (new ContractManagement()).destroy();
    }

    // Contract Methods
    public static void setOwner(Hash160 owner) {
        validateOwner("setOwner");
        validateAccount(owner, "setOwner");
        validateHash160(owner, "owner");

        Storage.put(CTX(), OWNER_KEY(), owner);
        onSetOwner.fire(owner);
    }

    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(RTX(), OWNER_KEY());
    }

    public static void setVoterScriptHash(Hash160 voterHash) {
        validateOwner("setVoterScriptHash");
        validateContract(voterHash, "voterHash");

        Storage.put(CTX(), VOTER_HASH_KEY(), voterHash);
    }

    @Safe
    public static Hash160 getVoterScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), VOTER_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void setBneoScriptHash(Hash160 bneoHash) {
        validateOwner("setBneoScriptHash");
        validateContract(bneoHash, "bneoHash");

        Storage.put(CTX(), BNEO_HASH_KEY(), bneoHash);
        onSetBneoScriptHash.fire(bneoHash);
    }

    @Safe
    public static Hash160 getBneoScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), BNEO_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void setSwapPairScriptHash(Hash160 swapPairHash) {
        validateOwner("setSwapPairScriptHash");
        validateContract(swapPairHash, "swapPairHash");

        GasToken gasContract = new GasToken();
        Hash160 gasHash = gasContract.getHash();
        FlamingoSwapPairContract swapPairContract = new FlamingoSwapPairContract(swapPairHash);
        Hash160 token0Hash = swapPairContract.getToken0();
        Hash160 token1Hash = swapPairContract.getToken1();

        StorageContext ctx = CTX();
        byte[] swapPairGasIndexKey = SWAP_PAIR_GAS_INDEX_KEY();
        if (gasHash.equals(token0Hash)) {
            Storage.put(ctx, swapPairGasIndexKey, 0);
        } else if (gasHash.equals(token1Hash)) {
            Storage.put(ctx, swapPairGasIndexKey, 1);
        } else {
            abort("GAS is not one of the tokens in 'swapPairHash'", "setSwapPairScriptHash");
        }

        Storage.put(ctx, SWAP_PAIR_HASH_KEY(), swapPairHash);
        onSetSwapPairScriptHash.fire(swapPairHash);
    }

    @Safe
    public static Hash160 getSwapPairScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), SWAP_PAIR_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    @Safe
    public static int getSwapPairGasIndex() {
        return Storage.getIntOrZero(RTX(), SWAP_PAIR_GAS_INDEX_KEY());
    }

    public static void setSwapRouterScriptHash(Hash160 swapRouterHash) {
        validateOwner("setSwapRouterScriptHash");
        validateContract(swapRouterHash, "swapRouterHash");

        Storage.put(CTX(), SWAP_ROUTER_HASH_KEY(), swapRouterHash);
        onSetSwapRouterScriptHash.fire(swapRouterHash);
    }

    @Safe
    public static Hash160 getSwapRouterScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), SWAP_ROUTER_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    @Safe
    public static int getLastCompounded() {
        return Storage.getIntOrZero(RTX(), LAST_COMPOUNDED_KEY());
    }

    public static void setCompoundPeriod(int compoundPeriod) {
        validateOwner("setCompoundPeriod");
        validatePositiveNumber(compoundPeriod, "compoundPeriod");

        Storage.put(CTX(), COMPOUND_PERIOD_KEY(), compoundPeriod);
        onSetCompoundPeriod.fire(compoundPeriod);
    }

    @Safe
    public static int getCompoundPeriod() {
        final Integer storageVal = Storage.getInt(RTX(), COMPOUND_PERIOD_KEY());
        return storageVal == null ? INITIAL_COMPOUND_PERIOD() : storageVal;
    }

    public static void setFeePercent(int feePercent) {
        validateOwner("setFeePercent");
        validatePositiveNumber(feePercent, "feePercent");
        assert feePercent <= getMaxFeePercent();

        Storage.put(CTX(), FEE_PERCENT_KEY(), feePercent);
        onSetFeePercent.fire(feePercent);
    }

    @Safe
    public static int getFeePercent() {
        final Integer storageVal = Storage.getInt(RTX(), FEE_PERCENT_KEY());
        return storageVal == null ? INITIAL_FEE_PERCENT() : storageVal;
    }

    public static void setMaxSlippage(int maxSlippage) {
        validateOwner("setMaxSlippage");
        validatePositiveNumber(maxSlippage, "maxSlippage");
        assert maxSlippage < 100;

        Storage.put(CTX(), MAX_SLIPPAGE_KEY(), maxSlippage);
    }

    @Safe
    public static int getMaxSlippage() {
        final Integer storageVal = Storage.getInt(RTX(), MAX_SLIPPAGE_KEY());
        return storageVal == null ? INITIAL_MAX_SLIPPAGE() : storageVal;
    }

    public static void setMaxSupply(int maxSupply) {
        validateOwner("setMaxSupply");
        validatePositiveNumber(maxSupply, "maxSupply");
        assert maxSupply >= totalSupply();

        Storage.put(CTX(), MAX_SUPPLY_KEY(), maxSupply);
        onSetMaxSupply.fire(maxSupply);
    }

    @Safe
    public static int getMaxSupply() {
        final Integer storageVal = Storage.getInt(RTX(), MAX_SUPPLY_KEY());
        return storageVal == null ? INITIAL_MAX_SUPPLY() : storageVal;
    }

    public static void setGasReward(int gasReward) {
        validateOwner("setGasReward");
        validatePositiveNumber(gasReward, "gasReward");
        assert gasReward <= getMaxGasReward();

        Storage.put(CTX(), GAS_REWARD_KEY(), gasReward);
        onSetGasReward.fire(gasReward);
    }

    @Safe
    public static int getGasReward() {
        return Storage.getIntOrZero(RTX(), GAS_REWARD_KEY());
    }

    public static void setMaxGasReward(int maxGasReward) {
        validateOwner("setMaxGasReward");
        validatePositiveNumber(maxGasReward, "maxGasReward");

        Storage.put(CTX(), MAX_GAS_REWARD_KEY(), maxGasReward);
    }

    @Safe
    public static int getMaxGasReward() {
        final Integer storageVal = Storage.getInt(RTX(), MAX_GAS_REWARD_KEY());
        return storageVal == null ? INITIAL_MAX_GAS_REWARD() : storageVal;
    }

    public static void setMaxFeePercent(int maxFeePercent) {
        validateOwner("setMaxFeePercent");
        validatePositiveNumber(maxFeePercent, "maxFeePercent");
        assert maxFeePercent < 100;

        Storage.put(CTX(), MAX_FEE_PERCENT_KEY(), maxFeePercent);
    }

    @Safe
    public static int getMaxFeePercent() {
        final Integer storageVal = Storage.getInt(RTX(), MAX_FEE_PERCENT_KEY());
        return storageVal == null ? INITIAL_MAX_FEE_PERCENT() : storageVal;
    }

    public static void setMaxSwapGas(int maxSwapGas) {
        validateOwner("setMaxSwapGas");
        validatePositiveNumber(maxSwapGas, "maxSwapGas");

        Storage.put(CTX(), MAX_SWAP_GAS_KEY(), maxSwapGas);
        onSetMaxSwapGas.fire(maxSwapGas);
    }

    @Safe
    public static int getMaxSwapGas() {
        final Integer storageVal = Storage.getInt(RTX(), MAX_SWAP_GAS_KEY());
        return storageVal == null ? INITIAL_MAX_SWAP_GAS() : storageVal;
    }

    @Safe
    public static String symbol() {
        return SYMBOL();
    }
    
    @Safe
    public static Integer decimals() {
        return DECIMALS();
    }
    
    @Safe
    public static int balanceOf(Hash160 account) {
        validateHash160(account, "account");
        return getBalance(account);
    }

    @Safe
    public static int totalSupply() {
        return Storage.getIntOrZero(RTX(), SUPPLY_KEY());
    }

    @Safe
    public static int getBneoReserves() {
        FungibleToken bneoContract = new FungibleToken(getBneoScriptHash());
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        return bneoContract.balanceOf(cneoHash);
    }

    @Safe
    public static int getNeoReserves() {
        NeoToken neoContract = new NeoToken();
        Hash160 voterHash = getVoterScriptHash();
        return neoContract.balanceOf(voterHash);
    }

    @Safe
    public static int getTotalReserves() {
        return getTotalReservesInternal(getBneoReserves(), getNeoReserves());
    }

    @Safe
    public static int getGasReserves() {
        GasToken gasContract = new GasToken();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        return gasContract.balanceOf(cneoHash);
    }

    /**
     * Compute the (bNEO + NEO):cNEO ratio in this contract
     *
     * @return the ratio (bNEO + NEO reserves) / (cNEO supply), multiplied by {@link #FLOAT_MULTIPLIER}
     */
    @Safe
    public static int getReserveRatio() {
        return getReserveRatioInternal(getBneoReserves(), getNeoReserves(), totalSupply());
    }

    public static boolean transfer(Hash160 from, Hash160 to, int amount, Object data) {
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
                BALANCE_MAP().delete(from.toByteArray());
            } else {
                deductFromBalance(from, amount);
            }
            addToBalance(to, amount);
        }

        onTransfer.fire(from, to, amount);
        postTransfer(from, to, amount, data);

        return true;
    }

    /**
     * This method is invoked by FlamingoSwapRouter to circumvent the need for a verify() method
     * after we invoke swapTokenInForTokenOut
     *
     * @param token  the token to transfer - this is limited to GAS
     * @param to     the contract to transfer to - this is limited to the FlamingoSwapRouter
     * @param amount the quantity of GAS to transfer
     * @param data   any additional data requested by the FlamingoSwapRouter
     * @return       the success status of the GAS transfer
     */
    public static boolean approvedTransfer(Hash160 token, Hash160 to, int amount, byte[] data) {
        // We only allow GAS transfers to swap for bNEO
        GasToken gasContract = new GasToken();
        assert token.equals(gasContract.getHash());

        // We only allow transfers to the FlamingoSwapRouter
        assert to.equals(getSwapPairScriptHash());

        validateNonNegativeNumber(amount, "amount");

        // The transaction quantity must have been pre-approved
        assert getApprovedSwapQuantity() >= amount;
        deductFromApprovedSwapQuantity(amount);

        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        return gasContract.transfer(cneoHash, to, amount, data);
    }

    /**
     * Withdraw GAS profits from the contract
     *
     * @param account          the address of the GAS receiver
     * @param withdrawQuantity the quantity of GAS to withdraw
     */
    public static void withdrawGas(Hash160 account, int withdrawQuantity) {
        validateOwner("withdrawGas");
        validateAccount(account, "withdrawGas");

        assert withdrawQuantity <= getGasReserves();

        boolean transferSuccess = transferGas(account, withdrawQuantity);
        assert transferSuccess;
        onWithdrawGas.fire(account, withdrawQuantity);
    }

    /**
     * Compound underlying bNEO reserves
     * This can only be called every COMPOUND_PERIOD
     *
     * @param account the address of the transaction signer
     */
    public static void compound(Hash160 account) {
        validateAccount(account, "compound");
        validateNonContract(account, "compound");
        validateCompoundTime();

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        Hash160 voterHash = getVoterScriptHash();
        FungibleToken bneoContract = new FungibleToken(bneoHash);
        NeoToken neoContract = new NeoToken();
        GasToken gasContract = new GasToken();

        // GAS reserves <= GAS balance always so comparing against this quantity
        // includes GAS accrued from NEO transfers in between calls of compound()
        int beforeBalance = getGasReserves();

        // Claim GAS from Voter
        boolean transferSuccess = neoContract.transfer(cneoHash, voterHash, 0, null);
        assert transferSuccess;

        // Send 0 bNEO to the bNEO contract to receive GAS
        transferSuccess = bneoContract.transfer(cneoHash, bneoHash, 0, null);
        assert transferSuccess;

        int afterBalance = (int) Contract.call(gasContract.getHash(), BALANCE_OF(), CallFlags.ReadOnly, new Object[]{cneoHash});
        int gasQuantity = afterBalance - beforeBalance;
        int treasuryCut = (gasQuantity * getFeePercent()) / PERCENT();
        int gasToSwap = gasQuantity - treasuryCut;

        // If gasToSwap is too large, it will be clipped and
        // the extra GAS will go to the treasury
        // However, the compound period will then be halved to ensure that
        // the GAS quantity will be smaller on the next compounding period
        int clippedGasToSwap = Math.min(gasToSwap, getMaxSwapGas());
        if (clippedGasToSwap < gasToSwap) {
            treasuryCut = gasQuantity - clippedGasToSwap;
            halveCompoundPeriod();
        }
        int bneoQuantity = clippedGasToSwap > 0 ? swapGasForBneo(clippedGasToSwap) : 0;

        // Reward the invoker for a job well done
        transferSuccess = transferGas(account, getGasReward());
        assert transferSuccess;

        onCompound.fire(account, gasQuantity, bneoQuantity, treasuryCut);
    }

    /**
     * Compound a portion of the contract's GAS reserves
     * into more bNEO reserves
     *
     * @param gasQuantity the quantity of GAS reserves to compound
     */
    public static void compoundReserves(int gasQuantity) {
        validateOwner("compoundReserves");
        validatePositiveNumber(gasQuantity, "gasQuantity");

        assert gasQuantity <= getGasReserves();
        assert gasQuantity <= getMaxSwapGas();

        int bneoQuantity = gasQuantity > 0 ? swapGasForBneo(gasQuantity) : 0;
        onCompoundReserves.fire(gasQuantity, bneoQuantity);
    }

    public static void convertToBneo(int neoQuantity) {
        validateOwner("convertToBneo");
        validatePositiveNumber(neoQuantity, "neoQuantity");

        convertToBneoInternal(neoQuantity);
        onConvertToBneo.fire(neoQuantity);
    }

    public static void convertToNeo(int neoQuantity) {
        validateOwner("convertToNeo");
        validatePositiveNumber(neoQuantity, "neoQuantity");

        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        Hash160 voterHash = getVoterScriptHash();
        GasToken gasContract = new GasToken();
        FungibleToken bneoContract = new FungibleToken(getBneoScriptHash());

        int bneoQuantity = neoQuantity * getBneoMultiplier();
        int gasQuantity = neoQuantity * GAS_FOR_NEO();
        int gasBalance = gasContract.balanceOf(cneoHash);
        assert gasQuantity <= gasBalance;

        boolean transferSuccess = gasContract.transfer(cneoHash, voterHash, gasQuantity, null);
        assert transferSuccess;

        transferSuccess = bneoContract.transfer(cneoHash, voterHash, bneoQuantity, null);
        assert transferSuccess;

        onConvertToNeo.fire(neoQuantity);
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) {
        Hash160 tokenHash = Runtime.getCallingScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 voterHash = getVoterScriptHash();
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
                abort("NEP17Transfer from mint but not bNEO or GAS", "onNEP17Payment");
            }
        }

        // Case 1: Called by NeoBurger contract
        else if (from.equals(bneoHash)) {
            // 1a) bNEO GAS claim - execution continues in compound
            if (tokenHash.equals(gasContract.getHash())) {
                return;
            }
            else {
                abort("NEP17Transfer from NeoBurger but not GAS", "onNEP17Payment");
            }
        }

        // Case 2: Called by the GAS-bNEO swap pair
        else if (from.equals(swapPairHash)) {
            // 2a) bNEO swap - execution continues in swapGasForBneo
            if (tokenHash.equals(bneoHash)) {
                return;
            }
            else {
                abort("NEP17Transfer from GAS-bNEO swap pair but not bNEO", "onNEP17Payment");
            }
        }

        // Case 3: Called by the Voter contract
        else if (from.equals(voterHash)) {
            // 3a) GAS claim - execution continues in compound
            if (tokenHash.equals(gasContract.getHash())) {
                return;
            }
            // 3b) bNEO redemption - execution continues in convertToBneo
            else if (tokenHash.equals(bneoHash)) {
                return;
            }
            else {
                abort("NEP17Transfer from bNEO agent but not NEO", "onNEP17Payment");
            }
        }

        // Case 4: non-null payload - must satisfy (data instanceof List) && (data[0] instanceof String)
        else if (data != null) {
            NEP17Payload params = (NEP17Payload) data;
            String action = params.action;

            // 4a) GAS top-up for continued operations
            if (tokenHash.equals(gasContract.getHash()) && ACTION_TOP_UP_GAS().equals(action)) {
                onTopUpGas.fire(from, amount);
                return;
            }
            else {
                abort("NEP17Transfer with non-null payload but unknown action", "onNEP17Payment");
            }
        }

        // Case 5: null payload, not from earlier cases
        else {
            // 5a) Handle incoming NEO: swap for bNEO and mint cNEO
            if (tokenHash.equals(neoContract.getHash())) {
                handleNeoDeposit(from, amount);
            }
            // 5b) Handle incoming bNEO: mint cNEO
            else if (tokenHash.equals(bneoHash)) {
                handleBneoDeposit(from, amount);
            }
            // 5c) Handle incoming cNEO: withdraw bNEO
            else if (tokenHash.equals(cneoHash)) {
                handleBneoWithdraw(from, amount);
            }
            else {
                abort("NEP17Transfer with null params must be one of NEO, bNEO, or cNEO", "onNEP17Payment");
            }
        }
    }

    // Helper Methods

    /**
     * This method swaps NEO reserves to bNEO reserves
     *
     * @param neoQuantity the quantity of NEO desired
     */
    private static void convertToBneoInternal(int neoQuantity) {
        CompoundingNeoVoterContract voterContract = new CompoundingNeoVoterContract(getVoterScriptHash());

        assert neoQuantity <= getNeoReserves();
        voterContract.withdrawBneo(neoQuantity);
    }

    /**
     * 1. Burn the cNEO offered by the user
     * 2. Send the corresponding quantity of bNEO back to the requesting user
     * 
     * @param account      the account that is withdrawing bNEO
     * @param cneoQuantity the quantity of cNEO to be burned
     */
    private static void handleBneoWithdraw(Hash160 account, int cneoQuantity) {
        validateHash160(account, "account");
        validateNonNegativeNumber(cneoQuantity, "cneoQuantity");

        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FungibleToken bneoContract = new FungibleToken(getBneoScriptHash());

        int bneoToCneoRatio = getReserveRatio();
        int computedBneoQuantity = (bneoToCneoRatio * cneoQuantity ) / FLOAT_MULTIPLIER();
        // bneoQuantity can be greater than bneoReserves due to float precision
        int bneoQuantity = Math.min(computedBneoQuantity, getTotalReserves());

        burn(cneoHash, cneoQuantity);

        // Convert NEO to bNEO if necessary
        int bneoReserves = getBneoReserves();
        if (bneoQuantity > bneoReserves) {
            int missingBneoQuantity = bneoQuantity - bneoReserves;
            int bneoMultiplier = getBneoMultiplier();
            int floorNeoQuantity = missingBneoQuantity / bneoMultiplier;
            int neoQuantity = missingBneoQuantity % bneoMultiplier == 0 ? floorNeoQuantity : floorNeoQuantity + 1;

            convertToBneoInternal(neoQuantity);
        }

        boolean transferSuccess = bneoContract.transfer(Runtime.getExecutingScriptHash(), account, bneoQuantity, null);
        assert transferSuccess;
    }

    /**
     * 1. Convert the incoming NEO quantity into bNEO
     * 2. Mint and transfer the corresponding quantity of cNEO to the account
     * 
     * @param account     the account that is depositing NEO
     * @param neoQuantity the quantity of NEO to be converted into bNEO and locked
     */
    private static void handleNeoDeposit(Hash160 account, int neoQuantity) {
        validateHash160(account, "account");
        validateNonNegativeNumber(neoQuantity, "neoQuantity");

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        NeoToken neoContract = new NeoToken();
        String balanceOf = BALANCE_OF();

        // Swap incoming NEO for bNEO
        int beforeBalance = (int) Contract.call(bneoHash, balanceOf, CallFlags.ReadOnly, new Object[]{cneoHash});

        boolean transferSuccess = neoContract.transfer(cneoHash, bneoHash, neoQuantity, null);
        assert transferSuccess;
        
        int afterBalance = (int) Contract.call(bneoHash, balanceOf, CallFlags.ReadOnly, new Object[]{cneoHash});
        int bneoQuantity = afterBalance - beforeBalance;

        mintCneoFromBneo(bneoQuantity, account);
    }

    /**
     * Mint and transfer the corresponding quantity of cNEO to the account
     * 
     * @param account      the account that is depositing bNEO
     * @param bneoQuantity the quantity of bNEO to be locked
     */
    private static void handleBneoDeposit(Hash160 account, int bneoQuantity) {
        validateHash160(account, "account");
        validateNonNegativeNumber(bneoQuantity, "bneoQuantity");

        mintCneoFromBneo(bneoQuantity, account);
    }

    private static void setLastCompounded(int lastCompounded) {
        validatePositiveNumber(lastCompounded, "lastCompounded");

        Storage.put(CTX(), LAST_COMPOUNDED_KEY(), lastCompounded);
    }

    private static boolean transferGas(Hash160 to, int quantity) {
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        GasToken gasContract = new GasToken();
        return gasContract.transfer(cneoHash, to, quantity, null);
    }

    /**
     * Mint cNEO corresponding to incoming bNEO
     *
     * @param bneoQuantity the quantity of bNEO to be locked
     * @param account      the account to which the cNEO is to be minted
     */
    private static void mintCneoFromBneo(int bneoQuantity, Hash160 account) {
        // We have to use the *old* reserve ratio,
        // before the bneoQuantity has been received by the contract
        int prevBneoReserves = getBneoReserves() - bneoQuantity;
        assert prevBneoReserves >= 0;

        int neoReserves = getNeoReserves();
        int cneoSupply = totalSupply();
        int reserveRatio = getReserveRatioInternal(prevBneoReserves, neoReserves, cneoSupply);

        // As total reserves increase, the quantity of cNEO minted per bNEO decreases
        int cneoQuantity = (FLOAT_MULTIPLIER() * bneoQuantity) / reserveRatio;

        int newSupply = totalSupply() + cneoQuantity;
        int maxSupply = getMaxSupply();
        assert newSupply <= maxSupply;

        mint(account, cneoQuantity);
    }

    /**
     * Swap the incoming quantity of GAS to bNEO on Flamingo
     *
     * @param gasQuantity the quantity of incoming GAS
     * @return the quantity of bNEO added to the reserves
     */
    private static int swapGasForBneo(int gasQuantity) {
        validateNonNegativeNumber(gasQuantity, "gasQuantity");

        // Protect against attackers swapping too much GAS at once
        assert gasQuantity <= getMaxSwapGas();

        GasToken gasContract = new GasToken();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = Runtime.getExecutingScriptHash();
        FlamingoSwapRouterContract swapRouterContract = new FlamingoSwapRouterContract(getSwapRouterScriptHash());
        String balanceOf = BALANCE_OF();

        int beforeBalance = (int) Contract.call(bneoHash, balanceOf, CallFlags.ReadOnly, new Object[]{cneoHash});
        Hash160[] paths = new Hash160[]{ gasContract.getHash(), bneoHash };
        int minBneoIn = computeMinBneoIn(gasQuantity);
        int deadline = Runtime.getTime() + 30;
        addToApprovedSwapQuantity(gasQuantity);
        boolean swapSuccess = swapRouterContract.swapTokenInForTokenOut(gasQuantity, minBneoIn, paths, deadline);
        assert swapSuccess;

        int afterBalance = (int) Contract.call(bneoHash, balanceOf, CallFlags.ReadOnly, new Object[]{cneoHash});
        int bneoQuantity = afterBalance - beforeBalance;

        return bneoQuantity;
    }

    public static int computeMinBneoIn(int gasQuantity) {
        int gasIndex = getSwapPairGasIndex();
        FlamingoSwapPairContract swapPairContract = new FlamingoSwapPairContract(getSwapPairScriptHash());

        List<Integer> reserves = swapPairContract.getReserves();
        int gasReserves = reserves.get(gasIndex);
        int bneoReserves = reserves.get(1 - gasIndex);
        int bneoQuantity = (gasQuantity * bneoReserves) / gasReserves;
        int percent = PERCENT();

        return (bneoQuantity * (percent - getMaxSlippage())) / percent;
    }

    private static void halveCompoundPeriod() {
        int newCompoundPeriod = getCompoundPeriod() / 2;
        Storage.put(CTX(), COMPOUND_PERIOD_KEY(), newCompoundPeriod);
        onSetCompoundPeriod.fire(newCompoundPeriod);
    }

    private static void mint(Hash160 account, int mintQuantity) {
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
    
    private static void burn(Hash160 account, int burnQuantity) {
        validateHash160(account, "account");
        validateNonNegativeNumber(burnQuantity, "burnQuantity");

        final int supply = totalSupply();
        final int accountBalance = getBalance(account);
        assert burnQuantity <= supply;
        assert burnQuantity <= accountBalance;

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

    private static void abort(String msg, String method) {
        // Keeping this method signature in case notifications are enabled for aborts later.
        Helper.abort();
    }

    private static void addToSupply(int value) {
        Storage.put(CTX(), SUPPLY_KEY(), totalSupply() + value);
    }

    private static void deductFromSupply(int value) {
        addToSupply(-value);
    }

    private static void addToBalance(Hash160 key, int value) {
        BALANCE_MAP().put(key.toByteArray(), getBalance(key) + value);
    }

    private static void deductFromBalance(Hash160 key, int value) {
        addToBalance(key, -value);
    }

    private static int getApprovedSwapQuantity() {
        return Storage.getIntOrZero(RTX(), APPROVED_SWAP_QUANTITY_KEY());
    }

    private static void addToApprovedSwapQuantity(int value) {
        Storage.put(CTX(), APPROVED_SWAP_QUANTITY_KEY(), getApprovedSwapQuantity() + value);
    }

    private static void deductFromApprovedSwapQuantity(int value) {
        addToApprovedSwapQuantity(-value);
    }

    private static int getBalance(Hash160 key) {
        return BALANCE_MAP().getIntOrZero(key.toByteArray());
    }

    private static int getBneoMultiplier() {
        Hash160 bneoHash = getBneoScriptHash();
        int bneoDecimals = (int) Contract.call(bneoHash, "decimals", CallFlags.ReadOnly, new Object[]{});
        return Helper.pow(10, bneoDecimals);
    }

    private static void validateCompoundTime() {
        int curTime = Runtime.getTime();
        int nextCompound = getLastCompounded() + getCompoundPeriod();
        assert curTime >= nextCompound;

        setLastCompounded(curTime);
    }

    private static int getReserveRatioInternal(int bneoReserves, int neoReserves, int cneoSupply) {
        int totalReserves = getTotalReservesInternal(bneoReserves, neoReserves);
        int floatMultiplier = FLOAT_MULTIPLIER();

        // Special case when cNEO supply is 0
        if (cneoSupply == 0) {
            return floatMultiplier;
        }

        // Otherwise, this is a valid number
        return (floatMultiplier * totalReserves) / cneoSupply;
    }

    private static int getTotalReservesInternal(int bneoReserves, int neoReserves) {
        return bneoReserves + (neoReserves * getBneoMultiplier());
    }
    
    private static void validateHash160(Hash160 hash, String hashName) {
        // Keeping this here so we can use it later if asserts later support messages
        // String message = "The parameter '" + hashName + "' must be a 20-byte address";
        assert Hash160.isValid(hash) && !hash.isZero();
    }

    private static void validateNonContract(Hash160 hash, String hashName) {
        // Keeping this here so we can use it later if asserts later support messages
        // String message = "The parameter '" + hashName + "' must be a contract hash";
        validateHash160(hash, hashName);
        assert (new ContractManagement()).getContract(hash) == null;
    }

    private static void validateContract(Hash160 hash, String hashName) {
        // Keeping this here so we can use it later if asserts later support messages
        // String message = "The parameter '" + hashName + "' must be a contract hash";
        validateHash160(hash, hashName);
        assert (new ContractManagement()).getContract(hash) != null;
    }

    private static void validatePositiveNumber(int number, String numberName) {
        // Keeping this here so we can use it later if asserts later support messages
        // StdLib stdLib = new StdLib();
        // String message = "The parameter '" + numberName + "'=" + stdLib.itoa(number, 10) + " must be positive";
        assert number > 0;
    }

    private static void validateNonNegativeNumber(int number, String numberName) {
        // Keeping this here so we can use it later if asserts later support messages
        // StdLib stdLib = new StdLib();
        // String message = "The parameter '" + numberName + "'=" + stdLib.itoa(number, 10) + " must be non-negative";
        assert number >= 0;
    }

    private static void validateOwner(String method) {
        if (!Runtime.checkWitness(getOwner())) {
            abort("Not authorized", method);
        }
    }

    private static void validateAccount(Hash160 account, String method) {
        if (!Runtime.checkWitness(account)) {
            abort("Invalid witness", method);
        }
    }
}
