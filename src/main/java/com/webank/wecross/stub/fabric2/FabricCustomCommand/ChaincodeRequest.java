package com.webank.wecross.stub.fabric2.FabricCustomCommand;

public interface ChaincodeRequest {
    void check() throws Exception;

    byte[] toBytes() throws Exception;

    org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType() throws Exception;
}
