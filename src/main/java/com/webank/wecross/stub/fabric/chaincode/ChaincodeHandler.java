package com.webank.wecross.stub.fabric.chaincode;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric.*;
import com.webank.wecross.stub.fabric.FabricCustomCommand.*;
import com.webank.wecross.utils.FabricUtils;
import org.hyperledger.fabric.sdk.TransactionRequest;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * All rights Reserved, Designed By www.webank.com
 *
 * @version V1.0
 * @Title: ChaincodeHandler.java
 * @Package com.webank.wecross.stub.fabric.chaincode
 * @Description: 链码操作类
 * @author: mirsu
 * @date: 2020/11/3 13:57
 * @Copyright: 2020-2020/11/3  www.tbs.com Inc. All rights reserved.
 * <p>
 * 注意：本内容仅限于TBS项目组内部传阅，禁止外泄以及用于其他的商业目的
 */
public class ChaincodeHandler {

    private static Logger logger = LoggerFactory.getLogger(ChaincodeHandler.class);
    private ChaincodeHandler() {

    }

    /**
     * @Description: 打包链码
     * @params: [request]
     * @return: LifecycleChaincodePackage
     * @Author: mirsu
     * @Date: 2020/11/3 14:41
     *
     * @param request*/
    public static LifecycleChaincodePackage packageChaincode(PackageChaincodeRequest request) throws TransactionException {
        LifecycleChaincodePackage lifecycleChaincodePackage;
        try {
            request.check();
            String ccPath = request.getChaincodePath() + "/" + request.getChaincodeName();
            lifecycleChaincodePackage = LifecycleChaincodePackage.fromSource(request.getChaincodeLabel(),
                    Paths.get(request.getChaincodeSourcePath()),
                    FabricType.stringTochainCodeType(request.getChaincodeType()),
                    ccPath,
                    Paths.get(request.getChaincodeMetainfoPath()));
        } catch (Exception e) {
            String errorMessage = "Fabric driver package exception: " + e;
            logger.error(errorMessage);
            throw TransactionException.Builder.newInternalException(errorMessage);
        }
        return lifecycleChaincodePackage;
    }
    /**
     * @Description: 安装链码2.0（以组织为维度）
     * @params: [hfClient, channel, orgName, lifecycleChaincodePackage]
     * @return: java.lang.String
     * @Author: mirsu
     * @Date: 2020/11/5 10:47
     **/
    public static String installChaincode (HFClient hfClient,
                                           Channel channel, Collection<Peer> peers,
                                           LifecycleChaincodePackage lifecycleChaincodePackage) throws Exception {

        try {
            Collection<LifecycleInstallChaincodeProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            logger.info("Creating lifecycleInstallChaincodeRequest");
            LifecycleInstallChaincodeRequest lifecycleInstallChaincodeRequest = hfClient.newLifecycleInstallChaincodeRequest();
            lifecycleInstallChaincodeRequest.setLifecycleChaincodePackage(lifecycleChaincodePackage);
            lifecycleInstallChaincodeRequest.setProposalWaitTime(5 * 60 * 1000); // 等待5min
            logger.info("Sending lifecycleInstallChaincodeRequest to selected peers...");
            int numInstallProposal = peers.size();
            responses = hfClient.sendLifecycleInstallChaincodeRequest(lifecycleInstallChaincodeRequest, peers);
            for (ProposalResponse response : responses) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    logger.info("[√]Successful InstallChaincode proposal response Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }
            logger.info("Received {} InstallChaincode proposal responses. Successful+verified: {} . Failed: {}", numInstallProposal, successful.size(), failed.size());

            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error("[X] Not enough endorsers for install : {} . {}", successful.size(), first.getMessage());
                throw new Exception();
            }

            return ((LifecycleInstallChaincodeProposalResponse) successful.iterator().next()).getPackageId();

        } catch (InvalidArgumentException e1) {
            throw new Exception("InvalidArgumentException", e1);
        } catch (ProposalException e2) {
            throw new Exception("ProposalException", e2);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }


    /**
     * @Description: 异步安装链码
     * @params: [request, connection, callback]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 15:56
     **/
    @Deprecated
    public static void asyncInstallChaincode(
            TransactionContext<InstallChaincodeRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            checkInstallRequest(request);

            Request installRequest = EndorserRequestFactory.buildInstallProposalRequest(request);
            installRequest.setType(
                    FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER);

            if (request.getResourceInfo() == null) {
                ResourceInfo resourceInfo = new ResourceInfo();
                installRequest.setResourceInfo(resourceInfo);
            }

            byte[] envelopeRequestData =
                    TransactionParams.parseFrom(installRequest.getData()).getData();
            connection.asyncSend(
                    installRequest,
                    connectionResponse -> {
                        TransactionResponse response = new TransactionResponse();
                        TransactionException transactionException;
                        try {
                            if (connectionResponse.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {
                                response = FabricUtils.decodeTransactionResponse(connectionResponse.getData());
                                response.setHash(EndorserRequestFactory.getTxIDFromEnvelopeBytes(envelopeRequestData));
                            }
                            transactionException =
                                    new TransactionException(
                                            connectionResponse.getErrorCode(),
                                            connectionResponse.getErrorMessage());
                        } catch (Exception e) {
                            String errorMessage =
                                    "Fabric driver install chaincode onResponse exception: "
                                            + e;
                            logger.error(errorMessage);
                            transactionException =
                                    TransactionException.Builder.newInternalException(
                                            errorMessage);
                        }
                        callback.onTransactionResponse(transactionException, response);
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver install exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }
    /**
     * @Description: 异步实例化合约
     * @params: [request, connection, callback]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 17:08
     **/
    @Deprecated
    public  static void asyncInstantiateChaincode(
            TransactionContext<InstantiateChaincodeRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            checkInstantiateRequest(request);

            Request instantiateRequest =
                    EndorserRequestFactory.buildInstantiateProposalRequest(request);
            instantiateRequest.setType(
                    FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER);

            if (request.getResourceInfo() == null) {
                ResourceInfo resourceInfo = new ResourceInfo();
                instantiateRequest.setResourceInfo(resourceInfo);
            }

            byte[] envelopeRequestData =
                    TransactionParams.parseFrom(instantiateRequest.getData()).getData();
            connection.asyncSend(
                    instantiateRequest,
                    endorserResponse -> asyncSendTransactionHandleEndorserResponse(
                            request,
                            envelopeRequestData,
                            endorserResponse,
                            connection,
                            callback));

        } catch (Exception e) {
            String errorMessage = "Fabric driver instantiate exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    /**
     * @Description:异步更新合约
     * @params: [request, connection, callback]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 17:29
     **/
    @Deprecated
    public static void asyncUpgradeChaincode(
            TransactionContext<UpgradeChaincodeRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            checkUpgradeRequest(request);

            Request upgradeRequest = EndorserRequestFactory.buildUpgradeProposalRequest(request);
            upgradeRequest.setType(
                    FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER);

            if (request.getResourceInfo() == null) {
                ResourceInfo resourceInfo = new ResourceInfo();
                upgradeRequest.setResourceInfo(resourceInfo);
            }

            byte[] envelopeRequestData =
                    TransactionParams.parseFrom(upgradeRequest.getData()).getData();
            connection.asyncSend(
                    upgradeRequest,
                    endorserResponse -> asyncSendTransactionHandleEndorserResponse(
                            request,
                            envelopeRequestData,
                            endorserResponse,
                            connection,
                            callback));

            //todo
            System.out.println("upgradeRequest:->" + upgradeRequest.toString());
        } catch (Exception e) {
            String errorMessage = "Fabric driver upgrade exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }
    public static void asyncSendTransactionHandleEndorserResponse(
            TransactionContext<?> request,
            byte[] envelopeRequestData,
            Response endorserResponse,
            Connection connection,
            Driver.Callback callback) {
        if (endorserResponse.getErrorCode() != FabricType.TransactionResponseStatus.SUCCESS) {
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    new TransactionException(
                            endorserResponse.getErrorCode(), endorserResponse.getErrorMessage());
            callback.onTransactionResponse(transactionException, response);
            return;
        } else {
            // Send to orderer
            try {
                byte[] ordererPayloadToSign = endorserResponse.getData();
                Request ordererRequest =
                        OrdererRequestFactory.build(request.getAccount(), ordererPayloadToSign);
                ordererRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER);
                ordererRequest.setResourceInfo(request.getResourceInfo());

                connection.asyncSend(
                        ordererRequest,
                        new Connection.Callback() {
                            @Override
                            public void onResponse(Response ordererResponse) {
                                asyncSendTransactionHandleOrdererResponse(
                                        request,
                                        envelopeRequestData,
                                        ordererPayloadToSign,
                                        ordererResponse,
                                        callback);
                            }
                        });

            } catch (Exception e) {
                String errorMessage = "Fabric driver call orderer exception: " + e;
                logger.error(errorMessage);
                TransactionResponse response = new TransactionResponse();
                TransactionException transactionException =
                        TransactionException.Builder.newInternalException(errorMessage);
                callback.onTransactionResponse(transactionException, response);
            }
        }
    }

    public static void asyncSendTransactionHandleOrdererResponse(
            TransactionContext<?> request,
            byte[] envelopeRequestData,
            byte[] ordererPayloadToSign,
            Response ordererResponse,
            Driver.Callback callback) {
        try {
            if (ordererResponse.getErrorCode() == FabricType.TransactionResponseStatus.SUCCESS) {
                // Success, verify transaction
                String txID = EndorserRequestFactory.getTxIDFromEnvelopeBytes(envelopeRequestData);
                long txBlockNumber = FabricUtils.bytesToLong(ordererResponse.getData());

                asyncVerifyTransactionOnChain(
                        txID,
                        txBlockNumber,
                        request.getBlockHeaderManager(),
                        new Consumer<Boolean>() {
                            @Override
                            public void accept(Boolean verifyResult) {
                                TransactionResponse response = new TransactionResponse();
                                TransactionException transactionException = null;
                                try {
                                    if (!verifyResult) {
                                        transactionException =
                                                new TransactionException(
                                                        FabricType.TransactionResponseStatus
                                                                .FABRIC_TX_ONCHAIN_VERIFY_FAIED,
                                                        "Verify failed. Tx("
                                                                + txID
                                                                + ") is invalid or not on block("
                                                                + txBlockNumber
                                                                + ")");
                                    } else {
                                        response =
                                                FabricUtils.decodeTransactionResponse(
                                                        FabricTransaction.buildFromPayloadBytes(
                                                                ordererPayloadToSign)
                                                                .getOutputBytes());
                                        response.setHash(txID);
                                        response.setBlockNumber(txBlockNumber);
                                        response.setErrorCode(
                                                FabricType.TransactionResponseStatus.SUCCESS);
                                        response.setErrorMessage("Success");
                                        transactionException =
                                                TransactionException.Builder.newSuccessException();
                                    }
                                } catch (Exception e) {
                                    transactionException =
                                            new TransactionException(
                                                    FabricType.TransactionResponseStatus
                                                            .FABRIC_TX_ONCHAIN_VERIFY_FAIED,
                                                    "Verify failed. Tx("
                                                            + txID
                                                            + ") is invalid or not on block("
                                                            + txBlockNumber
                                                            + ") Internal error: "
                                                            + e);
                                }
                                callback.onTransactionResponse(transactionException, response);
                            }
                        });

            } else if (ordererResponse.getErrorCode()
                    == FabricType.TransactionResponseStatus.FABRIC_EXECUTE_CHAINCODE_FAILED) {
                TransactionResponse response = new TransactionResponse();
                Integer errorCode = new Integer(ordererResponse.getData()[0]);
                // If transaction execute failed, fabric TxValidationCode is in data
                TransactionException transactionException =
                        new TransactionException(
                                ordererResponse.getErrorCode(), ordererResponse.getErrorMessage());
                response.setErrorCode(errorCode);
                response.setErrorMessage(ordererResponse.getErrorMessage());
                callback.onTransactionResponse(transactionException, response);
            } else {
                TransactionResponse response = new TransactionResponse();
                TransactionException transactionException =
                        new TransactionException(
                                ordererResponse.getErrorCode(), ordererResponse.getErrorMessage());
                callback.onTransactionResponse(transactionException, response);
            }

        } catch (Exception e) {
            String errorMessage = "Fabric driver call handle orderer response exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            response.setErrorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR);
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }
    public static void asyncVerifyTransactionOnChain(
            String txID,
            long blockNumber,
            BlockHeaderManager blockHeaderManager,
            Consumer<Boolean> callback) {
        logger.debug("To verify transaction, waiting fabric block syncing ...");
        blockHeaderManager.asyncGetBlockHeader(
                blockNumber,
                new BlockHeaderManager.GetBlockHeaderCallback() {
                    @Override
                    public void onResponse(Exception e, byte[] blockHeader) {
                        logger.debug("Receive block, verify transaction ...");
                        boolean verifyResult;
                        try {
                            FabricBlock block = FabricBlock.encode(blockHeader);
                            verifyResult = block.hasTransaction(txID);
                            logger.debug(
                                    "Tx(block: "
                                            + blockNumber
                                            + "): "
                                            + txID
                                            + " verify: "
                                            + verifyResult);
                        } catch (Exception e1) {
                            logger.debug("Consumer accept exception, {}", e1);
                            verifyResult = false;
                        }
                        callback.accept(verifyResult);
                    }
                });
    }
    public static void handleInstallCommand(
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            Driver.CustomCommandCallback callback) {

        try {
            FabricConnection.Properties properties =
                    FabricConnection.Properties.parseFromMap(connection.getProperties());
            String channelName = properties.getChannelName();
            if (channelName == null) {
                throw new Exception("Connection properties(ChannelName) is not set");
            }

            InstallChaincodeRequest installChaincodeRequest =
                    InstallCommand.parseEncodedArgs(args, channelName); // parse args from sdk

            TransactionContext<InstallChaincodeRequest> installRequest =
                    new TransactionContext<InstallChaincodeRequest>(
                            installChaincodeRequest, account, null, null, blockHeaderManager);

            asyncInstallChaincode(
                    installRequest,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            if (transactionException.isSuccess()) {
                                callback.onResponse(null, new String("Success"));
                            } else {
                                callback.onResponse(
                                        transactionException,
                                        new String("Failed: ") + transactionException.getMessage());
                            }
                        }
                    });

        } catch (Exception e) {
            callback.onResponse(e, new String("Failed: ") + e.getMessage());
        }
    }

    public static void handleInstantiateCommand(
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            Driver.CustomCommandCallback callback) {
        try {
            FabricConnection.Properties properties =
                    FabricConnection.Properties.parseFromMap(connection.getProperties());
            String channelName = properties.getChannelName();
            if (channelName == null) {
                throw new Exception("Connection properties(ChannelName) is not set");
            }

            InstantiateChaincodeRequest instantiateChaincodeRequest =
                    InstantiateCommand.parseEncodedArgs(args, channelName);

            TransactionContext<InstantiateChaincodeRequest> instantiateRequest =
                    new TransactionContext<InstantiateChaincodeRequest>(
                            instantiateChaincodeRequest, account, null, null, blockHeaderManager);
            AtomicBoolean hasResponsed = new AtomicBoolean(false);
            asyncInstantiateChaincode(
                    instantiateRequest,
                    connection,
                    new Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            logger.debug(
                                    "asyncInstantiateChaincode response:{} e:{}",
                                    transactionResponse,
                                    transactionException);
                            if (!hasResponsed.getAndSet(true)) {
                                if (transactionException.isSuccess()) {
                                    callback.onResponse(null, new String("Success"));
                                } else {
                                    callback.onResponse(
                                            transactionException,
                                            new String("Failed: ")
                                                    + transactionException.getMessage());
                                }
                            }
                        }
                    });
            Thread.sleep(5000); // Sleep for error response
            if (!hasResponsed.getAndSet(true)) {
                callback.onResponse(
                        null,
                        new String(
                                "Instantiating... Please wait and use 'listResources' to check. See router's log for more information."));
            }

        } catch (Exception e) {
            callback.onResponse(e, new String("Failed: ") + e.getMessage());
        }
    }

    public static void handleUpgradeCommand(
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            Driver.CustomCommandCallback callback) {
        try {
            FabricConnection.Properties properties =
                    FabricConnection.Properties.parseFromMap(connection.getProperties());
            String channelName = properties.getChannelName();
            if (channelName == null) {
                throw new Exception("Connection properties(ChannelName) is not set");
            }

            UpgradeChaincodeRequest upgradeChaincodeRequest =
                    UpgradeCommand.parseEncodedArgs(args, channelName);

            TransactionContext<UpgradeChaincodeRequest> upgradeRequest =
                    new TransactionContext<UpgradeChaincodeRequest>(
                            upgradeChaincodeRequest, account, null, null, blockHeaderManager);
            AtomicBoolean hasResponsed = new AtomicBoolean(false);
            asyncUpgradeChaincode(
                    upgradeRequest,
                    connection,
                    (transactionException, transactionResponse) -> {
                        logger.debug(
                                "asyncUpgradeChaincode response:{} e:{}",
                                transactionResponse,
                                transactionException);
                        if (!hasResponsed.getAndSet(true)) {
                            if (transactionException.isSuccess()) {
                                callback.onResponse(null, new String("Success"));
                            } else {
                                callback.onResponse(
                                        transactionException,
                                        new String("Failed: ")
                                                + transactionException.getMessage());
                            }
                        }
                    });
            Thread.sleep(5000); // Sleep for error response
            if (!hasResponsed.getAndSet(true)) {
                callback.onResponse(
                        null,
                        new String(
                                "Upgrading... Please wait and use 'detail' to check the version. See router's log for more information."));
            }

        } catch (Exception e) {
            callback.onResponse(e, new String("Failed: ") + e.getMessage());
        }
    }
    private static void checkPackageRequest(TransactionContext<PackageChaincodeRequest> request) throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

    }
    private static void checkInstallRequest(TransactionContext<InstallChaincodeRequest> request)
            throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }

