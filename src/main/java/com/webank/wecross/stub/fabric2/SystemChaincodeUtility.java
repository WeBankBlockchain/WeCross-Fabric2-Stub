package com.webank.wecross.stub.fabric2;

import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.ApproveChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.CommitChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.PackageChaincodeRequest;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.UpgradeChaincodeRequest;
import com.webank.wecross.stub.fabric2.chaincode.ChaincodeHandler;
import com.webank.wecross.stub.fabric2.utils.TarUtils;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.sdk.LifecycleChaincodePackage;

public class SystemChaincodeUtility {
    public static final int Proxy = 0;
    public static final int Hub = 1;

    public static void deploy(String chainPath, int type, String chaincodeName, String[] args)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, FabricConnection> orgConnections = FabricConnectionFactory.buildOrgConnections(stubPath);

        if (type == Proxy) {
             /*
            if (connection.hasProxyDeployed2AllPeers()) {
                System.out.println("SUCCESS: WeCrossProxy has been deployed to all connected org");
                return;
            }

            } else if (type == Hub) {
                if (connection.hasHubDeployed2AllPeers()) {
                    System.out.println("SUCCESS: WeCrossHub has been deployed to all connected org");
                    return;
                }*/
        } else {
            System.out.println("ERROR: type " + type + " not supported");
            return;
        }


        String packageId = null;
        long sequence = 3L;

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

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            String accountName = orgEntry.getValue().getAdminName();

            FabricConnection connection = orgConnections.get(orgName);
            connection.start();

            FabricStubFactory fabricStubFactory = new FabricStubFactory();
            Driver driver = fabricStubFactory.newDriver();
            BlockManager blockManager = new DirectBlockManager(driver, connection);

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            // install
            String currentPackageId =
                    install(
                            orgName,
                            connection,
                            driver,
                            orgAdmin,
                            blockManager,
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
                    connection,
                    driver,
                    orgAdmin,
                    blockManager,
                    chaincodeName,
                    version,
                    sequence,
                    packageId);
        }

        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockManager blockManager = new DirectBlockManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);

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

        System.out.println("SUCCESS: " + chaincodeName + " has been deployed to " + chainPath);
    }

    public static void upgrade(String chainPath, String chaincodeName, String[] args)
            throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;

        FabricStubConfigParser configFile = new FabricStubConfigParser(stubPath);
        String version = String.valueOf(System.currentTimeMillis() / 1000);
        FabricConnection connection = FabricConnectionFactory.build(stubPath);
        connection.start();

        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        Driver driver = fabricStubFactory.newDriver();
        BlockManager blockManager = new DirectBlockManager(driver, connection);
        List<String> orgNames = new LinkedList<>();
        String adminName = configFile.getFabricServices().getOrgUserName();
        Account admin =
                fabricStubFactory.newAccount(
                        adminName, "classpath:accounts" + File.separator + adminName);

        for (Map.Entry<String, FabricStubConfigParser.Orgs.Org> orgEntry :
                configFile.getOrgs().entrySet()) {
            String orgName = orgEntry.getKey();
            orgNames.add(orgName);
            String accountName = orgEntry.getValue().getAdminName();

            Account orgAdmin =
                    fabricStubFactory.newAccount(
                            accountName, "classpath:accounts" + File.separator + accountName);

            String chaincodeFilesDir =
                    "classpath:"
                            + chainPath
                            + File.separator
                            + "chaincode/"
                            + chaincodeName
                            + File.separator;
            byte[] code = TarUtils.generateTarGzInputStreamBytesFoGoChaincode(chaincodeFilesDir);
            String packageId =
                    install(
                            orgName,
                            connection,
                            driver,
                            orgAdmin,
                            blockManager,
                            chaincodeName,
                            code,
                            version);
        }
        upgrade(orgNames, connection, driver, admin, blockManager, chaincodeName, args, version);
        System.out.println("SUCCESS: " + chaincodeName + " has been upgraded to " + chainPath);
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

    private static void instantiate(
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
                "Instantiating "
                        + chaincodeName
                        + ":"
                        + version
                        + " to "
                        + orgNames.toString()
                        + " ...");
        String channelName = connection.getChannel().getName();
        String language = "GO_LANG";

        InstantiateChaincodeRequest instantiateChaincodeRequest =
                InstantiateChaincodeRequest.build()
                        .setName(chaincodeName)
                        .setVersion(version)
                        .setOrgNames(orgNames.toArray(new String[] {}))
                        .setChannelName(channelName)
                        .setChaincodeLanguage(language)
                        .setEndorsementPolicy("") // "OR ('Org1MSP.peer','Org2MSP.peer')"
                        .setArgs(args);

        TransactionContext transactionContext =
                new TransactionContext(user, null, null, blockManager);

        CompletableFuture<TransactionException> future2 = new CompletableFuture<>();
        ((FabricDriver) driver)
                .asyncInstantiateChaincode(
                        transactionContext,
                        instantiateChaincodeRequest,
                        connection,
                        (transactionException, transactionResponse) ->
                                future2.complete(transactionException));

        TransactionException e2 = future2.get(180, TimeUnit.SECONDS);
        if (!e2.isSuccess()) {
            throw new Exception("ERROR: asyncCustomCommand instantiate error: " + e2.getMessage());
        }
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
                        .setChaincodeMetaInfoPath(sourcePath)
                        .setChaincodeSourcePath(sourcePath);
        LifecycleChaincodePackage lifecycleChaincodePackage =
                ChaincodeHandler.packageChaincode(packageChaincodeRequest);
        System.out.println(
                "Package success!!! lifecycleChaincodePackage"
                        + lifecycleChaincodePackage.getPath());
        return lifecycleChaincodePackage;
    }
}
