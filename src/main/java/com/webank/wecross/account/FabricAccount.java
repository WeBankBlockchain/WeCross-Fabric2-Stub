package com.webank.wecross.account;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.identity.IdentityFactory;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class FabricAccount implements Account {

    private User user;
    private SigningIdentity signer;

    public FabricAccount(User user) throws Exception {
        this.setUser(user);

        // ECDSA secp256r1
        this.signer =
                IdentityFactory.getSigningIdentity(CryptoSuite.Factory.getCryptoSuite(), user);
    }

    public byte[] sign(byte[] message) throws Exception {
        return signer.sign(message);
    }

    // Only in fabric stub
    public boolean verifySign(byte[] message, byte[] sig)
            throws CryptoException, InvalidArgumentException {
        return true;
    }

    @Override
    public String getName() {
        return user.getName();
    }

    @Override
    public String getType() {
        return FabricType.Account.FABRIC_ACCOUNT;
    }

    @Override
    public String getIdentity() {
        return signer.createSerializedIdentity().toByteString().toStringUtf8();
    }

    @Override
    public int getKeyID() {
        return 0;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }
}
