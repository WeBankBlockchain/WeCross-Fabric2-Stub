package com.webank.wecross.stub.fabric.proxy;

import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.PackageChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.UpgradeChaincodeRequest;
import com.webank.wecross.stub.fabric.chaincode.ChaincodeHandler;
import com.webank.wecross.utils.TarUtils;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.fabric.FabricStubConfigParser;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyChaincodeDeployment {

    private static NetworkConfig networkConfig;
    private static LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy ;
    private static ChaincodeCollectionConfiguration ccCollectionConfiguration ;
    static {
        try {
            networkConfig = NetworkConfig.fromYamlFile(new File("conf/fabric-sdk" + File.separator + "network_config.yaml"));
            lccEndorsementPolicy = LifecycleChaincodeEndorsementPolicy.fromSignaturePolicyYamlFile(
                    Paths.get("conf/fabric-sdk" + File.separator + "chaincode-endorsement_policy.yaml"));
            ccCollectionConfiguration = ChaincodeCollectionConfiguration.fromYamlFile(
                    new File("conf/fabric-sdk" + File.separator + "collection-config.yaml"));
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
        //newConnection 创建并启动成功
        FabricConnection connection = (FabricConnection) fabricStubFactory.newConnection(stubPath);

        if (connection != null) {
            System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
        }
    }
/**
 * @Description: 部署代理合约
 * @params: [chainPath]
 * @return: void
 * @Author: mirsu
 * @Date: 2020/10/30 11:00
 **/
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
//            BlockHeaderManager blockHeaderManager = new DirectBlockHeaderManager(driver, connection);
//            List<String> orgNames = new LinkedList<>();
//            String adminName = configFile.getFabricServices().getOrgUserName();
            //admin 账户
//            Account admin = fabricStubFactory.newAccount( adminName, "classpath:accounts" + File.separator + adminName);
            //获取代理合约名称
            String chaincodeName = configFile.getAdvanced().getProxyChaincode();

            //通道名称
            String channelName = configFile.getFabricServices().getChannelName();

            long sequence = 1L;
            String sourcePath = "conf" + File.separator + chainPath + File.separator + chaincodeName;
            System.out.println("sourcePath------->" + sourcePath);
            //打包
            String chaincodeLabel = chaincodeName + "_" + version;
            LifecycleChaincodePackage chaincodePackage = packageChaincode(sourcePath, chaincodeName,chaincodeLabel);
            Collection<NetworkConfig.OrgInfo> organizationInfos = networkConfig.getOrganizationInfos();
            Iterator<NetworkConfig.OrgInfo> iterator = organizationInfos.iterator();
            String orgName;
            Channel channel= null;
            HFClient hfClient= null;
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
                String packageId = ChaincodeHandler.installChaincode(hfClient, channel, peers, chaincodePackage);
                boolean queryInstalled = ChaincodeHandler.queryInstalled(hfClient, peers, packageId, chaincodeLabel);
                System.out.println("install chaincode to " + orgName + " end, packageId = " + packageId + ", result = " + queryInstalled);

                System.out.println("approve chaincode to " + orgName + "  ...");
                CompletableFuture<BlockEvent.TransactionEvent> future = ChaincodeHandler.approveForMyOrg(hfClient, channel, peers, sequence, chaincodeName, version, lccEndorsementPolicy, ccCollectionConfiguration, true, packageId);
                BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
                System.out.println("approve chaincode to " + orgName + " end, result = " + event.isValid());
            }
            for (NetworkConfig.OrgInfo orgInfo : organizationInfos) {
                orgName =orgInfo.getName();
                hfClient = getTheClient(orgName);
                channel = constructChannel(hfClient, channelName);
                Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);

                System.out.println("commit chaincode to " + orgName + "  ...");
                CompletableFuture<BlockEvent.TransactionEvent> future1 = ChaincodeHandler.commitChaincodeDefinition(hfClient, channel, sequence, chaincodeName, version, lccEndorsementPolicy, ccCollectionConfiguration, true, peers);
                BlockEvent.TransactionEvent event1 = future1.get(60, TimeUnit.SECONDS);
                boolean queryCommitted = ChaincodeHandler.queryCommitted(hfClient, channel, chaincodeName, peers, sequence, true);
                System.out.println("commit chaincode to " + orgName + " end, commitResult = " + event1.isValid() + ", queryCommitted = " + queryCommitted);

            }
            System.out.println("init chaincode to " + chaincodeName + "  ...");
            CompletableFuture<BlockEvent.TransactionEvent> future = ChaincodeHandler.initChaincode(hfClient, hfClient.getUserContext(), channel, true, chaincodeName, version, TransactionRequest.Type.GO_LANG, new String[]{channelName});
            BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
            System.out.println("init chaincode to " + chaincodeName + " end, result = " + event.isValid());

            System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
        }
    }

    private static HFClient getTheClient(String orgName) throws Exception {
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        client.setUserContext(networkConfig.getPeerAdmin(orgName));
        return client;

    }

    private static void initChaincode(String channelName, FabricAccount account, String chaincodeName, String version) throws Exception {
        String language = "GO_LANG";
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel =  hfClient.newChannel(channelName); // Cha
        String[] args = new String[] {channelName};
        boolean initRequired = true;
        BlockEvent.TransactionEvent initEvent =ChaincodeHandler.initChaincode(hfClient, account.getUser(), channel, initRequired, chaincodeName,
                        version, FabricType.stringTochainCodeType(language), args)
                        .get(60, TimeUnit.SECONDS);/**/
        System.out.println("commit chaincode to -> "  +
                " ,chaincodeName -> " + chaincodeName +
                " ,chaincodeVersion -> " + version +
                " ,args -> " + args +
                " , result -> " + initEvent.isValid() + "\n commit end");

    }

    private static void commitChaincodeDefinition(FabricAccount account, String channelName, String orgName, long sequence, String chaincodeName, String chaincodeVersion, LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy, ChaincodeCollectionConfiguration ccCollectionConfiguration) throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel =  hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        boolean initRequired = true;
        BlockEvent.TransactionEvent commitEvent =
                ChaincodeHandler.commitChaincodeDefinition(hfClient, channel, sequence, chaincodeName,
                        chaincodeVersion, lccEndorsementPolicy, ccCollectionConfiguration, initRequired, peers)
                        .get(60, TimeUnit.SECONDS);
        System.out.println("commit chaincode to -> " + orgName +
                " ,chaincodeName -> " + chaincodeName +
                " ,sequence -> " + sequence +
                " ,chaincodeVersion -> " + chaincodeVersion +
                " , result -> " + commitEvent.isValid() + "\n commit end");
        boolean checkResult1 = ChaincodeHandler.queryCommitted(hfClient, channel, chaincodeName, peers, sequence, initRequired);
        System.out.println("////////////////// check commit result ///////////////" + checkResult1);



    }

    private static LifecycleChaincodePackage packageChaincode(String sourcePath, String chaincodeName,String chaincodeLabel) throws  Exception {
        System.out.println("Package " + chaincodeName + " ...");
        String language = "GO_LANG";
        PackageChaincodeRequest packageChaincodeRequest = PackageChaincodeRequest.build()
                .setChaincodeName(chaincodeName)
                .setChaincodeLabel(chaincodeLabel)
                .setChaincodeType(language)
                .setChaincodePath("github.com")
                .setChaincodeMetainfoPath(sourcePath)
                .setChaincodeSourcePath(sourcePath);
        LifecycleChaincodePackage lifecycleChaincodePackage = ChaincodeHandler.packageChaincode(packageChaincodeRequest);
        System.out.println("Package success!!! lifecycleChaincodePackage" + lifecycleChaincodePackage.getPath());
        return lifecycleChaincodePackage;
    }

    /**
     * @Description: 安装 go合约
     * @params: [orgName, connection, driver, user, blockHeaderManager, chaincodeName, code, version]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 11:32
     **/
    @Deprecated
    public static void install(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
            byte[] code,
            String version)
            throws Exception {
        System.out.println("Install " + chaincodeName + ":" + version + " to " + orgName + " ...");

        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        InstallChaincodeRequest installChaincodeRequest =
                InstallChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setCode(code);

        TransactionContext<InstallChaincodeRequest> installRequest =
                new TransactionContext<InstallChaincodeRequest>(
                        installChaincodeRequest, user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future1 = new CompletableFuture<>();
        ChaincodeHandler.asyncInstallChaincode(
                        installRequest,
                        connection,
                        (transactionException, transactionResponse) -> future1.complete(transactionException));

        TransactionException e1 = future1.get(80, TimeUnit.SECONDS);
        if (!e1.isSuccess()) {
            System.out.println("WARNING: asyncCustomCommand install: " + e1.getMessage());
        }
    }


    private static void approveForMyOrg(FabricAccount account, Channel channel, String chaincodeName, String orgName, String version, long sequence, String packageId, LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy, ChaincodeCollectionConfiguration ccCollectionConfiguration) throws Exception {
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        System.out.println("account：----》" + account.getUser());
//        Channel channel =  hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        boolean initRequired = true;
        CompletableFuture<BlockEvent.TransactionEvent> future = ChaincodeHandler.approveForMyOrg(hfClient, channel, peers, sequence, chaincodeName,
                version, lccEndorsementPolicy, ccCollectionConfiguration, initRequired, packageId);
        BlockEvent.TransactionEvent event = future.get(60, TimeUnit.SECONDS);
        System.out.println("approve chaincode to -> " + orgName + ", result -> " + event.isValid() + "\n approve end");

    }

    private static String installChaincode(FabricAccount account, LifecycleChaincodePackage chaincodePackage, String channelName, String orgName) throws Exception {
        System.out.println("Install chaincode " + chaincodePackage.getLabel() + " to " + orgName + " ...");
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        System.out.println("account.getUser()   " + account.getUser().getAccount());
        System.out.println("account.getMspId()   " + account.getUser().getMspId());
        System.out.println("account.getName()   " + account.getUser().getName());
        System.out.println("account.getRoles()   " + account.getUser().getRoles());
        System.out.println("account.getEnrollment().getCert   :" + account.getUser().getEnrollment().getCert());
        Channel channel = constructChannel(hfClient, channelName);
//        Channel channel =  hfClient.newChannel(channelName); // ChannelName
        Collection<Peer> peers = ChaincodeHandler.extractPeersFromChannel(channel, orgName);
        String packageId = ChaincodeHandler.installChaincode(hfClient, channel, peers, chaincodePackage);
        System.out.println("install chaincode to -> " + orgName + ", packageId -> " + packageId + "\n install end");
        return packageId;
    }

    private static Channel constructChannel(HFClient hfClient, String channelName) throws Exception {
        Channel newChannel = hfClient.loadChannelFromConfig(channelName, networkConfig);
        if (newChannel == null) {
            throw new Exception("Channel " + channelName + " is not defined in the config file!");
        }

        return newChannel.initialize();
    }

    public static void upgrade(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockHeaderManager blockHeaderManager = new DirectBlockHeaderManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);
        String chaincodeName = configFile.getAdvanced().getProxyChaincode();

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            String chaincodeFilesDir =
                    "classpath:" + chainPath + File.separator + chaincodeName + File.separator;
            byte[] code =
                    TarUtils.generateTarGzInputStreamBytesFoGoChaincode(
                            chaincodeFilesDir); // Proxy is go
            install(
                    orgName,
                    connection,
                    driver,
                    orgAdmin,
                    blockHeaderManager,
                    chaincodeName,
                    code,
                    version);
        }
        upgrade(orgNames, connection, driver, admin, blockHeaderManager, chaincodeName, version);
        System.out.println("SUCCESS: " + chaincodeName + " has been upgraded to " + chainPath);
    }
    /**
     * @Description:实例化合约
     * @params: [orgNames, connection, driver, user, blockHeaderManager, chaincodeName, version]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 17:22
     **/
    public static void instantiate(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
            String version)
            throws Exception {
        System.out.println(
                "Instantiating "
                        + chaincodeName
                        + ":"
                        + version
                        + " to "
                        + orgNames.toString()
                        + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        String[] args = new String[] {channelName};

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        // .setTransientMap()
                        .setArgs(args);
        TransactionContext<InstantiateChaincodeRequest> instantiateRequest =
                new TransactionContext<InstantiateChaincodeRequest>(
                        instantiateChaincodeRequest, user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ChaincodeHandler.asyncInstantiateChaincode(
                        instantiateRequest,
                        connection,
                        (transactionException, transactionResponse) -> future2.complete(transactionException));

//        TransactionException e2 = future2.get(50, TimeUnit.SECONDS);
        //不设置等待时间
        TransactionException e2 = future2.get();
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand instantiate error: " + e2.getMessage());
        }
    }
    /**
     * @Description: 升级合约
     * @params: [orgNames, connection, driver, user, blockHeaderManager, chaincodeName, version]
     * @return: void
     * @Author: mirsu
     * @Date: 2020/10/30 17:22
     **/
    public static void upgrade(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockHeaderManager blockHeaderManager,
            String chaincodeName,
            String version)
            throws Exception {
        System.out.println(
                "Upgrade " + chaincodeName + ":" + version + " to " + orgNames.toString() + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        String[] args = new String[] {channelName};

        UpgradeChaincodeRequest upgradeChaincodeRequest =
                UpgradeChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        // .setTransientMap()
                        .setArgs(args);
        TransactionContext<UpgradeChaincodeRequest> instantiateRequest =
                new TransactionContext<UpgradeChaincodeRequest>(
                        upgradeChaincodeRequest, user, null, null, blockHeaderManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ChaincodeHandler.asyncUpgradeChaincode(
                        instantiateRequest,
                        connection,
                        (transactionException, transactionResponse) -> future2.complete(transactionException));

//        TransactionException e2 = future2.get(50, TimeUnit.SECONDS);
        TransactionException e2 = future2.get();
        if (!e2.isSuccess()) {
            e2.printStackTrace();
            throw new Exception("ERROR: asyncCustomCommand upgrade error: " + e2.getMessage());
        }
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
                //校验
                check(chainPath);
                break;
            case "deploy":
                //部署
                deploy(chainPath);
                break;
            case "upgrade":
                //升级
                upgrade(chainPath);
                break;
            default:
                usage();
        }
    }
    /**
     * @Description: driver 与 connection 的组合类
     * @Author: mirsu
     * @Date: 2020/10/30 11:20
     **/
    public static class DirectBlockHeaderManager implements BlockHeaderManager {
        private Driver driver;
        private Connection connection;

        public DirectBlockHeaderManager(Driver driver, Connection connection) {
            this.driver = driver;
            this.connection = connection;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

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
        public void asyncGetBlockHeader(long blockNumber, GetBlockHeaderCallback callback) {
            driver.asyncGetBlockHeader(
                    blockNumber,
                    connection,
                    new Driver.GetBlockHeaderCallback() {
                        @Override
                        public void onResponse(Exception e, byte[] blockHeader) {
                            callback.onResponse(e, blockHeader);
                        }
                    });
        }
    }
}
