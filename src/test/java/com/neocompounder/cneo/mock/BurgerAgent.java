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

@DisplayName("BurgerAgent")
@Permission(contract = "*", methods = "*")
public class BurgerAgent {

    private static StorageContext ctx = Storage.getStorageContext();

    // Keys
    private static byte[] OWNER_KEY = new byte[]{0x00};

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

    // Since this is a mock, we only support one way swaps
    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        FungibleToken token = new FungibleToken(Runtime.getCallingScriptHash());
        Hash160 to = (Hash160) data;
        token.transfer(Runtime.getExecutingScriptHash(), to, amount, null);
    }

    public static boolean swapTokenInForTokenOut(Hash160 sender, int amountIn, int amountOutMin, Hash160[] paths, int deadLine) throws Exception {
        Hash160 routerHash = Runtime.getExecutingScriptHash();

        FungibleToken inToken = new FungibleToken(paths[0]);
        FungibleToken outToken = new FungibleToken(paths[1]);
        if (!inToken.transfer(sender, routerHash, amountIn, null)) {
            throw new Exception("Failed to transfer inToken");
        }
        int amountOut = outToken.balanceOf(routerHash);
        if (!outToken.transfer(routerHash, sender, amountOut, null)) {
            throw new Exception("Failed to transfer outToken");
        }

        return true;
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
