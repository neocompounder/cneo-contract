package com.neocompounder.cneo;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.Transaction;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.events.Event1Arg;

@DisplayName("CompoundingNeoVoter")
@ManifestExtra(key = "Author", value = "Lyrebird Finance")
@ManifestExtra(key = "Email", value = "hello@lyrebird.finance")
@ManifestExtra(key = "Description", value = "cNEO Voting Agent")
@ContractSourceCode("https://github.com/neocompounder/cneo-contract/blob/main/src/main/java/com/neocompounder/cneo/CompoundingNeoVoter.java")
@Permission(contract = "*", methods = "*")
public class CompoundingNeoVoter {
    private static StorageContext CTX() { return Storage.getStorageContext(); }
    private static StorageContext RTX() { return Storage.getReadOnlyContext(); }

    // To redeem bNEO to NEO, we need to send 100000 GAS per 1 NEO
    private static final int GAS_FOR_NEO() { return 100000; }

    // Keys
    private static final byte[] OWNER_KEY() { return new byte[]{0x00}; }
    private static final byte[] BNEO_HASH_KEY() { return new byte[]{0x01}; }
    private static final byte[] CNEO_HASH_KEY() { return new byte[]{0x02}; }

    // Hex strings
    private static final ByteString VOTE() { return new ByteString("vote"); }

    // Events
    @DisplayName("SetOwner")
    private static Event1Arg<Hash160> onSetOwner;
    
    // Lifecycle Methods
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            final Hash160 owner = (Hash160) data;
            validateHash160(owner, "owner");

            // Initialize the supply
            StorageContext ctx = CTX();
            Storage.put(ctx, OWNER_KEY(), owner);
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
    public static boolean verify() {
        if (Runtime.checkWitness(getOwner())) {
            Transaction tx = (Transaction) Runtime.getScriptContainer();
            ByteString script = tx.script;
            return script.length() == 71 && script.range(40, 4).equals(VOTE());
        }
        return false;
    }

    // Contract Methods
    public static void setOwner(Hash160 owner) throws Exception {
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

    public static void setBneoScriptHash(Hash160 bneoHash) {
        validateOwner("setBneoScriptHash");
        validateContract(bneoHash, "bneoHash");

        Storage.put(CTX(), BNEO_HASH_KEY(), bneoHash);
    }

    @Safe
    public static Hash160 getBneoScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), BNEO_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void setCneoScriptHash(Hash160 cneoHash) {
        validateOwner("setCneoScriptHash");
        validateContract(cneoHash, "cneoHash");

        Storage.put(CTX(), CNEO_HASH_KEY(), cneoHash);
    }

    @Safe
    public static Hash160 getCneoScriptHash() {
        final Hash160 storageVal = Storage.getHash160(RTX(), CNEO_HASH_KEY());
        return storageVal == null ? Hash160.zero() : storageVal;
    }

    public static void vote(ECPoint candidate) {
        validateOwner("vote");
        validateECPoint(candidate, "vote");

        NeoToken neoContract = new NeoToken();
        Hash160 voterHash = Runtime.getExecutingScriptHash();
        neoContract.vote(voterHash, candidate);
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) {
        Hash160 voterHash = Runtime.getExecutingScriptHash();
        Hash160 tokenHash = Runtime.getCallingScriptHash();
        Hash160 bneoHash = getBneoScriptHash();
        Hash160 cneoHash = getCneoScriptHash();
        NeoToken neoContract = new NeoToken();
        GasToken gasContract = new GasToken();

        if (from != null && from.equals(cneoHash)) {
            // Case 0: cNEO GAS claim - send all GAS to cNEO
            if (tokenHash.equals(neoContract.getHash())) {
                int gasBalance = gasContract.balanceOf(voterHash);
                gasContract.transfer(voterHash, cneoHash, gasBalance, null);
            }
            // Case 1: cNEO convertToNeo - convert incoming bNEO to NEO
            else if (tokenHash.equals(bneoHash)) {
                int bneoQuantity = amount;
                int neoQuantity = bneoQuantity / getBneoMultiplier();

                // Ensure we have enough GAS for the bNEO -> NEO conversion
                int gasQuantity = neoQuantity * GAS_FOR_NEO();
                int gasBalance = gasContract.balanceOf(voterHash);
                assert gasQuantity <= gasBalance;

                // Send the GAS fee for the bNEO -> NEO conversion
                int beforeBalance = neoContract.balanceOf(voterHash);
                boolean transferSuccess = gasContract.transfer(voterHash, bneoHash, gasQuantity, null);
                assert transferSuccess;

                int afterBalance = neoContract.balanceOf(voterHash);
                int actualNeoQuantity = afterBalance - beforeBalance;
                assert actualNeoQuantity == neoQuantity;
            }
        }
    }

    /**
     * cNEO convertToBneo - convert bNEO to NEO and withdraw to cNEO
     * 
     * @param neoQuantity the quantity of NEO to convert to bNEO and withdraw
     */
    public static void withdrawBneo(int neoQuantity) {
        Hash160 cneoHash = getCneoScriptHash();
        validateAccount(cneoHash, "withdrawBneo");

        Hash160 bneoHash = getBneoScriptHash();
        Hash160 voterHash = Runtime.getExecutingScriptHash();
        NeoToken neoContract = new NeoToken();
        FungibleToken bneoContract = new FungibleToken(bneoHash);
        int neoReserves = neoContract.balanceOf(voterHash);
        int bneoQuantity = neoQuantity * getBneoMultiplier();

        assert neoQuantity <= neoReserves;

        int beforeBalance = bneoContract.balanceOf(voterHash);
        boolean transferSuccess = neoContract.transfer(voterHash, bneoHash, neoQuantity, null);
        assert transferSuccess;

        int afterBalance = bneoContract.balanceOf(voterHash);
        int actualBneoQuantity = afterBalance - beforeBalance;
        assert bneoQuantity == actualBneoQuantity;

        bneoContract.transfer(voterHash, cneoHash, bneoQuantity, null);
    }

    private static int getBneoMultiplier() {
        Hash160 bneoHash = getBneoScriptHash();
        int bneoDecimals = (int) Contract.call(bneoHash, "decimals", CallFlags.ReadOnly, new Object[]{});
        return Helper.pow(10, bneoDecimals);
    }

    private static void abort(String msg, String method) {
        // Keeping this method signature in case notifications are enabled for aborts later.
        Helper.abort();
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

    private static void validateECPoint(ECPoint ecPoint, String hashName) {
        // Keeping this here so we can use it later if asserts later support messages
        // String message = "The parameter '" + hashName + "' must be a 33-byte address";
        assert ECPoint.isValid(ecPoint);
    }

    private static void validateHash160(Hash160 hash, String hashName) {
        // Keeping this here so we can use it later if asserts later support messages
        // String message = "The parameter '" + hashName + "' must be a 20-byte address";
        assert Hash160.isValid(hash) && !hash.isZero();
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
