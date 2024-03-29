package com.webank.wecross.stub.fabric2;

import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.fabric2.common.FabricType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.hyperledger.fabric.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeResourceManager {
    private static Logger logger = LoggerFactory.getLogger(ChaincodeResourceManager.class);

    private static final long updateChaincodeMapExpires = 10000; // ms
    private long lastChaincodeMapUpdateTime = 0;

    public interface EventHandler {
        void onChange(List<ResourceInfo> resourceInfos);
    }

    private HFClient hfClient;
    private Channel channel;
    private Map<String, Peer> peersMap;
    private String proxyChaincodeName;
    private Map<String, ChaincodeResource> chaincodeMap = new HashMap<>();
    private Timer mainloopTimer;
    private EventHandler eventHandler;

    public ChaincodeResourceManager(
            HFClient hfClient,
            Channel channel,
            Map<String, Peer> peersMap,
            String proxyChaincodeName) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.peersMap = peersMap;
        this.proxyChaincodeName = proxyChaincodeName;
    }

    public void start() {
        mainloopTimer = new Timer("ChaincodeResourceManager");

        updateChaincodeMap(); // update once at start

        mainloopTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        updateChaincodeMap();
                    }
                },
                updateChaincodeMapExpires,
                updateChaincodeMapExpires);
    }

    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public ChaincodeResource getChaincodeResource(String name) {
        return chaincodeMap.get(name);
    }

    public List<ResourceInfo> getResourceInfoList(boolean ignoreProxyChaincode) {
        List<ResourceInfo> resourceInfoList = new LinkedList<>();
        for (ChaincodeResource chaincodeResource : chaincodeMap.values()) {
            ResourceInfo resourceInfo = chaincodeResource.getResourceInfo();

            if (ignoreProxyChaincode && resourceInfo.getName().equals(proxyChaincodeName)) {
                continue; // Ignore WeCrossProxy chaincode
            }

            resourceInfo
                    .getProperties()
                    .put(FabricType.ResourceInfoProperty.CHANNEL_NAME, channel.getName());
            resourceInfoList.add(resourceInfo);
        }
        return resourceInfoList;
    }

    public Map<String, ChaincodeResource> getChaincodeMap() {
        return chaincodeMap;
    }

    private Map<String, ChaincodeResource> queryChaincodeMap() {
        Map<String, String> chaincode2Version = queryActiveChaincode();
        Map<String, ChaincodeResource> currentChaincodeMap = new HashMap<>();
        for (String chaincodeName : chaincode2Version.keySet()) {
            for (Peer peer : peersMap.values()) {
                if (isChaincodeActiveInPeer(peer, chaincodeName)) {
                    if (currentChaincodeMap.get(chaincodeName) == null) {
                        currentChaincodeMap.put(
                                chaincodeName,
                                new ChaincodeResource(
                                        chaincodeName,
                                        chaincodeName,
                                        chaincode2Version.get(chaincodeName),
                                        channel.getName()));
                    }
                    currentChaincodeMap.get(chaincodeName).addEndorser(peer);
                }
            }
        }
        return currentChaincodeMap;
    }

    private boolean isChaincodeActiveInPeer(Peer peer, String chaincodeName) {
        TransactionProposalRequest transactionProposalRequest =
                hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setFcn("wecross_test_chaincode_active_probe");
        transactionProposalRequest.setChaincodeID(
                ChaincodeID.newBuilder().setName(chaincodeName).build());

        Collection<Peer> peers = new HashSet<>();
        peers.add(peer);
        try {
            // sendTransactionProposal may wake up docker container if the chaincode has been
            // instantiated
            Collection<ProposalResponse> responses =
                    channel.sendTransactionProposal(transactionProposalRequest, peers);
            return isChaincodeActive(responses);

        } catch (Exception e) {
            logger.debug(
                    "isChaincodeActiveInPeer peer:{}, doesn't have chaincode:{} expcetion:{}",
                    peer,
                    chaincodeName,
                    e);
            return false;
        }
    }

    private boolean isChaincodeActive(Collection<ProposalResponse> responses) {
        // cannot retrieve package for chaincode
        boolean isActive = true;
        for (ProposalResponse response : responses) {

            if (!response.getStatus().equals(ChaincodeResponse.Status.SUCCESS)) {

                if (response.getMessage().contains("cannot retrieve package for chaincode")) {
                    // chaincode not exist (just for Fabric 1.4)
                    isActive &= false;
                }

                if (response.getMessage().contains("could not get chaincode code")) {
                    // chaincode uninstalled (just for Fabric 1.4)
                    isActive &= false;
                }
            }
        }
        return isActive;
    }

    private Map<String, String> queryActiveChaincode() {
        Map<String, String> name2Version = new HashMap<>();

        Collection<Peer> peers = new HashSet<>();

        // Only query peers with same mspID as for orderer
        String mspID =
                channel.getOrderers()
                        .stream()
                        .findFirst()
                        .get()
                        .getProperties()
                        .getProperty(FabricType.ORG_MSP_DEF);
        for (Peer peer : peersMap.values()) {
            String mspIDOfPeer = peer.getProperties().getProperty(FabricType.ORG_MSP_DEF);
            if (mspID != null && mspID.equals(mspIDOfPeer)) {
                peers.add(peer);
            }
        }

        try {
            LifecycleQueryInstalledChaincodesRequest request =
                    hfClient.newLifecycleQueryInstalledChaincodesRequest();
            QueryLifecycleQueryChaincodeDefinitionRequest
                    queryLifecycleQueryChaincodeDefinitionRequest =
                            hfClient.newQueryLifecycleQueryChaincodeDefinitionRequest();
            Collection<LifecycleQueryInstalledChaincodesProposalResponse> responses =
                    hfClient.sendLifecycleQueryInstalledChaincodes(request, peers);
            for (LifecycleQueryInstalledChaincodesProposalResponse respons : responses) {
                Collection<
                                LifecycleQueryInstalledChaincodesProposalResponse
                                        .LifecycleQueryInstalledChaincodesResult>
                        results = respons.getLifecycleQueryInstalledChaincodesResult();
                for (LifecycleQueryInstalledChaincodesProposalResponse
                                .LifecycleQueryInstalledChaincodesResult
                        result : results) {
                    String label = result.getLabel();
                    String chaincodeName = getChaincodeNameFromLabel(label);
                    queryLifecycleQueryChaincodeDefinitionRequest.setChaincodeName(chaincodeName);
                    Collection<LifecycleQueryChaincodeDefinitionProposalResponse> responses2 =
                            channel.lifecycleQueryChaincodeDefinition(
                                    queryLifecycleQueryChaincodeDefinitionRequest, peers);
                    if (responses2 == null || responses2.isEmpty()) {
                        continue;
                    }
                    for (LifecycleQueryChaincodeDefinitionProposalResponse respons2 : responses2) {
                        if (respons2 == null
                                || !ChaincodeResponse.Status.SUCCESS.equals(respons2.getStatus())) {
                            continue;
                        }
                        String version = respons2.getVersion();
                        name2Version.put(chaincodeName, version);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Could not get instantiated Chaincodes from:{} ", peers.toString());
        }
        /* for (Peer peer : peersMap.values()) {
            try {

                List<Query.ChaincodeInfo> chaincodeInfos = channel.queryInstantiatedChaincodes(peer);
                System.out.println("chaincodeInfos ----->: " + chaincodeInfos.toString());
                chaincodeInfos.forEach(
                        chaincodeInfo ->
                                name2Version.put(
                                        chaincodeInfo.getName(), chaincodeInfo.getVersion()));
            } catch (Exception e) {
                logger.warn("Could not get instantiated Chaincodes from:{} ", peer.toString());
            }
        }*/
        /*
                for (Peer peer : peersMap.values()) {
                    try {
                        List<Query.ChaincodeInfo> chaincodeInfos = hfClient.queryInstalledChaincodes(peer);
                        chaincodeInfos.forEach(
                                chaincodeInfo -> name2Version.add(chaincodeInfo.getName()));
                    } catch (Exception e) {
                        logger.warn("Could not get installed Chaincodes from:{} ", peer.toString());
                    }
                }
        */
        logger.debug("queryActiveChaincode: " + name2Version.toString());
        return name2Version;
    }

    private String getChaincodeNameFromLabel(String label) {
        // label: my_sacc_1122.2134
        // name: sacc

        int last = label.lastIndexOf("_");
        return label.substring(0, last);
    }

    public void dumpChaincodeMap() {
        String output = "Chaincode Resources: ";
        for (Map.Entry<String, ChaincodeResource> entry : chaincodeMap.entrySet()) {
            output += "Name:" + entry.getKey() + " Resource:" + entry.getValue().toString() + "\n";
        }
        logger.debug(output);
    }

    public void updateChaincodeMap() {
        synchronized (this) {
            if (needUpdateChaincodeMap()) {

                Map<String, ChaincodeResource> oldMap = this.chaincodeMap;
                this.chaincodeMap = queryChaincodeMap();

                if (eventHandler != null && !isSameChaincodeMap(oldMap, this.chaincodeMap)) {
                    logger.info(
                            "Chaincode resource has changed to: {}", this.chaincodeMap.keySet());
                    eventHandler.onChange(getResourceInfoList(false));
                }
                lastChaincodeMapUpdateTime = System.currentTimeMillis();
            }
            dumpChaincodeMap();
        }
    }

    private boolean needUpdateChaincodeMap() {
        return System.currentTimeMillis() - lastChaincodeMapUpdateTime > 1000;
    }

    private boolean isSameChaincodeMap(
            Map<String, ChaincodeResource> mp1, Map<String, ChaincodeResource> mp2) {
        if (!mp1.keySet().equals(mp2.keySet())) {
            return false;
        }

        for (String name : mp1.keySet()) {
            if (!mp1.get(name).getVersion().equals(mp2.get(name).getVersion())) {
                return false;
            }
        }
        return true;
    }
}
