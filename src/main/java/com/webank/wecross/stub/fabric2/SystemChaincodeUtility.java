package com.webank.wecross.stub.fabric2;

import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.ApproveChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.CommitChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.PackageChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.QueryCommittedRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.UpgradeChaincodeRequest;
import com.webank.wecross.stub.fabric2.chaincode.ChaincodeHandler;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.hyperledger.fabric.sdk.LifecycleChaincodePackage;

public class SystemChaincodeUtility {
    public static final int Proxy = 0;
    public static final int Hub = 1;

    public static void deploy(String chainPath, int type, String chaincodeName, String[] args)
            throws Exception {
        deploy(chainPath, type, chaincodeName, args, false);
    }

    public static void upgrade(String chainPath, int type, String chaincodeName, String[] args)
            throws Exception {
        deploy(chainPath, type, chaincodeName, args, true);
    }

    private static void deploy(
            String chainPath,
            int type,
            String chaincodeName,
            String[] args,
            boolean ignoreHasDeployed)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();

        BlockManager blockManager = new DirectBlockManager(driver, connection);

        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);

        if (type == Proxy) {
            if (!ignoreHasDeployed && connection.hasProxyDeployed2AllPeers()) {
                System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
                return;
            }
        } else if (type == Hub) {
            if (!ignoreHasDeployed && connection.hasHubDeployed2AllPeers()) {
                System.out.println("SUCCESS: WeCrossHub has been deployed to all connected org");
                return;
            }

        } else {
            System.out.println("ERROR: type " + type + " not supported");
            return;
        }

        String packageId = null;
        long sequence =
                queryCommittedChaincodeSequenceByName(
                                connection, driver, admin, blockManager, chaincodeName)
                        + 1;

        // package
        String sourcePath =
                "conf"
                        + File.separator
                        + chainPath
                        + File.separator
                        + "chaincode"
                        + File.separator
                        + chaincodeName;
        System.out.println("sourcePath------->" + sourcePath);
        String chaincodeLabel = chaincodeName + "_" + version;
        LifecycleChaincodePackage chaincodePackage =
                packageChaincode(sourcePath, chaincodeName, chaincodeLabel, "GO_LANG");
        byte[] code = chaincodePackage.getAsBytes();

        Map<String, FabricConnection> orgConnections =
                FabricConnectionFactory.buildOrgConnections(stubPath);
        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            String accountName = orgEntry.getValue().getAdminName();

            FabricConnection orgConnection = orgConnections.get(orgName);
            orgConnection.start();

            Driver orgDriver = fabricStubFactory.newDriver();
            BlockManager orgBlockManager = new DirectBlockManager(orgDriver, orgConnection);

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            // install
            String currentPackageId =
                    install(
                            orgName,
                            orgConnection,
                            orgDriver,
                            orgAdmin,
                            orgBlockManager,
                            chaincodeName,
                            code,
                            version);

            if (packageId != null && !packageId.equals(currentPackageId)) {
                System.out.println("PackageIds are not the same in different org:" + orgName);
            }
            packageId = currentPackageId;

            // approve
            approveForMyOrg(
                    orgName,
                    orgConnection,
                    orgDriver,
                    orgAdmin,
                    orgBlockManager,
                    chaincodeName,
                    version,
                    sequence,
                    packageId);
        }

        List<String> orgNames = new LinkedList<>();

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
        }

        // commit
        commitChaincodeDefinition(
                orgNames,
                connection,
                driver,
                admin,
                blockManager,
                chaincodeName,
                version,
                sequence,
                packageId);

        // waiting for chaincode booting up
        int tryTimes = 0;
        if (type == Proxy) {
            System.out.print("Waiting for WeCrossProxy chaincode booting up..");
            do {
                System.out.print(".");
                Thread.sleep(10000);
                tryTimes++;
            } while (!connection.hasProxyDeployed2AllPeers() && tryTimes < 3);
            System.out.println();
        } else if (type == Hub) {
            System.out.print("Waiting for WeCrossHub chaincode booting up..");
            do {
                System.out.print(".");
                Thread.sleep(10000);
                tryTimes++;
            } while (!connection.hasHubDeployed2AllPeers() && tryTimes < 3);
            System.out.println();
        } else {
            System.out.println("ERROR: type " + type + " not supported");
            return;
        }

        // init
        initChaincode(
                orgNames, connection, driver, admin, blockManager, chaincodeName, version, args);

        System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
    }

    private static long queryCommittedChaincodeSequenceByName(
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName)
            throws Exception {
        System.out.println("Query committed chaincode " + chaincodeName + " ...");

        String channelName = connection.getChannel().getName();

        QueryCommittedRequest request = new QueryCommittedRequest();
        request.setName(chaincodeName);
        request.setChannelName(channelName);

        List<ResourceInfo> resourceInfos = connection.getResources(true);

        if (resourceInfos == null || resourceInfos.isEmpty()) {
            return 0;
        }

        ResourceInfo resourceInfo = null;

        try {
            resourceInfo =
                    resourceInfos
                            .stream()
                            .filter(
                                    new Predicate<ResourceInfo>() {
                                        @Override
                                        public boolean test(ResourceInfo resourceInfo) {
                                            return resourceInfo.getName().equals(chaincodeName);
                                        }
                                    })
                            .findFirst()
                            .get();
        } catch (Exception e) {
            return 0;
        }

        TransactionContext transactionContext =
                new TransactionContext(user, null, resourceInfo, blockManager);

        CompletableFuture<TransactionResponse> future1 = new CompletableFuture<>();
        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();

        ((FabricDriver) driver)
                .asyncQueryCommittedChaincode(
                        transactionContext,
                        request,
                        connection,
                        (transactionException, transactionResponse) -> {
                            future1.complete(transactionResponse);
                            future2.complete(transactionException);
                        });

        TransactionResponse transactionResponse = future1.get(80, TimeUnit.SECONDS);
        TransactionException transactionException = future2.get(80, TimeUnit.SECONDS);
        if (!transactionException.isSuccess()) {
            return 0;
        } else {
            String sequenceStr = transactionResponse.getResult()[0];
            Long sequence = new Long(sequenceStr);
            System.out.println(
                    "Query committed chaincode success, "
                            + chaincodeName
                            + " sequence:"
                            + sequence);
            return sequence;
        }
    }

    private static String install(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            byte[] code,
            String version)
            throws Exception {
        System.out.println("Install " + chaincodeName + ":" + version + " to " + orgName + " ...");

        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        InstallChaincodeRequest request =
                InstallChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgName(orgName)
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setCode(code);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionResponse> future1 = new CompletableFuture<>();
        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();

        ((FabricDriver) driver)
                .asyncInstallChaincode(
                        transactionContext,
                        request,
                        connection,
                        (transactionException, transactionResponse) -> {
                            future1.complete(transactionResponse);
                            future2.complete(transactionException);
                        });

        TransactionResponse transactionResponse = future1.get(80, TimeUnit.SECONDS);
        TransactionException transactionException = future2.get(80, TimeUnit.SECONDS);
        if (!transactionException.isSuccess()) {
            throw new Exception("Install error: " + transactionException.getMessage());
        } else {
            String packageId = transactionResponse.getResult()[0];
            System.out.println(
                    "Install success, "
                            + chaincodeName
                            + ":"
                            + version
                            + " to "
                            + orgName
                            + " packageId: "
                            + packageId);
            return packageId;
        }
    }

    private static void approveForMyOrg(
            String orgName,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String version,
            long sequence,
            String packageId)
            throws Exception {

        System.out.println("Approve " + chaincodeName + ":" + version + " to " + orgName + " ...");

        String channelName = connection.getChannel().getName();

        ApproveChaincodeRequest request =
                ApproveChaincodeRequest.build()
                        .setSequence(sequence)
                        .setName(chaincodeName)
                        .setChannelName(channelName)
                        .setVersion(version)
                        .setPackageId(packageId)
                        .setOrgName(orgName);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncApproveChaincode(
                        transactionContext,
                        request,
                        connection,
                        (transactionException, transactionResponse) ->
                                future.complete(transactionException));

        TransactionException transactionException = future.get(180, TimeUnit.SECONDS);
        if (!transactionException.isSuccess()) {
            throw new Exception("Approve error: " + transactionException.getMessage());
        }
        System.out.println(
                "Approve success, " + chaincodeName + ":" + version + " to " + orgName + " ...");
    }

    private static void commitChaincodeDefinition(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String version,
            long sequence,
            String packageId)
            throws Exception {

        System.out.println("Commit " + chaincodeName + ":" + version + " to " + orgNames + " ...");

        String channelName = connection.getChannel().getName();

        CommitChaincodeRequest request =
                CommitChaincodeRequest.build()
                        .setSequence(sequence)
                        .setName(chaincodeName)
                        .setChannelName(channelName)
                        .setVersion(version)
                        .setPackageId(packageId)
                        .setOrgNames(orgNames.toArray(new String[] {}));

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncCommitChaincode(
                        transactionContext,
                        request,
                        connection,
                        (transactionException, transactionResponse) ->
                                future.complete(transactionException));

        TransactionException transactionException = future.get(180, TimeUnit.SECONDS);
        if (!transactionException.isSuccess()) {
            throw new Exception("Commit chaincode error: " + transactionException.getMessage());
        }
        System.out.println(
                "Commit success, " + chaincodeName + ":" + version + " to " + orgNames + " ...");
    }

    private static void initChaincode(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String version,
            String[] args)
            throws Exception {

        System.out.println("Init " + chaincodeName + ":" + version + " to " + orgNames + " ...");

        String channelName = connection.getChannel().getName();

        TransactionRequest request = new TransactionRequest();
        request.setMethod("init");
        request.setArgs(args);

        List<ResourceInfo> resourceInfos = connection.getResources(true);
        if (resourceInfos == null || resourceInfos.isEmpty()) {
            throw new Exception("Chaincode " + chaincodeName + " not found");
        }
        ResourceInfo resourceInfo = null;

        try {
            resourceInfo =
                    resourceInfos
                            .stream()
                            .filter(
                                    new Predicate<ResourceInfo>() {
                                        @Override
                                        public boolean test(ResourceInfo resourceInfo) {
                                            return resourceInfo.getName().equals(chaincodeName);
                                        }
                                    })
                            .findFirst()
                            .get();
        } catch (Exception e) {
            throw new Exception("Chaincode " + chaincodeName + " not found");
        }

        TransactionContext transactionContext =
                new TransactionContext(user, null, resourceInfo, blockManager);

        CompletableFuture<TransactionException> future = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInitChaincode(
                        transactionContext,
                        request,
                        connection,
                        (transactionException, transactionResponse) ->
                                future.complete(transactionException));

        TransactionException transactionException = future.get(180, TimeUnit.SECONDS);
        if (!transactionException.isSuccess()) {
            throw new Exception("Init chaincode error: " + transactionException.getMessage());
        }
        System.out.println(
                "Init success, " + chaincodeName + ":" + version + " to " + orgNames + " ...");
    }

    private static void upgrade(
            List<String> orgNames,
            FabricConnection connection,
            Driver driver,
            Account user,
            BlockManager blockManager,
            String chaincodeName,
            String[] args,
            String version)
            throws Exception {
        System.out.println(
                "Upgrade " + chaincodeName + ":" + version + " to " + orgNames.toString() + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

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
        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncUpgradeChaincode(
                        transactionContext,
                        upgradeChaincodeRequest,
                        connection,
                        (transactionException, transactionResponse) ->
                                future2.complete(transactionException));

        TransactionException e2 = future2.get(180, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand upgrade error: " + e2.getMessage());
        }
    }

    public static class DirectBlockManager implements BlockManager {
        private Driver driver;
        private Connection connection;

        public DirectBlockManager(Driver driver, Connection connection) {
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
                    connection, (e, blockNumber) -> callback.onResponse(e, blockNumber));
        }

        @Override
        public void asyncGetBlock(long blockNumber, GetBlockCallback callback) {
            driver.asyncGetBlock(
                    blockNumber, true, connection, (e, block) -> callback.onResponse(e, block));
        }
    }

    public static LifecycleChaincodePackage packageChaincode(
            String sourcePath, String chaincodeName, String chaincodeLabel, String language)
            throws Exception {
        System.out.println("Package " + chaincodeName + " ...");
        // String language = "GO_LANG";
        PackageChaincodeRequest packageChaincodeRequest =
                PackageChaincodeRequest.build()
                        .setChaincodeName(chaincodeName)
                        .setChaincodeLabel(chaincodeLabel)
                        .setChaincodeType(language)
                        .setChaincodePath("github.com")
                        .setChaincodeMetaInfoPath(null)
                        .setChaincodeSourcePath(sourcePath);
        LifecycleChaincodePackage lifecycleChaincodePackage =
                ChaincodeHandler.packageChaincode(packageChaincodeRequest);
        System.out.println(
                "Package success! lifecycleChaincodePackage "
                        + lifecycleChaincodePackage.getPath());
        return lifecycleChaincodePackage;
    }
}