    private static void checkInstantiateRequest(TransactionContext<InstantiateChaincodeRequest> request)
            throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }

    private static void checkUpgradeRequest(TransactionContext<UpgradeChaincodeRequest> request)
            throws Exception {
        if (request.getData() == null) {
            throw new Exception("Request data is null");
        }
        request.getData().check();

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }
    public static Collection<Peer> extractPeersFromChannel (Channel channel, String orgName) throws InvalidArgumentException {
        Collection<Peer> allPeers = channel.getPeers();
        if (!StringUtils.hasText(orgName)) {
            return allPeers;
        } else {
            Collection<Peer> selectPeers = new ArrayList<>();
            boolean isFind = false;
            for (Peer peer : allPeers) {
                if (peer.getName().contains(orgName)) {
                    selectPeers.add(peer);
                        isFind = true;
                        break;
                    }
                }
                if (!isFind) {
                    throw new InvalidArgumentException(String.format("The specified peer %s does not exist in the current channel: %s.", orgName, channel.getName()));
                }
            return selectPeers;
        }
    }

    public static CompletableFuture<BlockEvent.TransactionEvent> approveForMyOrg(HFClient hfClient, Channel channel, Collection<Peer> peers, long sequence, String chaincodeName, String version, LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy, ChaincodeCollectionConfiguration ccCollectionConfiguration, boolean initRequired, String packageId) throws Exception {
        try {

            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            LifecycleApproveChaincodeDefinitionForMyOrgRequest lifecycleApproveChaincodeDefinitionForMyOrgRequest = hfClient.newLifecycleApproveChaincodeDefinitionForMyOrgRequest();
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setPackageId(packageId);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeName(chaincodeName);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeVersion(version);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setSequence(sequence);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setInitRequired(initRequired);

            if (lccEndorsementPolicy != null) {
                lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeEndorsementPolicy(lccEndorsementPolicy);
            }

            if (null != ccCollectionConfiguration) {
                lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeCollectionConfiguration(ccCollectionConfiguration);
            }

            logger.info("Sending lifecycleApproveChaincodeRequest to selected peers...");
            Collection<LifecycleApproveChaincodeDefinitionForMyOrgProposalResponse> responses = channel.sendLifecycleApproveChaincodeDefinitionForMyOrgProposal(lifecycleApproveChaincodeDefinitionForMyOrgRequest, peers);
            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    logger.info("[√] Succesful ApproveChaincode proposal response Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            logger.info("Received {} ApproveChaincode proposal responses. Successful+verified: {} . Failed:{}", responses.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error("[X] Not enough endorsers for ApproveChaincode : {} endorser failed with {}. Was verified: {}", successful.size(), first.getMessage(), first.isVerified());
            }
            return channel.sendTransaction(successful);
        } catch (InvalidArgumentException e1) {
            throw new Exception("InvalidArgumentException", e1);
        } catch (ProposalException e2) {
            throw new Exception("ProposalException", e2);
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    // 提交链码
    // peer lifecycle chaincode commit \
    // -o orderer.example.com:7050 \
    // --tls true \
    // --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem \
    // --channelID mychannel \
    // --name mycc \
    // --peerAddresses peer0.org1.example.com:7051 \
    // --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt \
    // --peerAddresses peer0.org2.example.com:9051 \
    // --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt \
    // --version 1 \
    // --sequence 1 \
    // --init-required
    public static CompletableFuture<BlockEvent.TransactionEvent> commitChaincodeDefinition (HFClient hfClient,
                                                                                            Channel channel,
                                                                                            long sequence,
                                                                                            String chaincodeName,
                                                                                            String chaincodeVersion,
                                                                                            LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy,
                                                                                            ChaincodeCollectionConfiguration chaincodeCollectionConfiguration,
                                                                                            boolean initRequired,
                                                                                            Collection<Peer> endorsingPeers) throws Exception {

        try {

            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            LifecycleCommitChaincodeDefinitionRequest lifecycleCommitChaincodeDefinitionRequest = hfClient.newLifecycleCommitChaincodeDefinitionRequest();
            lifecycleCommitChaincodeDefinitionRequest.setChaincodeName(chaincodeName);
            lifecycleCommitChaincodeDefinitionRequest.setChaincodeVersion(chaincodeVersion);
            lifecycleCommitChaincodeDefinitionRequest.setSequence(sequence);
            lifecycleCommitChaincodeDefinitionRequest.setInitRequired(initRequired);
            if (lccEndorsementPolicy != null) {
                lifecycleCommitChaincodeDefinitionRequest.setChaincodeEndorsementPolicy(lccEndorsementPolicy);
            }

            if (null != chaincodeCollectionConfiguration) {
                lifecycleCommitChaincodeDefinitionRequest.setChaincodeCollectionConfiguration(chaincodeCollectionConfiguration);
            }

            logger.info("Sending lifecycleApproveChaincodeRequest to selected peers...");
            Collection<LifecycleCommitChaincodeDefinitionProposalResponse>  responses = channel.sendLifecycleCommitChaincodeDefinitionProposal(lifecycleCommitChaincodeDefinitionRequest, endorsingPeers);
            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    logger.info("[√] Succesful CommitChaincode proposal response Txid:{} from peer {}", response.getTransactionID(), response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            logger.info("Received {} CommitChaincode proposal responses. Successful+verified: {} . Failed: {}", responses.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error("[X] Not enough endorsers for CommitChaincode : {} endorser failed with {}. Was verified: {}", successful.size(), first.getMessage(), first.isVerified());
            }

            if (null == successful | successful.isEmpty()) {
                throw new Exception("commit chaincode definition fail.");
            }

            ///////////////
            /// Send instantiate transaction to orderer
            logger.info("Sending instantiateTransaction to orderer...");
            return channel.sendTransaction(successful);

        } catch (InvalidArgumentException e1) {
            throw new Exception("InvalidArgumentException", e1);
        } catch (ProposalException e2) {
            throw new Exception("ProposalException", e2);
        } catch (Exception e3) {
            throw e3;
        }
    }

    public static CompletableFuture<BlockEvent.TransactionEvent> initChaincode(HFClient hfClient, User user, Channel channel, boolean initRequired, String chaincodeName, String version, TransactionRequest.Type chainCodeType, String[] args) throws Exception {
        final String fcn = "init";
        boolean doInit = initRequired ? true : null;

        return basicInvokeChaincode(hfClient, user, channel, fcn, doInit, chaincodeName, version, chainCodeType, args);
    }
    private static CompletableFuture<BlockEvent.TransactionEvent> basicInvokeChaincode (HFClient hfClient,
                                                                                        User userContext,
                                                                                        Channel channel,
                                                                                        String fcn,
                                                                                        Boolean doInit,
                                                                                        String chaincodeName,
                                                                                        String chaincodeVersion,
                                                                                        TransactionRequest.Type chaincodeType,
                                                                                        String[] args) throws Exception {
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        try {

            TransactionProposalRequest transactionProposalRequest = hfClient.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeName(chaincodeName);
            transactionProposalRequest.setChaincodeVersion(chaincodeVersion);
            transactionProposalRequest.setChaincodeLanguage(chaincodeType);
            transactionProposalRequest.setFcn(fcn);
            transactionProposalRequest.setArgs(args);
            transactionProposalRequest.setProposalWaitTime(60 * 1000);

            if (userContext != null) {
                transactionProposalRequest.setUserContext(userContext);
            }

            if (doInit != null) {
                transactionProposalRequest.setInit(doInit);
            }

            logger.info("Sending transaction proposal on channel {} to all peers with arguments: {}:{}.{}({})",
                    channel.getName(), chaincodeName, chaincodeVersion, fcn, Arrays.asList(args));
            Collection<ProposalResponse> responses = channel.sendTransactionProposal(transactionProposalRequest);
            for (ProposalResponse response : responses) {
                if (response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                    logger.info("[√] Successful transaction proposal response Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    logger.error("[X] Failed transaction proposal response Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(responses);
            if (proposalConsistencySets.size() != 1) {
                logger.error("Expected only one set of consistent move proposal responses but got {}", proposalConsistencySets.size());
            }

            logger.info("Received {} transaction proposal responses. Successful+verified: {} . Failed: {}", responses.size(), successful.size(),
                    failed.size());

            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                logger.error("Not enough endorsers for invoke({}:{}.{}({})):{} endorser error:{}. Was verified:{}",
                        chaincodeName, chaincodeVersion, fcn, Arrays.asList(args), firstTransactionProposalResponse.getStatus().getStatus(),
                        firstTransactionProposalResponse.getMessage(), firstTransactionProposalResponse.isVerified());
            }

            logger.info("Successfully received transaction proposal responses.");

            logger.info("Sending chaincode transaction：invoke({}:{}.{}({})) to orderer.", chaincodeName, chaincodeVersion, fcn, Arrays.asList(args));

            return channel.sendTransaction(successful);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static boolean queryCommitted(HFClient hfClient, Channel channel, String chaincodeName, Collection<Peer> peers, long sequence, boolean initRequired) throws Exception {
        try {
            QueryLifecycleQueryChaincodeDefinitionRequest queryLifecycleQueryChaincodeDefinitionRequest = hfClient.newQueryLifecycleQueryChaincodeDefinitionRequest();
            queryLifecycleQueryChaincodeDefinitionRequest.setChaincodeName(chaincodeName);

            Collection<LifecycleQueryChaincodeDefinitionProposalResponse> responses = channel.lifecycleQueryChaincodeDefinition(queryLifecycleQueryChaincodeDefinitionRequest, peers);

            if (peers.size() != responses.size()) {
                throw new Exception(String.format("responses %d not same as peers %d.", responses.size(), peers.size()));
            }

            boolean checkResult = true;

            for (LifecycleQueryChaincodeDefinitionProposalResponse response : responses) {
                String peer = response.getPeer().getName();

                if (ChaincodeResponse.Status.SUCCESS.equals(response.getStatus())) {

                    if (sequence != response.getSequence()) {
                        logger.error("[X] With {} inconsistent -sequence，actually: {}, expect: {}", peer, response.getSequence(), sequence);
                        checkResult = false;
                    }

                    if (initRequired != response.getInitRequired()) {
                        logger.error("[X] With {} inconsistent -initRequired，actually: {}, expect: {}", peer, response.getInitRequired(), initRequired);
                        checkResult = false;
                    }


                } else {
                    logger.error("[X] Received {} bad response, status: {}, message: {}.", peer, response.getStatus(), response.getMessage());
                }
            }
            return checkResult;

        }   catch (Exception e) {
            throw new Exception(e);
        }


    }

    public static boolean queryInstalled(HFClient hfClient, Collection<Peer> peers, String packageId, String chaincodeLabel) throws Exception {
        try {

            final LifecycleQueryInstalledChaincodeRequest lifecycleQueryInstalledChaincodeRequest = hfClient.newLifecycleQueryInstalledChaincodeRequest();
            lifecycleQueryInstalledChaincodeRequest.setPackageID(packageId);
            Collection<LifecycleQueryInstalledChaincodeProposalResponse> responses = hfClient.sendLifecycleQueryInstalledChaincode(lifecycleQueryInstalledChaincodeRequest, peers);

            if (peers.size() != responses.size()) {
                throw new Exception(String.format("responses %d not same as peers %d.", responses.size(), peers.size()));
            }

            boolean found = false;
            for (LifecycleQueryInstalledChaincodeProposalResponse response : responses) {

                String peerName = response.getPeer().getName();

                if (response.getStatus().equals(ChaincodeResponse.Status.SUCCESS)) {
                    if (chaincodeLabel.equals(response.getLabel())) {
                        logger.info("[√] Peer {} returned back same label: {}", peerName, response.getLabel());
                        found = true;
                    } else {
                        logger.info("[?] Peer {} returned back different label: {}", peerName, response.getLabel());
                    }
                } else {
                    logger.error("[X] Peer {} returned back bad status code: {}", peerName, response.getStatus());
                }
            }

            return found;

        } catch (Exception e1) {
            e1.printStackTrace();
            throw e1;
        }
    }
}
