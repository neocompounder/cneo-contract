package com.neocompounder.cneo.interfaces;

import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.List;

public class FlamingoSwapPairContract extends ContractInterface {
    public FlamingoSwapPairContract(String contractHash) {
        super(contractHash);
    }

    public FlamingoSwapPairContract(Hash160 contractHash) {
        super(contractHash);
    }

    public native Hash160 getToken0();
    public native Hash160 getToken1();
    public native List<Integer> getReserves();
}
