package com.neocompounder.cneo.interfaces;

import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.Hash160;

public class FlamingoSwapFactoryContract extends ContractInterface {
    public FlamingoSwapFactoryContract(String contractHash) {
        super(contractHash);
    }

    public FlamingoSwapFactoryContract(Hash160 contractHash) {
        super(contractHash);
    }

    public native Hash160 getExchangePair(Hash160 tokenA, Hash160 tokenB);
}
