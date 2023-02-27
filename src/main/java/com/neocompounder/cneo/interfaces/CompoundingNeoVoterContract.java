package com.neocompounder.cneo.interfaces;

import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.Hash160;

public class CompoundingNeoVoterContract extends ContractInterface {
    public CompoundingNeoVoterContract(String contractHash) {
        super(contractHash);
    }

    public CompoundingNeoVoterContract(Hash160 contractHash) {
        super(contractHash);
    }

    public native boolean withdrawBneo(int neoQuantity);
}
