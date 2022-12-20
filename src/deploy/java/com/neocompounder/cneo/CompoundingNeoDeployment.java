package com.neocompounder.cneo;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractUtils;
import io.neow3j.contract.NefFile;
import io.neow3j.protocol.core.response.ContractManifest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CompoundingNeoDeployment {
    public static void main(String[] args) throws Throwable {
        Map<String, String> substitutions = new HashMap<>();

        deployCompoundingNeo(substitutions);
    }

    private static void deployCompoundingNeo(Map<String, String> substitutions) throws Throwable {
        CompilationUnit result = new Compiler().compile(CompoundingNeo.class.getCanonicalName(), substitutions);

        String contractName = result.getManifest().getName();
        if (contractName == null || contractName.length() == 0) {
            throw new IllegalStateException("No contract name is set in the contract's manifest.");
        }

        Path buildRelativePath = Paths.get("", "build", "neow3j");
        Path buildAbsolutePath = buildRelativePath.toAbsolutePath();

        NefFile nef = result.getNefFile();
        ContractUtils.writeNefFile(nef, contractName, buildAbsolutePath);
        ContractManifest manifest = result.getManifest();
        ContractUtils.writeContractManifestFile(manifest, buildAbsolutePath);
    }
}
