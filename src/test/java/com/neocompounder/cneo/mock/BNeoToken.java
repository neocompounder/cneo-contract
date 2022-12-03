package com.neocompounder.cneo.mock;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;

// Change these for each token; substitution not possible
@DisplayName("bNEO")
@SupportedStandard(neoStandard = NeoStandard.NEP_17)
@Permission(contract = "*", methods = "*")
public class BNeoToken {

    private static StorageContext ctx = Storage.getStorageContext();

    private static int INITIAL_SUPPLY = StringLiteralHelper.stringToInt("100000000000000");
    private static int DECIMALS = 8;
    private static String SYMBOL = "${symbol}";

    // Keys
    private static byte[] OWNER_KEY = new byte[]{0x00};
    private static byte[] SUPPLY_KEY = new byte[]{0x01};

    private static StorageMap BALANCE_MAP = new StorageMap(Storage.getStorageContext(), new byte[]{0x10});

    // Events
    @DisplayName("Transfer")
    private static Event3Args<Hash160, Hash160, Integer> onTransfer;

    /**
     * This event is intended to be fired before aborting the VM. The first argument should be a message and the
     * second argument should be the method name whithin which it has been fired.
     */
    @DisplayName("Error")
    private static Event2Args<String, String> onError;
    
    // Lifecycle Methods
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            final Hash160 owner = (Hash160) data;

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
    public static boolean verify() {
        return Runtime.checkWitness(getOwner());
    }


    // Contract Methods
    public static void setOwner(Hash160 owner) throws Exception {
        validateOwner("setOwner");
        validateHash160(owner, "owner");

        Storage.put(ctx, OWNER_KEY, owner);
    }

    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(ctx, OWNER_KEY);
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
        return Storage.getIntOrZero(ctx, SUPPLY_KEY);
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

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        Hash160 token = Runtime.getCallingScriptHash();
        Hash160 bneoHash = Runtime.getExecutingScriptHash();
        FungibleToken bneoContract = new FungibleToken(bneoHash);

        GasToken gasContract = new GasToken();
        NeoToken neoContract = new NeoToken();

        if (from == null || from.equals(bneoHash)) {
            return;
        }

        // If someone sends bNEO, send all GAS
        if (token.equals(bneoHash)) {
            int gasBalance = gasContract.balanceOf(bneoHash);
            gasContract.transfer(bneoHash, from, gasBalance, null);
        }

        // If someone sends NEO, mint and send bNEO
        else if (token.equals(neoContract.getHash())) {
            int mintQuantity = amount * 100000000;
            mint(bneoHash, mintQuantity);
            bneoContract.transfer(bneoHash, from, mintQuantity, null);
        }
        // If someone sends GAS, burn bNEO and send NEO
        else if (token.equals(gasContract.getHash())) {
            int neoQuantity = amount / 100000;
            int bneoQuantity = neoQuantity * 100000000;
            burn(from, bneoQuantity);
            neoContract.transfer(bneoHash, from, neoQuantity, null);
        }
    }

    
    // Helper Methods
    private static void mint(Hash160 account, int mintQuantity) throws Exception {
        validateHash160(account, "account");
        validateNonNegativeNumber(mintQuantity, "mintQuantity");

        if (mintQuantity != 0) {
            addToSupply(mintQuantity);
            addToBalance(account, mintQuantity);
    
            onTransfer.fire(null, account, mintQuantity);
            postTransfer(null, account, mintQuantity, null);
        }
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

    private static void validateHash160(Hash160 hash, String hashName) throws Exception {
        if (!Hash160.isValid(hash)) {
            throw new Exception("The parameter '" + hashName + "' must be a 20-byte address");
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

    private static int getBalance(Hash160 key) {
        return BALANCE_MAP.getIntOrZero(key.toByteArray());
    }
}
