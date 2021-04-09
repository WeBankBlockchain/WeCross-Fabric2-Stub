package com.webank.wecross.stub.fabric.FabricCustomCommand;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.common.FabricType;
import java.io.IOException;

/**
 * All rights Reserved, Designed By www.webank.com
 *
 * @version V1.0 @Title: PackageChaincodeRequest.java @Package
 *     com.webank.wecross.stub.fabric.FabricCustomCommand @Description: 链码打包请求体
 * @author: mirsu
 * @date: 2020/11/3 10:58 @Copyright: 2020-2020/11/3 www.tbs.com Inc. All rights reserved.
 *     <p>peer lifecycle chaincode package ./channel-artifacts/basic_02.tar.gz --path
 *     /opt/gopath/src/github.com/chaincode/public-go/go/ --label basic_02
 *     <p>chaincodeLabel 链码标签，如：mycc_1.0，一般 ${ccName}_${ccVersion} chaincodeSourcePath
 *     链码资源路径，如："/Workspace/java/code.aliyz.com/fabric/gocc/mycc" chaincodeMetainfoPath
 *     链码元数据路径，就是链码相关的 "/META-INF" 文件夹路径 chaincodePath 链码路径，如："github.com" chaincodeName
 *     链码名称，如："mycc" chaincodeType 链码开发语言类型，枚举 注意：本内容仅限于TBS项目组内部传阅，禁止外泄以及用于其他的商业目的
 */
public class PackageChaincodeRequest implements ChaincodeRequest {

    private String chaincodeLabel;
    private String chaincodeName;
    private String chaincodeMetainfoPath;
    private String chaincodeSourcePath;
    private String chaincodePath;
    private String chaincodeType;

    private static ObjectMapper objectMapper = new ObjectMapper();

    public PackageChaincodeRequest() {}

    public static PackageChaincodeRequest build() {
        PackageChaincodeRequest defaultProposal = new PackageChaincodeRequest();
        return defaultProposal.setChaincodeLabel(defaultProposal.getChaincodeName());
    }

    public PackageChaincodeRequest setChaincodeLabel(String chaincodeLabel) {
        this.chaincodeLabel = chaincodeLabel;
        return this;
    }

    public PackageChaincodeRequest setChaincodeName(String chaincodeName) {
        this.chaincodeName = chaincodeName;
        return this;
    }

    public PackageChaincodeRequest setChaincodePath(String chaincodePath) {
        this.chaincodePath = chaincodePath;
        return this;
    }

    public PackageChaincodeRequest setChaincodeType(String chaincodeType) {
        this.chaincodeType = chaincodeType;
        return this;
    }

    public PackageChaincodeRequest setChaincodeMetainfoPath(String chaincodeMetainfoPath) {
        this.chaincodeMetainfoPath = chaincodeMetainfoPath;
        return this;
    }

    public PackageChaincodeRequest setChaincodeSourcePath(String chaincodeSourcePath) {
        this.chaincodeSourcePath = chaincodeSourcePath;
        return this;
    }

    public String getChaincodeMetainfoPath() {
        return chaincodeMetainfoPath;
    }

    public String getChaincodeSourcePath() {
        return chaincodeSourcePath;
    }

    public String getChaincodeLabel() {
        return chaincodeLabel;
    }

    public String getChaincodeName() {
        return chaincodeName;
    }

    public String getChaincodePath() {
        return chaincodePath;
    }

    public String getChaincodeType() {
        return chaincodeType;
    }

    @Override
    @JsonIgnore
    public byte[] toBytes() throws Exception {
        if (getChaincodeLabel() == null) {
            throw new Exception("ChaincodeLabel is null");
        }

        if (getChaincodeType() == null) {
            throw new Exception("ChaincodeType is null");
        }

        if (getChaincodeName() == null) {
            throw new Exception("ChaincodeName is null");
        }

        if (getChaincodePath() == null) {
            throw new Exception("ChaincodePath is null");
        }
        if (getChaincodeMetainfoPath() == null) {
            throw new Exception("ChaincodeMetainfoPath is null");
        }
        if (getChaincodeSourcePath() == null) {
            throw new Exception("ChaincodeSourcePath is null");
        }
        return objectMapper.writeValueAsBytes(this);
    }

    @JsonIgnore
    public static PackageChaincodeRequest parseFrom(byte[] bytes)
            throws IOException, JsonParseException, JsonMappingException {
        return objectMapper.readValue(bytes, PackageChaincodeRequest.class);
    }

    @Override
    @JsonIgnore
    public org.hyperledger.fabric.sdk.TransactionRequest.Type getChaincodeLanguageType()
            throws Exception {
        return FabricType.stringTochainCodeType(getChaincodeType());
    }

    @Override
    public void check() throws Exception {
        if (this.chaincodeLabel == null) {
            throw new Exception("chaincodeLabel not set");
        }

        if (this.chaincodeName == null) {
            throw new Exception("chaincodeName not set");
        }

        if (this.chaincodePath == null) {
            throw new Exception("chaincodePath not set");
        }

        if (this.chaincodeType == null) {
            throw new Exception("chaincodeType not set");
        }
        if (this.chaincodeMetainfoPath == null) {
            throw new Exception("chaincodeMetainfoPath not set");
        }
        if (this.chaincodeSourcePath == null) {
            throw new Exception("chaincodeSourcePath not set");
        }
    }
}
