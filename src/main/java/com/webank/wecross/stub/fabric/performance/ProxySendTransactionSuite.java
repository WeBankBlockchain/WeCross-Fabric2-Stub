package com.webank.wecross.stub.fabric.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric.EndorsementPolicyAnalyzer;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import com.webank.wecross.utils.FabricUtils;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;

/** @Description: 代理合约交易套件 @Author: mirsu @Date: 2020/10/30 10:27 */
public class ProxySendTransactionSuite implements PerformanceSuite {
    private final String proxyName = StubConstant.PROXY_NAME;
    static final int BOUND = Integer.MAX_VALUE - 1;

    private static ObjectMapper objectMapper = new ObjectMapper();
    SecureRandom rand = new SecureRandom();

    private Channel channel;
    private HFClient hfClient;
    private Collection<Peer> endorsers;

    public ProxySendTransactionSuite(String chainPath) throws Exception {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        FabricConnection fabricConnection =
                (FabricConnection) fabricStubFactory.newConnection(chainPath);

        this.channel = fabricConnection.getChannel();

        if (!fabricConnection.getChaincodeMap().containsKey(proxyName)) {
            throw new Exception("WeCross proxy chaincode has not been deployed");
        }

        if (!fabricConnection.getChaincodeMap().containsKey("chaincode/sacc")) {
            throw new Exception("Resource sacc has not been deployed");
        }

        this.hfClient = fabricConnection.getHfClient();

        this.endorsers = fabricConnection.getChaincodeMap().get("chaincode/sacc").getEndorsers();

        sendTransactionOnceByProxy(null);
    }

    @Override
    public String getName() {
        return "Proxy SendTransaction Performance Test Suite";
    }

    @Override
    public void call(PerformanceSuiteCallback callback) {
        try {
            sendTransactionOnceByProxy(callback);

        } catch (Exception e) {
            System.out.println("sacc query failed: " + e);
            callback.onFailed("sacc query failed: " + e);
        }
    }
    /**
     * @Description: 向代理合约提交交易
     *
     * @params: [callback]
     * @return: void @Author: mirsu @Date: 2020/10/30 10:23
     */
    private void sendTransactionOnceByProxy(PerformanceSuiteCallback callback) throws Exception {
        TransactionProposalRequest request = hfClient.newTransactionProposalRequest();
        String cc = proxyName;
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(cc).build();
        request.setChaincodeID(ccid);
        request.setFcn("sendTransaction");

        request.setArgs(buildSendTransactionProxyArgs());
        request.setProposalWaitTime(30000);
        // 发起交易提案
        Collection<ProposalResponse> proposalResponse =
                channel.sendTransactionProposal(request, endorsers);
        // 背书结果分析
        EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponse);

        if (!analyzer.allSuccess()) {
            throw new Exception("Not all transactionProposal success");
        }

        if (callback != null) {
            // 异步执行 结果回调
            channel.sendTransaction(proposalResponse)
                    .thenApply(
                            transactionEvent -> {
                                handleEvent(transactionEvent, callback);
                                return transactionEvent;
                            });

        } else {
            // 同步等待结果
            BlockEvent.TransactionEvent event =
                    channel.sendTransaction(proposalResponse).get(20, TimeUnit.SECONDS);
            if (!event.isValid()) {
                throw new Exception("Invoke failed");
            }
        }
    }

    private String[] buildSendTransactionProxyArgs() throws Exception {
        class ChaincodeArgs {
            public String[] args;

            public ChaincodeArgs() {}

            public ChaincodeArgs(String[] args) {
                this.args = args;
            }
        }

        String key = String.valueOf(rand.nextInt(BOUND));
        String value = String.valueOf(rand.nextInt(BOUND));
        String[] chaincodeArgs = {key, value};
        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));
        String[] args = {"0", "0", "payment.fabric.sacc", "set", argsJsonString};

        return args;
    }

    public static void handleEvent(
            BlockEvent.TransactionEvent transactionEvent, PerformanceSuiteCallback callback) {
        if (transactionEvent.isValid()) {
            long blockNumber = transactionEvent.getBlockEvent().getBlockNumber();
            byte[] blockNumberBytes = FabricUtils.longToBytes(blockNumber);
            callback.onSuccess(Arrays.toString(blockNumberBytes));
        } else {
            callback.onFailed("Failed");
        }
    }
}
