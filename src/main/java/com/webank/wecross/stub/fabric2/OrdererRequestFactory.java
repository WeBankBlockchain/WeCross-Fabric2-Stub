package com.webank.wecross.stub.fabric2;

import com.google.protobuf.ByteString;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.fabric2.account.FabricAccount;
import com.webank.wecross.stub.fabric2.common.FabricType;
import org.hyperledger.fabric.protos.common.Common;

public class OrdererRequestFactory {
    public static Request build(Account account, byte[] ordererPayloadToSign) throws Exception {
        if (!account.getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception("Illegal account type for fabric call: " + account.getType());
        }

        FabricAccount fabricAccount = (FabricAccount) account;

        byte[] sign = fabricAccount.sign(ordererPayloadToSign);

        Common.Envelope envelope =
                Common.Envelope.newBuilder()
                        .setPayload(ByteString.copyFrom(ordererPayloadToSign))
                        .setSignature(ByteString.copyFrom(sign))
                        .build();
        Request request = new Request();
        request.setData(envelope.toByteArray());
        return request;
    }
}
