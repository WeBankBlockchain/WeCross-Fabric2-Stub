package com.webank.wecross.stub.fabric;

import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.*;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateCommand;
import com.webank.wecross.stub.fabric.FabricCustomCommand.UpgradeCommand;
import com.webank.wecross.stub.fabric.chaincode.ChaincodeHandler;
import com.webank.wecross.stub.fabric.proxy.ProxyChaincodeResource;
import com.webank.wecross.utils.FabricUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricDriver implements Driver {
    private Logger logger = LoggerFactory.getLogger(FabricDriver.class);

    public byte[] encodeTransactionRequest(TransactionContext<TransactionRequest> request) {
        try {
            byte[] data = EndorserRequestFactory.encode(request);

            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            return transactionParams.toBytes();
        } catch (Exception e) {
            logger.error("encodeTransactionRequest error: " + e);
            return null;
        }
    }

    @Override
    public TransactionContext<TransactionRequest> decodeTransactionRequest(byte[] data) {
        try {
            TransactionParams transactionParams = TransactionParams.parseFrom(data);
            TransactionRequest plainRequest = transactionParams.getOriginTransactionRequest();

            TransactionContext<TransactionRequest> recoverContext =
                    EndorserRequestFactory.decode(transactionParams.getData());

            if (!transactionParams.isByProxy()) {
                // check the same
                TransactionRequest recoverRequest = recoverContext.getData();
                if (!recoverRequest.getMethod().equals(plainRequest.getMethod())
                        || !Arrays.equals(recoverRequest.getArgs(), plainRequest.getArgs())) {
                    throw new Exception(
                            "Illegal transaction request bytes, recover: "
                                    + recoverRequest
                                    + " plain: "
                                    + plainRequest);
                }

            } else {
                // TODO: Verify proxy transaction
            }

            TransactionContext<TransactionRequest> context =
                    new TransactionContext<>(
                            plainRequest,
                            recoverContext.getAccount(),
                            recoverContext.getPath(),
                            recoverContext.getResourceInfo(),
                            recoverContext.getBlockHeaderManager());

            return context;
        } catch (Exception e) {
            logger.error("decodeTransactionRequest error: " + e);
            return null;
        }
    }

    @Override
    public boolean isTransaction(Request request) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                return true;
            default:
                return false;
        }
    }

    @Override
    public BlockHeader decodeBlockHeader(byte[] data) {
        try {
            FabricBlock block = FabricBlock.encode(data);
            return block.dumpWeCrossHeader();
        } catch (Exception e) {
            logger.warn("decodeBlockHeader error: " + e);
            return null;
        }
    }

    @Override
    public TransactionResponse call(
            TransactionContext<TransactionRequest> request, Connection connection)
            throws TransactionException {
        TransactionResponse response = new TransactionResponse();

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();

        asyncCall(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException transactionException,
                            TransactionResponse transactionResponse) {
                        exceptionFuture.complete(transactionException);
                        future.complete(transactionResponse);
                    }
                });

        TransactionException transactionException;

        try {
            transactionException = exceptionFuture.get();
            response = future.get();
        } catch (Exception e) {
            throw TransactionException.Builder.newInternalException(
                    "Call: future get exception" + e);
        }

        if (!transactionException.isSuccess()) {
            throw transactionException;
        }

        return response;
    }

    @Override
    public void asyncCall(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Driver.Callback callback) {

        try {
            // check
            checkRequest(request);

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(request);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    connectionResponse -> {
                        TransactionResponse response = new TransactionResponse();
                        TransactionException transactionException;
                        try {
                            if (connectionResponse.getErrorCode()
                                    == FabricType.TransactionResponseStatus.SUCCESS) {
                                response =
                                        FabricUtils.decodeTransactionResponse(
                                                connectionResponse.getData());
                                response.setHash(
                                        EndorserRequestFactory.getTxIDFromEnvelopeBytes(data));
                            }
                            transactionException =
                                    new TransactionException(
                                            connectionResponse.getErrorCode(),
                                            connectionResponse.getErrorMessage());
                        } catch (Exception e) {
                            String errorMessage = "Fabric driver call onResponse exception: " + e;
                            logger.error(errorMessage);
                            transactionException =
                                    TransactionException.Builder.newInternalException(errorMessage);
                        }
                        callback.onTransactionResponse(transactionException, response);
                    });

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    @Override
    public void asyncCallByProxy(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Callback callback) {

        try {
            checkProxyRequest(request);

            TransactionContext<TransactionRequest> proxyRequest =
                    ProxyChaincodeResource.toProxyRequest(
                            request, ProxyChaincodeResource.MethodType.CALL);

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(proxyRequest);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, true);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_CALL);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    connectionResponse -> {
                        TransactionResponse response = new TransactionResponse();
                        TransactionException transactionException;
                        try {
                            if (connectionResponse.getErrorCode()
                                    == FabricType.TransactionResponseStatus.SUCCESS) {
                                response =
                                        FabricUtils.decodeTransactionResponse(
                                                connectionResponse.getData());
                                response.setHash(
                                        EndorserRequestFactory.getTxIDFromEnvelopeBytes(data));
                            }
                            transactionException =
                                    new TransactionException(
                                            connectionResponse.getErrorCode(),
                                            connectionResponse.getErrorMessage());
                        } catch (Exception e) {
                            String errorMessage =
                                    "Fabric driver callByProxy onResponse exception: " + e;
                            logger.error(errorMessage);
                            transactionException =
                                    TransactionException.Builder.newInternalException(errorMessage);
                        }
                        callback.onTransactionResponse(transactionException, response);
                    });

        } catch (Exception e) {
            callback.onTransactionResponse(
                    new TransactionException(
                            TransactionException.ErrorCode.INTERNAL_ERROR,
                            "asyncCallByProxy exception: " + e),
                    null);
        }
    }

    @Override
    public TransactionResponse sendTransaction(
            TransactionContext<TransactionRequest> request, Connection connection)
            throws TransactionException {

        TransactionResponse response = new TransactionResponse();

        CompletableFuture<TransactionResponse> future = new CompletableFuture<>();
        CompletableFuture<TransactionException> exceptionFuture = new CompletableFuture<>();

        asyncSendTransaction(
                request,
                connection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException transactionException,
                            TransactionResponse transactionResponse) {
                        exceptionFuture.complete(transactionException);
                        future.complete(transactionResponse);
                    }
                });

        TransactionException transactionException;

        try {
            transactionException = exceptionFuture.get();
            response = future.get();
        } catch (Exception e) {
            throw TransactionException.Builder.newInternalException(
                    "Sendtransaction: future get exception" + e);
        }

        if (!transactionException.isSuccess()
                && !transactionException
                        .getErrorCode()
                        .equals(
                                FabricType.TransactionResponseStatus
                                        .FABRIC_EXECUTE_CHAINCODE_FAILED)) {
            throw transactionException;
        }

        return response;
    }

    @Override
    public void asyncSendTransaction(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Driver.Callback callback) {
        try {
            // check
            checkRequest(request);

            // Send to endorser
            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(request);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, false);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    endorserResponse ->
                            ChaincodeHandler.asyncSendTransactionHandleEndorserResponse(
                                    request, data, endorserResponse, connection, callback));

        } catch (Exception e) {
            String errorMessage = "Fabric driver call exception: " + e;
            logger.error(errorMessage);
            TransactionResponse response = new TransactionResponse();
            TransactionException transactionException =
                    TransactionException.Builder.newInternalException(errorMessage);
            callback.onTransactionResponse(transactionException, response);
        }
    }

    @Override
    public void asyncSendTransactionByProxy(
            TransactionContext<TransactionRequest> request,
            Connection connection,
            Callback callback) {
        try {
            checkProxyRequest(request);

            TransactionContext<TransactionRequest> proxyRequest =
                    ProxyChaincodeResource.toProxyRequest(
                            request, ProxyChaincodeResource.MethodType.SENDTRANSACTION);

            byte[] data = EndorserRequestFactory.buildProposalRequestBytes(proxyRequest);
            TransactionParams transactionParams =
                    new TransactionParams(request.getData(), data, true);

            Request endorserRequest = new Request();
            endorserRequest.setData(transactionParams.toBytes());
            endorserRequest.setType(FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER);
            endorserRequest.setResourceInfo(request.getResourceInfo());

            connection.asyncSend(
                    endorserRequest,
                    endorserResponse ->
                            ChaincodeHandler.asyncSendTransactionHandleEndorserResponse(
                                    request, data, endorserResponse, connection, callback));

        } catch (Exception e) {
            callback.onTransactionResponse(
                    new TransactionException(
                            TransactionException.ErrorCode.INTERNAL_ERROR,
                            "asyncSendTransactionByProxy exception: " + e),
                    null);
        }
    }

    @Override
    public void asyncGetBlockNumber(Connection connection, GetBlockNumberCallback callback) {
        // Test failed
        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER);

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        if (response.getErrorCode()
                                == FabricType.TransactionResponseStatus.SUCCESS) {
                            long blockNumber = FabricUtils.bytesToLong(response.getData());
                            logger.debug("Get block number: " + blockNumber);
                            callback.onResponse(null, blockNumber);
                        } else {
                            String errorMsg =
                                    "Get block number failed: " + response.getErrorMessage();
                            logger.warn(errorMsg);
                            callback.onResponse(new Exception(errorMsg), -1);
                        }
                    }
                });
    }

    @Override
    public void asyncGetBlockHeader(
            long blockNumber, Connection connection, GetBlockHeaderCallback callback) {
        byte[] numberBytes = FabricUtils.longToBytes(blockNumber);

        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER);
        request.setData(numberBytes);

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        if (response.getErrorCode()
                                == FabricType.TransactionResponseStatus.SUCCESS) {
                            callback.onResponse(null, response.getData());
                        } else {
                            String errorMsg =
                                    "Get block header failed: " + response.getErrorMessage();
                            logger.warn(errorMsg);
                            callback.onResponse(new Exception(errorMsg), null);
                        }
                    }
                });
    }

    @Override
    public void asyncGetVerifiedTransaction(
            Path path,
            String transactionHash,
            long blockNumber,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            GetVerifiedTransactionCallback callback) {

        Request request = new Request();
        request.setType(FabricType.ConnectionMessage.FABRIC_GET_TRANSACTION);
        request.setData(transactionHash.getBytes(StandardCharsets.UTF_8));

        connection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        try {
                            if (response.getErrorCode()
                                    == FabricType.TransactionResponseStatus.SUCCESS) {

                                // Generate Verified transaction
                                FabricTransaction fabricTransaction =
                                        FabricTransaction.buildFromEnvelopeBytes(
                                                response.getData());
                                String txID = fabricTransaction.getTxID();
                                String chaincodeName = fabricTransaction.getChaincodeName();

                                if (!transactionHash.equals(txID)) {
                                    throw new Exception(
                                            "Request txHash: "
                                                    + transactionHash
                                                    + " but response: "
                                                    + txID);
                                }

                                ChaincodeHandler.asyncVerifyTransactionOnChain(
                                        txID,
                                        blockNumber,
                                        blockHeaderManager,
                                        new Consumer<Boolean>() {
                                            @Override
                                            public void accept(Boolean hasOnChain) {
                                                try {

                                                    if (!hasOnChain) {
                                                        throw new Exception(
                                                                "Verify failed. Tx("
                                                                        + txID
                                                                        + ") is invalid or not on block("
                                                                        + blockNumber
                                                                        + ")");
                                                    } else {
                                                        TransactionRequest transactionRequest =
                                                                parseFabricTransaction(
                                                                        fabricTransaction);

                                                        TransactionResponse transactionResponse =
                                                                FabricUtils
                                                                        .decodeTransactionResponse(
                                                                                fabricTransaction
                                                                                        .getOutputBytes());
                                                        transactionResponse.setHash(txID);
                                                        transactionResponse.setErrorCode(
                                                                FabricType.TransactionResponseStatus
                                                                        .SUCCESS);
                                                        transactionResponse.setBlockNumber(
                                                                blockNumber);

                                                        VerifiedTransaction verifiedTransaction =
                                                                new VerifiedTransaction(
                                                                        blockNumber,
                                                                        txID,
                                                                        path,
                                                                        chaincodeName,
                                                                        transactionRequest,
                                                                        transactionResponse);
                                                        callback.onResponse(
                                                                null, verifiedTransaction);
                                                    }
                                                } catch (Exception e) {
                                                    callback.onResponse(e, null);
                                                }
                                            }
                                        });
                            } else {
                                callback.onResponse(
                                        new Exception(response.getErrorMessage()), null);
                            }
                        } catch (Exception e) {
                            callback.onResponse(e, null);
                        }
                    }
                });
    }

    @Override
    public void asyncCustomCommand(
            String command,
            Path path,
            Object[] args,
            Account account,
            BlockHeaderManager blockHeaderManager,
            Connection connection,
            CustomCommandCallback callback) {
        switch (command) {
                //            package
                //            install
                //            appover
                //            commit
                //            init
            case InstallCommand.NAME:
                ChaincodeHandler.handleInstallCommand(
                        args, account, blockHeaderManager, connection, callback);
                break;
            case InstantiateCommand.NAME:
                ChaincodeHandler.handleInstantiateCommand(
                        args, account, blockHeaderManager, connection, callback);
                break;
            case UpgradeCommand.NAME:
                ChaincodeHandler.handleUpgradeCommand(
                        args, account, blockHeaderManager, connection, callback);
                break;
            default:
                callback.onResponse(new Exception("Unsupported command for Fabric plugin"), null);
                break;
        }
    }

    private void checkRequest(TransactionContext<TransactionRequest> request) throws Exception {
        if (request.getAccount() == null) {
            throw new Exception("Unknown account");
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        if (request.getBlockHeaderManager() == null) {
            throw new Exception("blockHeaderManager is null");
        }

        if (request.getResourceInfo() == null) {
            throw new Exception("resourceInfo is null");
        }

        if (request.getData() == null) {
            throw new Exception("TransactionRequest is null");
        }

        if (request.getData().getArgs() == null) {
            // Fabric has no null args, just pass it as String[0]
            request.getData().setArgs(new String[0]);
        }
    }

    private void checkProxyRequest(TransactionContext<TransactionRequest> request)
            throws Exception {
        if (request.getResourceInfo() == null) {
            throw new Exception("resourceInfo is null");
        }

        String isTemporary = (String) request.getResourceInfo().getProperties().get("isTemporary");
        if (isTemporary != null && isTemporary.equals("true")) {
            throw new Exception(
                    "Fabric resource " + request.getResourceInfo().getName() + " not found");
        }

        if (request.getAccount() == null) {
            throw new Exception("Unkown account: " + request.getAccount());
        }

        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }
    }

    private TransactionRequest parseFabricTransaction(FabricTransaction fabricTransaction)
            throws Exception {
        String chaincodeName = fabricTransaction.getChaincodeName();
        String[] originArgs = fabricTransaction.getArgs().toArray(new String[] {});
        String[] args;
        String method;

        if (chaincodeName.equals(ProxyChaincodeResource.DEFAULT_NAME)) {
            args = ProxyChaincodeResource.decodeSendTransactionArgs(originArgs);
            method = ProxyChaincodeResource.decodeSendTransactionArgsMethod(originArgs);
        } else {
            args = originArgs;
            method = fabricTransaction.getMethod();
        }

        TransactionRequest transactionRequest = new TransactionRequest();

        transactionRequest.setArgs(args);
        transactionRequest.setMethod(method);
        return transactionRequest;
    }
}
