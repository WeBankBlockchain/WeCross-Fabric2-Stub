package com.webank.wecross.stub.fabric2;

import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric2.account.FabricAccountFactory;
import com.webank.wecross.stub.fabric2.common.FabricType;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class FabricConnectionFactory {
    private static Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);

    /**
     * @Description: 构建fabric链接
     *
     * @params: [path]
     * @return: com.webank.wecross.stub.fabric2.FabricConnection @Author: mirsu @Date: 2020/10/30
     *     11:02
     */
    public static FabricConnection build(String path) {
        String stubPath = path;
        try {
            FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
            HFClient hfClient = buildClient(configFile);
            Map<String, Peer> peersMap = buildPeersMap(hfClient, configFile);
            Channel channel = buildChannel(hfClient, peersMap, configFile);
            ThreadPoolTaskExecutor threadPool = buildThreadPool(configFile);

            return new FabricConnection(
                    hfClient, channel, peersMap, StubConstant.PROXY_NAME, threadPool);

        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);
            logger.error("FabricConnection build exception: " + e);
            return null;
        }
    }

    public static Map<String, FabricConnection> buildOrgConnections(String path) {
        String stubPath = path;
        try {
            FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
            Map<String, FabricConnection> orgConnections = new HashMap<>();
            for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                    configFile.getOrgs().entrySet()) {

                HFClient hfClient = buildClient(orgEntry.getValue().getAdminName());
                Map<String, Peer> peersMap =
                        buildOrgPeersMap(hfClient, orgEntry.getKey(), orgEntry.getValue());
                Channel channel = buildChannel(hfClient, peersMap, configFile);
                ThreadPoolTaskExecutor threadPool = buildThreadPool(configFile);
                FabricConnection fabricConnection =
                        new FabricConnection(
                                hfClient, channel, peersMap, StubConstant.PROXY_NAME, threadPool);
                orgConnections.put(orgEntry.getKey(), fabricConnection);
            }

            return orgConnections;

        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(FabricConnectionFactory.class);
            logger.error("FabricConnection build exception: " + e);
            return null;
        }
    }

    private static ThreadPoolTaskExecutor buildThreadPool(FabricStubConfigParser configFile) {
        ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
        int corePoolSize = configFile.getAdvanced().getThreadPool().getCorePoolSize();
        int maxPoolSize = configFile.getAdvanced().getThreadPool().getMaxPoolSize();
        int queueCapacity = configFile.getAdvanced().getThreadPool().getQueueCapacity();
        threadPool.setCorePoolSize(corePoolSize);
        threadPool.setMaxPoolSize(maxPoolSize);
        threadPool.setQueueCapacity(queueCapacity);
        threadPool.setThreadNamePrefix("FabricConnection-");
        logger.info(
                "Init threadPool with corePoolSize:{}, maxPoolSize:{}, queueCapacity:{}",
                corePoolSize,
                maxPoolSize,
                queueCapacity);
        return threadPool;
    }

    public static HFClient buildClient(FabricStubConfigParser fabricStubConfigParser)
            throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        String orgUserName = fabricStubConfigParser.getFabricServices().getOrgUserName();
        User admin =
                FabricAccountFactory.buildUser(
                        orgUserName, "classpath:accounts" + File.separator + orgUserName);
        hfClient.setUserContext(admin);
        return hfClient;
    }

    public static HFClient buildClient(String orgUserName) throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        User admin =
                FabricAccountFactory.buildUser(
                        orgUserName, "classpath:accounts" + File.separator + orgUserName);
        hfClient.setUserContext(admin);
        return hfClient;
    }

    public static Map<String, Peer> buildPeersMap(
            HFClient client, FabricStubConfigParser fabricStubConfigParser) throws Exception {
        Map<String, Peer> peersMap = new LinkedHashMap<>();
        int index = 0;
        Map<String, FabricStubConfigParser.Orgs.Org> orgs = fabricStubConfigParser.getOrgs();

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry : orgs.entrySet()) {
            String orgName = orgEntry.getKey();
            FabricStubConfigParser.Orgs.Org org = orgEntry.getValue();

            String orgUserName = org.getAdminName();
            String mspID =
                    FabricAccountFactory.getMspID(
                            orgUserName, "classpath:accounts" + File.separator + orgUserName);

            for (String peerAddress : org.getEndorsers()) {
                String name = "peer-" + String.valueOf(index);
                peersMap.put(
                        name,
                        buildPeer(client, peerAddress, org.getTlsCaFile(), orgName, index, mspID));
                index++;
            }
        }

        return peersMap;
    }

    public static Map<String, Peer> buildOrgPeersMap(
            HFClient client, String orgName, FabricStubConfigParser.Orgs.Org org) throws Exception {
        Map<String, Peer> peersMap = new LinkedHashMap<>();
        int index = 0;

        String orgUserName = org.getAdminName();
        String mspID =
                FabricAccountFactory.getMspID(
                        orgUserName, "classpath:accounts" + File.separator + orgUserName);

        for (String peerAddress : org.getEndorsers()) {
            String name = "peer-" + String.valueOf(index);
            peersMap.put(
                    name,
                    buildPeer(client, peerAddress, org.getTlsCaFile(), orgName, index, mspID));
            index++;
        }
        return peersMap;
    }

    // Create Channel
    public static Channel buildChannel(
            HFClient client,
            Map<String, Peer> peersMap,
            FabricStubConfigParser fabricStubConfigParser)
            throws Exception {
        Channel channel =
                client.newChannel(fabricStubConfigParser.getFabricServices().getChannelName());

        String orgUserName = fabricStubConfigParser.getFabricServices().getOrgUserName();
        String mspID =
                FabricAccountFactory.getMspID(
                        orgUserName, "classpath:accounts" + File.separator + orgUserName);

        Orderer orderer = buildOrderer(client, fabricStubConfigParser, mspID);

        channel.addOrderer(orderer);

        for (Peer peer : peersMap.values()) {
            channel.addPeer(peer);
        }

        // channel.initialize(); not to start channel here
        return channel;
    }

    public static Orderer buildOrderer(
            HFClient client, FabricStubConfigParser fabricStubConfigParser, String mspID)
            throws InvalidArgumentException {
        Properties orderer1Prop = new Properties();
        orderer1Prop.setProperty(
                "pemFile", fabricStubConfigParser.getFabricServices().getOrdererTlsCaFile());
        // orderer1Prop.setProperty("sslProvider", "openSSL");
        orderer1Prop.setProperty("sslProvider", "JDK");
        orderer1Prop.setProperty("negotiationType", "TLS");
        orderer1Prop.setProperty("ordererWaitTimeMilliSecs", "300000");
        orderer1Prop.setProperty("hostnameOverride", "orderer");
        orderer1Prop.setProperty("trustServerCertificate", "true");
        orderer1Prop.setProperty("allowAllHostNames", "true");
        orderer1Prop.setProperty(
                FabricType.ORG_MSP_DEF, mspID); // ORG_NAME_DEF is only used by wecross
        Orderer orderer =
                client.newOrderer(
                        "orderer",
                        fabricStubConfigParser.getFabricServices().getOrdererAddress(),
                        orderer1Prop);
        return orderer;
    }

    public static Peer buildPeer(
            HFClient client,
            String address,
            String tlsCaFile,
            String orgName,
            Integer index,
            String mspID)
            throws InvalidArgumentException {
        Properties peer0Prop = new Properties();
        peer0Prop.setProperty("pemFile", tlsCaFile);
        // peer0Prop.setProperty("sslProvider", "openSSL");
        peer0Prop.setProperty("sslProvider", "JDK");
        peer0Prop.setProperty("negotiationType", "TLS");
        peer0Prop.setProperty("hostnameOverride", "peer0");
        peer0Prop.setProperty("trustServerCertificate", "true");
        peer0Prop.setProperty("allowAllHostNames", "true");
        peer0Prop.setProperty(
                FabricType.ORG_NAME_DEF, orgName); // ORG_NAME_DEF is only used by wecross
        peer0Prop.setProperty(
                FabricType.ORG_MSP_DEF, mspID); // ORG_NAME_DEF is only used by wecross
        Peer peer = client.newPeer("peer" + index, address, peer0Prop);
        return peer;
    }
}
