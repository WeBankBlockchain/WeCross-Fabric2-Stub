package com.webank.wecross.stub.fabric.FabricCustomCommand;

/**
 * All rights Reserved, Designed By www.webank.com
 *
 * @version V1.0 @Title: ChaincodeRequest.java @Package
 *     com.webank.wecross.stub.fabric.FabricCustomCommand @Description: TODO(用一句话描述该文件做什么)
 * @author: mirsu
 * @date: 2020/11/3 11:09 @Copyright: 2020-2020/11/3 www.tbs.com Inc. All rights reserved.
 *     <p>注意：本内容仅限于TBS项目组内部传阅，禁止外泄以及用于其他的商业目的
 */
public interface ChaincodeRequest {
    void check() throws Exception;

    byte[] toBytes() throws Exception;

    org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType() throws Exception;
}
