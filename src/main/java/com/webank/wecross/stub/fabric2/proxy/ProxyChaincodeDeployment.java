package com.webank.wecross.stub.fabric2.proxy;

import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.StubConstant;
import com.webank.wecross.stub.fabric2.FabricConnection;
import com.webank.wecross.stub.fabric2.FabricConnectionFactory;
import com.webank.wecross.stub.fabric2.FabricStubConfigParser;
import com.webank.wecross.stub.fabric2.FabricStubFactory;
import com.webank.wecross.stub.fabric2.SystemChaincodeUtility;
import java.io.File;
import java.util.*;
import org.hyperledger.fabric.sdk.*;

public class ProxyChaincodeDeployment {

    private static LifecycleChaincodeEndorsementPolicy lccEndorsementPolicy;
    private static ChaincodeCollectionConfiguration ccCollectionConfiguration;

    static {
        try {
            /*
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

             */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void usage() {
        System.out.println(getUsage("chains/fabric2"));
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

    public static void deploy(String chainPath) throws Exception {
        String stubPath = "classpath:" + File.separator + chainPath;
        FabricConnection connection = FabricConnectionFactory.build(stubPath);

        String[] args = new String[] {connection.getChannel().getName()};
        SystemChaincodeUtility.deploy(
                chainPath, SystemChaincodeUtility.Proxy, StubConstant.PROXY_NAME, args);
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
        connection.hasProxyDeployed2AllPeers();

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
