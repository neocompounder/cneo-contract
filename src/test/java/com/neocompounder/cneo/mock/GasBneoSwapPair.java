package com.neocompounder.cneo.mock;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.events.Event2Args;

@DisplayName("GasBneoSwapPair")
@Permission(contract = "*", methods = "*")
public class GasBneoSwapPair {

    private static StorageContext ctx = Storage.getStorageContext();

    // Keys
    private static byte[] OWNER_KEY = new byte[]{0x00};
    private static byte[] TOKEN0_KEY = new byte[]{0x01};
    private static byte[] TOKEN1_KEY = new byte[]{0x02};

    // Events
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

            Storage.put(ctx, OWNER_KEY, owner);
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

    public static void setToken0(Hash160 token0) throws Exception {
        validateOwner("setToken0");
        validateHash160(token0, "token0");

        Storage.put(ctx, TOKEN0_KEY, token0);
    }

    @Safe
    public static Hash160 getToken0() {
        return Storage.getHash160(ctx, TOKEN0_KEY);
    }

    public static void setToken1(Hash160 token1) throws Exception {
        validateOwner("setToken1");
        validateHash160(token1, "token1");

        Storage.put(ctx, TOKEN1_KEY, token1);
    }

    @Safe
    public static Hash160 getToken1() {
        return Storage.getHash160(ctx, TOKEN1_KEY);
    }

    // Since this is a mock, we only support one way swaps
    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        Hash160 inToken = Runtime.getCallingScriptHash();
        Hash160 pairHash = Runtime.getExecutingScriptHash();

        if (inToken.equals(getToken0())) {
            FungibleToken outToken = new FungibleToken(getToken1());
            int outQuantity = outToken.balanceOf(pairHash);
            outToken.transfer(pairHash, from, outQuantity, null);
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

    private static void validateOwner(String method) {
        if (!Runtime.checkWitness(getOwner())) {
            fireErrorAndAbort("Not authorized", method);
        }
    }
}
