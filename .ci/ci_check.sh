#!/bin/bash

set -e
PROXY_SRC_DIR=src/main/resources/chaincode-fabric2.0/WeCrossProxy
PROXY_DEST_DIR=conf/chains/fabric2/chaincode-fabric2.0/WeCrossProxy/src/github.com/WeCrossProxy

./gradlew verifyGoogleJavaFormat

mkdir -p demo
cd demo
bash ../scripts/build_fabric_demo_chain.sh

cp ../src/main/resources/stubs-sample/fabric/stub.toml certs/chains/fabric2
cp -r certs/*  ../src/test/resources/

cd -
mkdir -p ${PROXY_DEST_DIR}
cp -r ${PROXY_SRC_DIR}/* ${PROXY_DEST_DIR}/
cd ${PROXY_DEST_DIR}
go get
go mod vendor
cd -

./gradlew build -x test
./gradlew test -i
./gradlew jacocoTestReport