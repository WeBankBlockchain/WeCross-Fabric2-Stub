package com.webank.wecross.stub.fabric2;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Stub;
import com.webank.wecross.stub.StubFactory;
import com.webank.wecross.stub.WeCrossContext;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric2.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric2.account.FabricAccountFactory;
import com.webank.wecross.stub.fabric2.performance.PerformanceTest;
import com.webank.wecross.stub.fabric2.performance.ProxyTest;
import com.webank.wecross.stub.fabric2.proxy.ProxyChaincodeDeployment;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Stub("Fabric2.0")
public class FabricStubFactory implements StubFactory {
    private static Logger logger = LoggerFactory.getLogger(FabricStubFactory.class);

    @Override
    public void init(WeCrossContext context) {
        context.registerCommand(InstallCommand.NAME, InstallCommand.DESCRIPTION);
        context.registerCommand(InstantiateCommand.NAME, InstantiateCommand.DESCRIPTION);
    }

    @Override
    public Driver newDriver() {
        return new FabricDriver();
    }

    @Override
    public Connection newConnection(String path) {
        try {
            FabricConnection fabricConnection = FabricConnectionFactory.build(path);
            fabricConnection.start();

            // Check proxy chaincode
            if (!fabricConnection.hasProxyDeployed2AllPeers()) {
                System.out.println(ProxyChaincodeDeployment.getUsage(path));
                throw new Exception("WeCrossProxy has not been deployed to all org");
            }

            return fabricConnection;
        } catch (Exception e) {
            logger.error("newConnection exception: " + e);
            return null;
        }
    }

    @Override
    public Account newAccount(Map<String, Object> properties) {
        return FabricAccountFactory.build(properties);
    }

    // Used by default account
    public Account newAccount(String name, String path) {
        return FabricAccountFactory.build(name, path);
    }

    @Override
    public void generateAccount(String path, String[] args) {
        try {
            // Generate config file only, copy user cert from crypto-config/

            // Write config file
            String accountTemplate =
                    "[account]\n"
                            + "    type = 'Fabric2.0'\n"
                            + "    mspid = 'Org1MSP'\n"
                            + "    keystore = 'account.key'\n"
                            + "    signcert = 'account.crt'";

            String confFilePath = path + "/account.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }

            String name = new File(path).getName();
            System.out.println(
                    "\nSUCCESS: Account \""
                            + name
                            + "\" config framework has been generated to \""
                            + path
                            + "\"\nPlease copy cert file and edit account.toml");

        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    @Override
    public void generateConnection(String path, String[] args) {
        try {
            String chainName = new File(path).getName();

            String accountTemplate =
                    "[common]\n"
                            + "    name = 'fabric2'\n"
                            + "    type = 'Fabric2.0'\n"
                            + "\n"
                            + "[fabricServices]\n"
                            + "    channelName = 'mychannel'\n"
                            + "    orgUserName = 'fabric2_admin'\n"
                            + "    ordererTlsCaFile = 'orderer-tlsca.crt'\n"
                            + "    ordererAddress = 'grpcs://localhost:7050'\n"
                            + "\n"
                            + "[orgs]\n"
                            + "    [orgs.org1]\n"
                            + "        tlsCaFile = 'org1-tlsca.crt'\n"
                            + "        adminName = 'fabric2_admin_org1'\n"
                            + "        endorsers = ['grpcs://localhost:7051']\n"
                            + "\n"
                            + "    [orgs.org2]\n"
                            + "        tlsCaFile = 'org2-tlsca.crt'\n"
                            + "        adminName = 'fabric2_admin_org2'\n"
                            + "        endorsers = ['grpcs://localhost:9051']\n";
            String confFilePath = path + "/stub.toml";
            File confFile = new File(confFilePath);
            if (!confFile.createNewFile()) {
                logger.error("Conf file exists! {}", confFile);
                return;
            }

            FileWriter fileWriter = new FileWriter(confFile);
            try {
                fileWriter.write(accountTemplate);
            } finally {
                fileWriter.close();
            }

            // Generate proxy and hub chaincodes
            generateProxyChaincodes(path);
            generateHubChaincodes(path);

            System.out.println(
                    "SUCCESS: Chain \""
                            + chainName
                            + "\" config framework has been generated to \""
                            + path
                            + "\"\nPlease copy cert file and edit stub.toml");
        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
    }

    public void generateProxyChaincodes(String path) {
        String srcPath = "chaincode" + File.separator + "WeCrossProxy";
        String destPath =
                path
                        + File.separator
                        + srcPath
                        + File.separator
                        + "src"
                        + File.separator
                        + "github.com"
                        + File.separator
                        + "WeCrossProxy";
        copyJarDir(srcPath, destPath);
    }

    public void generateHubChaincodes(String path) {

        String srcPath = "chaincode" + File.separator + "WeCrossHub";
        String destPath =
                path
                        + File.separator
                        + srcPath
                        + File.separator
                        + "src"
                        + File.separator
                        + "github.com"
                        + File.separator
                        + "WeCrossHub";
        copyJarDir(srcPath, destPath);
    }

    private static void copyJarDir(String srcPath, String destPath) {
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(srcPath + File.separator + "**");
            for (Resource resource : resources) {
                URL url = resource.getURL();

                String destFileName;
                String[] split = url.getFile().split(srcPath);
                if (split.length == 2) {
                    if (split[1].endsWith("" + File.separator)) {
                        continue;
                    }
                    destFileName = split[1];
                } else if (split.length < 2) {
                    continue;
                } else {
                    destFileName = url.getFile().replace(split[0], "");
                }

                String destFile = destPath + File.separator + destFileName;

                // System.out.println("Copy " + url.getFile() + " to " + destFile);
                System.out.print(".");

                File dest = new File(destFile);
                try {
                    FileUtils.copyURLToFile(url, dest);
                } catch (Exception e) {
                    System.out.println(e);
                    continue;
                }
            }
        } catch (Exception e) {
            System.out.println(e);
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(
                "This is Fabric2.0 Stub Plugin. Please copy this file to router/plugin/");
        System.out.println("To deploy WeCrossProxy:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.fabric2.proxy.ProxyChaincodeDeployment ");
        System.out.println("To performance test, please run the command for more info:");
        System.out.println(
                "    Pure:    java -cp conf/:lib/*:plugin/* " + PerformanceTest.class.getName());
        System.out.println(
                "    Proxy:   java -cp conf/:lib/*:plugin/* " + ProxyTest.class.getName());
    }
}
