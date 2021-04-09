![](./docs/images/menu_logo_wecross.png)

# WeCross-Fabric2-Stub

[![CodeFactor](https://www.codefactor.io/repository/github/webankblockchain/WeCross-Fabric1-Stub/badge)](https://www.codefactor.io/repository/github/webankblockchain/WeCross-Fabric1-Stub) [![Build Status](https://travis-ci.org/WeBankBlockchain/WeCross-Fabric1-Stub.svg?branch=dev)](https://travis-ci.org/WeBankBlockchain/WeCross-Fabric1-Stub) [![Latest release](https://img.shields.io/github/release/WeBankBlockchain/WeCross-Fabric1-Stub.svg)](https://github.com/WeBankBlockchain/WeCross-Fabric1-Stub/releases/latest)
[![License](https://img.shields.io/github/license/WeBankBlockchain/WeCross-Fabric1-Stub)](https://www.apache.org/licenses/LICENSE-2.0) [![Language](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com)

WeCross Fabric2 Stub是[WeCross](https://github.com/WeBankBlockchain/WeCross)用于适配[Hyperledger Fabric 2.0](https://github.com/hyperledger/fabric/tree/release-1.4)及以上版本的插件。

## 关键特性

- Hyperledger Fabric配置加载
- Hyperledger Fabric账户生成
- Hyperledger Fabric链上资源访问
- Hyperledger Fabric交易签名与解析

## fabric2.0-stub 适配补充说明

- WeCrossProxy跨链合约
    跨连合约的安装支持通过控制台进行命令安装，实现方式参考了fabric-sdk官方，引入了 network_config.yaml等配置文件等
- 合约安装
    控制台安装合约的方式暂未进行适配（合约可通过BaaS平台进行安装）
- 其他基本与fabric1.4-stub类似

#### 接入Fabric链

1. 添加账户

		cd routers-payment/192.168.1.39-8251-25501/
		#用脚本生成BCOS账户：账户类型（Fabric2.0），账户名（fabric_user1）
		./add_account.sh -t Fabric2.0 -n fabric_user1 
		./add_account.sh -t Fabric2.0 -n fabric_admin
		./add_account.sh -t Fabric2.0 -n fabric_admin_org1
		./add_account.sh -t Fabric2.0 -n fabric_admin_org2
	目前配置了四个账户，若此router需要向BCOS的链发交易，也可配置BCOS的账户。账户配置与接入的链无关，router间自动转发交易至相应的链
2. 拷贝 Fabric链的证书(单机部署或者多机部署)
	
	```
    cp /data/wecross/fabric/certs/accounts/fabric_user1/* conf/accounts/fabric_user1/
    scp root@192.168.1.29:/data/2720ac1a11e549f6ace21813caed36cc/tbs/certs/accounts/fabric_user1/* conf/accounts/fabric_user1/
    cp /data/wecross/fabric/certs/accounts/fabric_admin/* conf/accounts/fabric_admin/
    scp root@192.168.1.29:/data/2720ac1a11e549f6ace21813caed36cc/tbs/certs/accounts/fabric_admin/* conf/accounts/fabric_admin/
    cp /data/wecross/fabric/certs/accounts/fabric_admin_org1/* conf/accounts/fabric_admin_org1/
    scp root@192.168.1.29:/data/2720ac1a11e549f6ace21813caed36cc/tbs/certs/accounts/fabric_admin_org1/* conf/accounts/fabric_admin_org1/
    cp /data/wecross/fabric/certs/accounts/fabric_admin_org2/* conf/accounts/fabric_admin_org2/
    scp root@192.168.1.11:/data/fabric/certs/accounts/fabric_admin_org2/* conf/accounts/fabric_admin_org2/

	```
3. 修改配置
	
	```
	vi conf/accounts/fabric_admin_org2/account.toml

	```
	```
	[account]
    type = 'Fabric2.0'
    mspid = 'Org2MSP' # 配置为Org2MSP
    keystore = 'account.key'
    signcert = 'account.crt'
	```
	
2. 配置接入Fabric链

		 cd WeCross/routers-payment/127.0.0.1-8250-25501/
		 # -t 链类型，-n 指定链名字
		./add_chain.sh -t Fabric2.0 -n fabric
3. 配置Fabric节点连接
	* 拷贝证书(从Fabric链搭建机器拷贝)

	```
	cd WeCross/routers-payment/127.0.0.1-8250-25501/
	#本机(certs 脚本生成的文件夹，具体要从fabric证书文件中去复制)
	cp /data/wecross/fabric/certs/chains/fabric/* conf/chains/fabric/
	scp root@192.168.1.29:/data/2720ac1a11e549f6ace21813caed36cc/tbs/certs/chains/fabric/* conf/chains/fabric/	
	```
	* 修改配置文件stub.toml
	
	```
	vi conf/chains/fabric/stub.toml
	```
	
	```
	[common]
    name = 'fabric'
    type = 'Fabric2.0'

	[fabricServices]
    channelName = 'mychannel'
    orgUserName = 'fabric_admin'
    ordererTlsCaFile = 'orderer-tlsca.crt'
    ordererAddress = 'grpcs://localhost:7050'

	[orgs]
    [orgs.Org1]
        tlsCaFile = 'org1-tlsca.crt'
        adminName = 'fabric_admin_org1'
        endorsers = ['grpcs://localhost:7051']

    [orgs.Org2]
        tlsCaFile = 'org2-tlsca.crt'
        adminName = 'fabric_admin_org2'
        endorsers = ['grpcs://localhost:9051']
	```
4. 部署代理合约

	```
	cd WeCross/routers-payment/127.0.0.1-8250-25501/
	
	java -cp 'conf/:lib/*:plugin/*' com.tusdao.wecross.stub.fabric.proxy.ProxyChaincodeDeployment deploy chains/fabric # deploy conf下的链配置位置 upgrade
	```
	* 注意链码目录结构
	
		```
		tree conf/chains/fabric/WeCrossProxy/ -L 4
       conf/chains/fabric/WeCrossProxy/
       ├── META-INF
       │   └── statedb
       │       └── couchdb
       │           └── indexes
       └── src
                  └── github.com
                       └── WeCrossProxy
                           ├── go.mod
                           ├── go.sum
                           ├── proxy.go
                           └── vendor 
       8 directories, 3 files
		```
	
5. 重启路由
	
	```
	cd WeCross/routers-payment/127.0.0.1-8250-25500/
	
	# 若WeCross跨链路由未停止，需要先停止
	bash stop.sh

	# 重新启动
	bash start.sh
	```
	


## 编译插件

**环境要求**:

  - [JDK8及以上](https://www.oracle.com/java/technologies/javase-downloads.html)
  - Gradle 5.0及以上

**编译命令**:

```shell
git clone https://github.com/WeBankBlockchain/WeCross-Fabric2-Stub.git
cd WeCross-Fabric2-Stub
./gradlew assemble
```

如果编译成功，将在当前目录的dist/apps目录下生成插件jar包。

## 插件使用

插件的详细使用方式请参阅[WeCross技术文档](https://wecross.readthedocs.io/zh_CN/latest/docs/stubs/fabric.html#id1)

## 贡献说明

欢迎参与WeCross社区的维护和建设：

- 提交代码(Pull requests)，可参考[代码贡献流程](CONTRIBUTING.md)以及[wiki指南](https://github.com/WeBankBlockchain/WeCross/wiki/%E8%B4%A1%E7%8C%AE%E4%BB%A3%E7%A0%81)
- [提问和提交BUG](https://github.com/WeBankBlockchain/WeCross-Fabric1-Stub/issues/new)

希望在您的参与下，WeCross会越来越好！

## 社区
联系我们：wecross@webank.com

## License

WeCross Fabric2 Stub的开源协议为Apache License 2.0，详情参考[LICENSE](./LICENSE)。
