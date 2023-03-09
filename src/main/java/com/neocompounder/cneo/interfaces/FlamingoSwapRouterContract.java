package com.neocompounder.cneo.interfaces;

import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.List;

public class FlamingoSwapRouterContract extends ContractInterface {
    public FlamingoSwapRouterContract(String contractHash) {
        super(contractHash);
    }

    public FlamingoSwapRouterContract(Hash160 contractHash) {
        super(contractHash);
    }

    public native int getAmountOut(int amountIn, int reserveIn, int reserveOut);
    public native List<Integer> getReserves(Hash160 tokenA, Hash160 tokenB);
    public native boolean swapTokenInForTokenOut(int amountIn, int amountOutMin, Hash160[] paths, int deadLine);
}
