package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import com.webank.wecross.stub.fabric.FabricCustomCommand.PackageChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricStubConfigParser;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import com.webank.wecross.stub.fabric.SystemChaincodeUtility;
import com.webank.wecross.stub.fabric.chaincode.ChaincodeHandler;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class ProxyChaincodeDeployment {

    private static NetworkConfig networkConfig;
    private static LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy;
    private static ChaincodeCollectionConfiguration ccCollectionConfiguration;

    static {
        try {
            networkConfig =
                    NetworkConfig.fromYamlFile(
                            new File("conf/fabric-sdk" + File.separator + "network_config.yaml"));
            lccEndorsementPolicy =
                    LifecycleChaincodeEndorsementPolicy.fromSignaturePolicyYamlFile(
                            Paths.get(
                                    "conf/fabric-sdk"
                                            + File.separator
                                            + "chaincode-endorsement_policy.yaml"));
            ccCollectionConfiguration =
                    ChaincodeCollectionConfiguration.fromYamlFile(
                            new File(
                                    "conf/fabric-sdk" + File.separator + "collection-config.yaml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void usage() {
        System.out.println(getUsage("chains/fabric"));
        exit();
    }

    public static String getUsage(String chainPath) {
        String pureChainPath = chainPath.replace("classpath:/", "").replace("classpath:", "");
        return "Usage:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " check [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy [chainName]\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade [chainName]\n"
                + "Example:\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " check "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " deploy "
                + pureChainPath
                + "\n"
                + "         java -cp 'conf/:lib/*:plugin/*' "
                + ProxyChaincodeDeployment.class.getName()
                + " upgrade "
                + pureChainPath
                + "";
    }

    private static void exit() {
        System.exit(0);
    }

    private static void exit(int sig) {
        System.exit(sig);
    }

    public static void check(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        // newConnection 创建并启动成功
        FabricConnection connection = (FabricConnection) fabricStubFactory.newConnection(stubPath);

        if (connection != null) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        }
    }
    /**
     * @Description: 部署代理合约
     *
     * @params: [chainPath]
     * @return: void @Author: mirsu @Date: 2020/10/30 11:00
     */
    public static void deploy(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();
        // Check proxy chaincode
        if (connection.hasProxyDeployed2AllPeers()) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        } else {
            FabricStubFactory fabricStubFactory = new FabricStubFactory();
            //            Driver driver = fabricStubFactory.newDriver();
            //            BlockHeaderManager blockHeaderManager = new
            // DirectBlockHeaderManager(driver, connection);
            //            List<String> orgNames = new LinkedList<>();
            //            String adminName = configFile.getFabricServices().getOrgUserName();
            // admin 账户
            //            Account admin = fabricStubFactory.newAccount( adminName,
            // "classpath:accounts" + File.separator + adminName);
            // 获取代理合约名称
            String chaincodeName = StubConstant.PROXY_NAME;

            // 通道名称
            String channelName = configFile.getFabricServices().getChannelName();

            long sequence = 1L;
            String sourcePath =
                    "conf" + File.separator + chainPath + File.separator + chaincodeName;
            System.out.println("sourcePath------->" + sourcePath);
            // 打包
            String chaincodeLabel = chaincodeName + "_" + version;
            LifecycleChaincodePackage chaincodePackage =
                    packageChaincode(sourcePath, chaincodeName, chaincodeLabel);
            Collection<NetworkConfig.OrgInfo> organizationInfos =
                    networkConfig.getOrganizationInfos();
            Iterator<NetworkConfig.OrgInfo> iterator = organizationInfos.iterator();
            String orgName;
            Channel channel = null;
            HFClient hfClient = null;
            for (NetworkConfig.OrgInfo orgInfo : organizationInfos) {
                System.out.println("orgInfo   = " + orgInfo);
                orgName = orgInfo.getName();
                System.out.println("orgName   = " + orgName);
                hfClient = getTheClient(orgName);
                System.out.println("hfClient   = " + hfClient.toString());
                channel = constructChannel(hfClient, channelName);
                System.out.println("channel   = " + channel.toString());
                Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
                System.out.println("peers   = " + peers.toString());

                System.out.println("install chaincode to " + orgName + "  ...");
                String packageId =
                        ChaincodeHandler.installChaincode(
                                hfClient, channel, peers, chaincodePackage);
                boolean queryInstalled =
                        ChaincodeHandler.queryInstalled(hfClient, peers, packageId, chaincodeLabel);
                System.out.println(
                        "install chaincode to "
                                + orgName
                                + " end, packageId = "
                                + packageId
                                + ", result = "
                                + queryInstalled);

                System.out.println("approve chaincode to " + orgName + "  ...");
                CompletableFuture<BlockEvent.TransactionEvent> future =
                        ChaincodeHandler.approveForMyOrg(
                                hfClient,
                                channel,
                                peers,
                                sequence,
                                chaincodeName,
                                version,
                                lccEndorsementPolicy,
                                ccCollectionConfiguration,
                                true,
                                packageId);
                BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
                System.out.println(
                        "approve chaincode to " + orgName + " end, result = " + event.isValid());
            }
            for (NetworkConfig.OrgInfo orgInfo : organizationInfos) {
                orgName = orgInfo.getName();
                hfClient = getTheClient(orgName);
                channel = constructChannel(hfClient, channelName);
                Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);

                System.out.println("commit chaincode to " + orgName + "  ...");
                CompletableFuture<BlockEvent.TransactionEvent> future1 =
                        ChaincodeHandler.commitChaincodeDefinition(
                                hfClient,
                                channel,
                                sequence,
                                chaincodeName,
                                version,
                                lccEndorsementPolicy,
                                ccCollectionConfiguration,
                                true,
                                peers);
                BlockEvent.TransactionEvent event1 = future1.get(60, TimeUnit.SECONDS);
                boolean queryCommitted =
                        ChaincodeHandler.queryCommitted(
                                hfClient, channel, chaincodeName, peers, sequence, true);
                System.out.println(
                        "commit chaincode to "
                                + orgName
                                + " end, commitResult = "
                                + event1.isValid()
                                + ", queryCommitted = "
                                + queryCommitted);
            }
            System.out.println("init chaincode to " + chaincodeName + "  ...");
            CompletableFuture<BlockEvent.TransactionEvent> future =
                    ChaincodeHandler.initChaincode(
                            hfClient,
                            hfClient.getUserContext(),
                            channel,
                            true,
                            chaincodeName,
                            version,
                            TransactionRequest.Type.GO_LANG,
                            new String[] {channelName});
            BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
            System.out.println(
                    "init chaincode to " + chaincodeName + " end, result = " + event.isValid());

            System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
        }
    }

    private static HFClient getTheClient(String orgName) throws Exception {
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        client.setUserContext(networkConfig.getPeerAdmin(orgName));
        return client;
    }

    private static void initChaincode(
            String channelName, FabricAccount account, String chaincodeName, String version)
            throws Exception {
        String language = "GO_LANG";
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel = hfClient.newChannel(channelName); // Cha
        String[] args = new String[] {channelName};
        boolean initRequired = true;
        BlockEvent.TransactionEvent initEvent =
                ChaincodeHandler.initChaincode(
                                hfClient,
                                account.getUser(),
                                channel,
                                initRequired,
                                chaincodeName,
                                version,
                                FabricType.stringTochainCodeType(language),
                                args)
                        .get(60, TimeUnit.SECONDS); /**/
        System.out.println(
                "commit chaincode to -> "
                        + " ,chaincodeName -> "
                        + chaincodeName
                        + " ,chaincodeVersion -> "
                        + version
                        + " ,args -> "
                        + args
                        + " , result -> "
                        + initEvent.isValid()
                        + "\n commit end");
    }

    private static void commitChaincodeDefinition(
            FabricAccount account,
            String channelName,
            String orgName,
            long sequence,
            String chaincodeName,
            String chaincodeVersion,
            LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy,
            ChaincodeCollectionConfiguration ccCollectionConfiguration)
            throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel = hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        boolean initRequired = true;
        BlockEvent.TransactionEvent commitEvent =
                ChaincodeHandler.commitChaincodeDefinition(
                                hfClient,
                                channel,
                                sequence,
                                chaincodeName,
                                chaincodeVersion,
                                lccEndorsementPolicy,
                                ccCollectionConfiguration,
                                initRequired,
                                peers)
                        .get(60, TimeUnit.SECONDS);
        System.out.println(
                "commit chaincode to -> "
                        + orgName
                        + " ,chaincodeName -> "
                        + chaincodeName
                        + " ,sequence -> "
                        + sequence
                        + " ,chaincodeVersion -> "
                        + chaincodeVersion
                        + " , result -> "
                        + commitEvent.isValid()
                        + "\n commit end");
        boolean checkResult1 =
                ChaincodeHandler.queryCommitted(
                        hfClient, channel, chaincodeName, peers, sequence, initRequired);
        System.out.println("////////////////// check commit result ///////////////" + checkResult1);
    }

    private static LifecycleChaincodePackage packageChaincode(
            String sourcePath, String chaincodeName, String chaincodeLabel) throws Exception {
        System.out.println("Package " + chaincodeName + " ...");
        String language = "GO_LANG";
        PackageChaincodeRequest packageChaincodeRequest =
                PackageChaincodeRequest.build()
                        .setChaincodeName(chaincodeName)
                        .setChaincodeLabel(chaincodeLabel)
                        .setChaincodeType(language)
                        .setChaincodePath("github.com")
                        .setChaincodeMetainfoPath(sourcePath)
                        .setChaincodeSourcePath(sourcePath);
        LifecycleChaincodePackage lifecycleChaincodePackage =
                ChaincodeHandler.packageChaincode(packageChaincodeRequest);
        System.out.println(
                "Package success!!! lifecycleChaincodePackage"
                        + lifecycleChaincodePackage.getPath());
        return lifecycleChaincodePackage;
    }

    private static void approveForMyOrg(
            FabricAccount account,
            Channel channel,
            String chaincodeName,
            String orgName,
            String version,
            long sequence,
            String packageId,
            LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy,
            ChaincodeCollectionConfiguration ccCollectionConfiguration)
            throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        System.out.println("account：----》" + account.getUser());
        //        Channel channel =  hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        boolean initRequired = true;
        CompletableFuture<BlockEvent.TransactionEvent> future =
                ChaincodeHandler.approveForMyOrg(
                        hfClient,
                        channel,
                        peers,
                        sequence,
                        chaincodeName,
                        version,
                        lccEndorsementPolicy,
                        ccCollectionConfiguration,
                        initRequired,
                        packageId);
        BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
        System.out.println(
                "approve chaincode to -> "
                        + orgName
                        + ", result -> "
                        + event.isValid()
                        + "\n approve end");
    }

    private static String installChaincode(
            FabricAccount account,
            LifecycleChaincodePackage chaincodePackage,
            String channelName,
            String orgName)
            throws Exception {
        System.out.println(
                "Install chaincode " + chaincodePackage.getLabel() + " to " + orgName + " ...");
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        System.out.println("account.getUser()   " + account.getUser().getAccount());
        System.out.println("account.getMspId()   " + account.getUser().getMspId());
        System.out.println("account.getName()   " + account.getUser().getName());
        System.out.println("account.getRoles()   " + account.getUser().getRoles());
        System.out.println(
                "account.getEnrollment().getCert   :"
                        + account.getUser().getEnrollment().getCert());
        Channel channel = constructChannel(hfClient, channelName);
        //        Channel channel =  hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        String packageId =
                ChaincodeHandler.installChaincode(hfClient, channel, peers, chaincodePackage);
        System.out.println(
                "install chaincode to -> "
                        + orgName
                        + ", packageId -> "
                        + packageId
                        + "\n install end");
        return packageId;
    }

    private static Channel constructChannel(HFClient hfClient, String channelName)
            throws Exception {
        Channel newChannel = hfClient.loadChannelFromConfig(channelName, networkConfig);
        if (newChannel == null) {
            throw new Exception("Channel " + channelName + " is not defined in the config file!");
        }

        return newChannel.initialize();
    }

    public static void upgrade(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.upgrade(chainPath, StubConstant.PROXY_NAME, args);
    }

    public static boolean hasInstantiate(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        Set<String> orgNames = configFile.getOrgs().keySet();
        Set<String> chainOrgNames = connection.getProxyOrgNames(true);

        orgNames.removeAll(chainOrgNames);
        return orgNames.isEmpty();
    }

    public static void main(String[] args) throws Exception {
        try {
            switch (args.length) {
                case 2:
                    handle2Args(args);
                    break;
                default:
                    usage();
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            exit();
        }
    }

    public static void handle2Args(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
        }

        String cmd = args[0];
        String chainPath = args[1];

        switch (cmd) {
            case "check":
                // 校验
                check(chainPath);
                break;
            case "deploy":
                // 部署
                deploy(chainPath);
                break;
            case "upgrade":
                // 升级
                upgrade(chainPath);
                break;
            default:
                usage();
        }
    }
    /** @Description: driver 与 connection 的组合类 @Author: mirsu @Date: 2020/10/30 11:20 */
    public static class DirectBlockHeaderManager implements BlockManager {
        private Driver driver;
        private Connection connection;

        public DirectBlockHeaderManager(Driver driver, Connection connection) {
            this.driver = driver;
            this.connection = connection;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void asyncGetBlockNumber(GetBlockNumberCallback callback) {
            driver.asyncGetBlockNumber(
                    connection,
                    new Driver.GetBlockNumberCallback() {
                        @Override
                        public void onResponse(Exception e, long blockNumber) {
                            callback.onResponse(e, blockNumber);
                        }
                    });
        }

        @Override
        public void asyncGetBlock(long blockNumber, GetBlockCallback callback) {
            driver.asyncGetBlock(
                    blockNumber,
                    false,
                    connection,
                    new Driver.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            callback.onResponse(e, block);
                        }
                    });
        }
    }
}
