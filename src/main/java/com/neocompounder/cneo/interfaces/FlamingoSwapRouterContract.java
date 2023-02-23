package com.neocompounder.cneo.interfaces;

import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.Hash160;

public class FlamingoSwapRouterContract extends ContractInterface {
    public FlamingoSwapRouterContract(String contractHash) {
        super(contractHash);
    }

    public FlamingoSwapRouterContract(Hash160 contractHash) {
        super(contractHash);
    }

    public native boolean swapTokenInForTokenOut(int amountIn, int amountOutMin, Hash160[] paths, int deadLine);
}
