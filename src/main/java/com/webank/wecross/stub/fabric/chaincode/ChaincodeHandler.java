package com.webank.wecross.stub.fabric.chaincode;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric.*;
import com.webank.wecross.stub.fabric.FabricCustomCommand.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.TransactionRequest;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @version V1.0 @Title: ChaincodeHandler.java @Package
 *     com.webank.wecross.stub.fabric.chaincode @Description: 链码操作类
 * @author: mirsu
 * @date: 2020/11/3 13:57
 */
public class ChaincodeHandler {

    private static Logger logger = LoggerFactory.getLogger(ChaincodeHandler.class);

    private ChaincodeHandler() {}

    /**
     * @Description: 打包链码
     *
     * @params: [request]
     * @return: LifecycleChaincodePackage @Author: mirsu @Date: 2020/11/3 14:41
     * @param request
     */
    public static LifecycleChaincodePackage packageChaincode(PackageChaincodeRequest request)
            throws TransactionException {
        LifecycleChaincodePackage lifecycleChaincodePackage;
        try {
            request.check();
            String ccPath = request.getChaincodePath() + "/" + request.getChaincodeName();
            lifecycleChaincodePackage =
                    LifecycleChaincodePackage.fromSource(
                            request.getChaincodeLabel(),
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
     *
     * @params: [hfClient, channel, orgName, lifecycleChaincodePackage]
     * @return: java.lang.String @Author: mirsu @Date: 2020/11/5 10:47
     */
    public static String installChaincode(
            HFClient hfClient,
            Channel channel,
            Collection<Peer> peers,
            LifecycleChaincodePackage lifecycleChaincodePackage)
            throws Exception {

        try {
            Collection<LifecycleInstallChaincodeProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            logger.info("Creating lifecycleInstallChaincodeRequest");
            LifecycleInstallChaincodeRequest lifecycleInstallChaincodeRequest =
                    hfClient.newLifecycleInstallChaincodeRequest();
            lifecycleInstallChaincodeRequest.setLifecycleChaincodePackage(
                    lifecycleChaincodePackage);
            lifecycleInstallChaincodeRequest.setProposalWaitTime(5 * 60 * 1000); // 等待5min
            logger.info("Sending lifecycleInstallChaincodeRequest to selected peers...");
            int numInstallProposal = peers.size();
            responses =
                    hfClient.sendLifecycleInstallChaincodeRequest(
                            lifecycleInstallChaincodeRequest, peers);
            for (ProposalResponse response : responses) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    logger.info(
                            "[√]Successful InstallChaincode proposal response Txid: {} from peer {}",
                            response.getTransactionID(),
                            response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }
            logger.info(
                    "Received {} InstallChaincode proposal responses. Successful+verified: {} . Failed: {}",
                    numInstallProposal,
                    successful.size(),
                    failed.size());

            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error(
                        "[X] Not enough endorsers for install : {} . {}",
                        successful.size(),
                        first.getMessage());
                throw new Exception();
            }

            return ((LifecycleInstallChaincodeProposalResponse) successful.iterator().next())
                    .getPackageId();

        } catch (InvalidArgumentException e1) {
            throw new Exception("InvalidArgumentException", e1);
        } catch (ProposalException e2) {
            throw new Exception("ProposalException", e2);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static Collection<Peer> extractPeersFromChannel(Channel channel, String orgName)
            throws InvalidArgumentException {
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
                throw new InvalidArgumentException(
                        String.format(
                                "The specified peer %s does not exist in the current channel: %s.",
                                orgName, channel.getName()));
            }
            return selectPeers;
        }
    }

    public static CompletableFuture<BlockEvent.TransactionEvent> approveForMyOrg(
            HFClient hfClient,
            Channel channel,
            Collection<Peer> peers,
            long sequence,
            String chaincodeName,
            String version,
            LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy,
            ChaincodeCollectionConfiguration ccCollectionConfiguration,
            boolean initRequired,
            String packageId)
            throws Exception {
        try {

            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            LifecycleApproveChaincodeDefinitionForMyOrgRequest
                    lifecycleApproveChaincodeDefinitionForMyOrgRequest =
                            hfClient.newLifecycleApproveChaincodeDefinitionForMyOrgRequest();
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setPackageId(packageId);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeName(chaincodeName);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeVersion(version);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setSequence(sequence);
            lifecycleApproveChaincodeDefinitionForMyOrgRequest.setInitRequired(initRequired);

            if (lccEndorsementPolicy != null) {
                lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeEndorsementPolicy(
                        lccEndorsementPolicy);
            }

            if (null != ccCollectionConfiguration) {
                lifecycleApproveChaincodeDefinitionForMyOrgRequest
                        .setChaincodeCollectionConfiguration(ccCollectionConfiguration);
            }

            logger.info("Sending lifecycleApproveChaincodeRequest to selected peers...");
            Collection<LifecycleApproveChaincodeDefinitionForMyOrgProposalResponse> responses =
                    channel.sendLifecycleApproveChaincodeDefinitionForMyOrgProposal(
                            lifecycleApproveChaincodeDefinitionForMyOrgRequest, peers);
            for (ProposalResponse response : responses) {
                if (response.isVerified()
                        && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    logger.info(
                            "[√] Succesful ApproveChaincode proposal response Txid: {} from peer {}",
                            response.getTransactionID(),
                            response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            logger.info(
                    "Received {} ApproveChaincode proposal responses. Successful+verified: {} . Failed:{}",
                    responses.size(),
                    successful.size(),
                    failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error(
                        "[X] Not enough endorsers for ApproveChaincode : {} endorser failed with {}. Was verified: {}",
                        successful.size(),
                        first.getMessage(),
                        first.isVerified());
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
    // --cafile
    // /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem \
    // --channelID mychannel \
    // --name mycc \
    // --peerAddresses peer0.org1.example.com:7051 \
    // --tlsRootCertFiles
    // /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt \
    // --peerAddresses peer0.org2.example.com:9051 \
    // --tlsRootCertFiles
    // /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt \
    // --version 1 \
    // --sequence 1 \
    // --init-required
    public static CompletableFuture<BlockEvent.TransactionEvent> commitChaincodeDefinition(
            HFClient hfClient,
            Channel channel,
            long sequence,
            String chaincodeName,
            String chaincodeVersion,
            LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy,
            ChaincodeCollectionConfiguration chaincodeCollectionConfiguration,
            boolean initRequired,
            Collection<Peer> endorsingPeers)
            throws Exception {

        try {

            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            LifecycleCommitChaincodeDefinitionRequest lifecycleCommitChaincodeDefinitionRequest =
                    hfClient.newLifecycleCommitChaincodeDefinitionRequest();
            lifecycleCommitChaincodeDefinitionRequest.setChaincodeName(chaincodeName);
            lifecycleCommitChaincodeDefinitionRequest.setChaincodeVersion(chaincodeVersion);
            lifecycleCommitChaincodeDefinitionRequest.setSequence(sequence);
            lifecycleCommitChaincodeDefinitionRequest.setInitRequired(initRequired);
            if (lccEndorsementPolicy != null) {
                lifecycleCommitChaincodeDefinitionRequest.setChaincodeEndorsementPolicy(
                        lccEndorsementPolicy);
            }

            if (null != chaincodeCollectionConfiguration) {
                lifecycleCommitChaincodeDefinitionRequest.setChaincodeCollectionConfiguration(
                        chaincodeCollectionConfiguration);
            }

            logger.info("Sending lifecycleApproveChaincodeRequest to selected peers...");
            Collection<LifecycleCommitChaincodeDefinitionProposalResponse> responses =
                    channel.sendLifecycleCommitChaincodeDefinitionProposal(
                            lifecycleCommitChaincodeDefinitionRequest, endorsingPeers);
            for (ProposalResponse response : responses) {
                if (response.isVerified()
                        && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    logger.info(
                            "[√] Succesful CommitChaincode proposal response Txid:{} from peer {}",
                            response.getTransactionID(),
                            response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            logger.info(
                    "Received {} CommitChaincode proposal responses. Successful+verified: {} . Failed: {}",
                    responses.size(),
                    successful.size(),
                    failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error(
                        "[X] Not enough endorsers for CommitChaincode : {} endorser failed with {}. Was verified: {}",
                        successful.size(),
                        first.getMessage(),
                        first.isVerified());
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

    public static CompletableFuture<BlockEvent.TransactionEvent> initChaincode(
            HFClient hfClient,
            User user,
            Channel channel,
            boolean initRequired,
            String chaincodeName,
            String version,
            TransactionRequest.Type chainCodeType,
            String[] args)
            throws Exception {
        final String fcn = "init";
        boolean doInit = initRequired ? true : null;

        return basicInvokeChaincode(
                hfClient, user, channel, fcn, doInit, chaincodeName, version, chainCodeType, args);
    }

    private static CompletableFuture<BlockEvent.TransactionEvent> basicInvokeChaincode(
            HFClient hfClient,
            User userContext,
            Channel channel,
            String fcn,
            Boolean doInit,
            String chaincodeName,
            String chaincodeVersion,
            TransactionRequest.Type chaincodeType,
            String[] args)
            throws Exception {
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        try {

            TransactionProposalRequest transactionProposalRequest =
                    hfClient.newTransactionProposalRequest();
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

            logger.info(
                    "Sending transaction proposal on channel {} to all peers with arguments: {}:{}.{}({})",
                    channel.getName(),
                    chaincodeName,
                    chaincodeVersion,
                    fcn,
                    Arrays.asList(args));
            Collection<ProposalResponse> responses =
                    channel.sendTransactionProposal(transactionProposalRequest);
            for (ProposalResponse response : responses) {
                if (response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                    logger.info(
                            "[√] Successful transaction proposal response Txid: {} from peer {}",
                            response.getTransactionID(),
                            response.getPeer().getName());
                    successful.add(response);
                } else {
                    logger.error(
                            "[X] Failed transaction proposal response Txid: {} from peer {}",
                            response.getTransactionID(),
                            response.getPeer().getName());
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one
            // set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets =
                    SDKUtils.getProposalConsistencySets(responses);
            if (proposalConsistencySets.size() != 1) {
                logger.error(
                        "Expected only one set of consistent move proposal responses but got {}",
                        proposalConsistencySets.size());
            }

            logger.info(
                    "Received {} transaction proposal responses. Successful+verified: {} . Failed: {}",
                    responses.size(),
                    successful.size(),
                    failed.size());

            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                logger.error(
                        "Not enough endorsers for invoke({}:{}.{}({})):{} endorser error:{}. Was verified:{}",
                        chaincodeName,
                        chaincodeVersion,
                        fcn,
                        Arrays.asList(args),
                        firstTransactionProposalResponse.getStatus().getStatus(),
                        firstTransactionProposalResponse.getMessage(),
                        firstTransactionProposalResponse.isVerified());
            }

            logger.info("Successfully received transaction proposal responses.");

            logger.info(
                    "Sending chaincode transaction：invoke({}:{}.{}({})) to orderer.",
                    chaincodeName,
                    chaincodeVersion,
                    fcn,
                    Arrays.asList(args));

            return channel.sendTransaction(successful);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static boolean queryCommitted(
            HFClient hfClient,
            Channel channel,
            String chaincodeName,
            Collection<Peer> peers,
            long sequence,
            boolean initRequired)
            throws Exception {
        try {
            QueryLifecycleQueryChaincodeDefinitionRequest
                    queryLifecycleQueryChaincodeDefinitionRequest =
                            hfClient.newQueryLifecycleQueryChaincodeDefinitionRequest();
            queryLifecycleQueryChaincodeDefinitionRequest.setChaincodeName(chaincodeName);

            Collection<LifecycleQueryChaincodeDefinitionProposalResponse> responses =
                    channel.lifecycleQueryChaincodeDefinition(
                            queryLifecycleQueryChaincodeDefinitionRequest, peers);

            if (peers.size() != responses.size()) {
                throw new Exception(
                        String.format(
                                "responses %d not same as peers %d.",
                                responses.size(), peers.size()));
            }

            boolean checkResult = true;

            for (LifecycleQueryChaincodeDefinitionProposalResponse response : responses) {
                String peer = response.getPeer().getName();

                if (ChaincodeResponse.Status.SUCCESS.equals(response.getStatus())) {

                    if (sequence != response.getSequence()) {
                        logger.error(
                                "[X] With {} inconsistent -sequence，actually: {}, expect: {}",
                                peer,
                                response.getSequence(),
                                sequence);
                        checkResult = false;
                    }

                    if (initRequired != response.getInitRequired()) {
                        logger.error(
                                "[X] With {} inconsistent -initRequired，actually: {}, expect: {}",
                                peer,
                                response.getInitRequired(),
                                initRequired);
                        checkResult = false;
                    }

                } else {
                    logger.error(
                            "[X] Received {} bad response, status: {}, message: {}.",
                            peer,
                            response.getStatus(),
                            response.getMessage());
                }
            }
            return checkResult;

        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    public static boolean queryInstalled(
            HFClient hfClient, Collection<Peer> peers, String packageId, String chaincodeLabel)
            throws Exception {
        try {

            final LifecycleQueryInstalledChaincodeRequest lifecycleQueryInstalledChaincodeRequest =
                    hfClient.newLifecycleQueryInstalledChaincodeRequest();
            lifecycleQueryInstalledChaincodeRequest.setPackageID(packageId);
            Collection<LifecycleQueryInstalledChaincodeProposalResponse> responses =
                    hfClient.sendLifecycleQueryInstalledChaincode(
                            lifecycleQueryInstalledChaincodeRequest, peers);

            if (peers.size() != responses.size()) {
                throw new Exception(
                        String.format(
                                "responses %d not same as peers %d.",
                                responses.size(), peers.size()));
            }

            boolean found = false;
            for (LifecycleQueryInstalledChaincodeProposalResponse response : responses) {

                String peerName = response.getPeer().getName();

                if (response.getStatus().equals(ChaincodeResponse.Status.SUCCESS)) {
                    if (chaincodeLabel.equals(response.getLabel())) {
                        logger.info(
                                "[√] Peer {} returned back same label: {}",
                                peerName,
                                response.getLabel());
                        found = true;
                    } else {
                        logger.info(
                                "[?] Peer {} returned back different label: {}",
                                peerName,
                                response.getLabel());
                    }
                } else {
                    logger.error(
                            "[X] Peer {} returned back bad status code: {}",
                            peerName,
                            response.getStatus());
                }
            }

            return found;

        } catch (Exception e1) {
            e1.printStackTrace();
            throw e1;
        }
    }
}
