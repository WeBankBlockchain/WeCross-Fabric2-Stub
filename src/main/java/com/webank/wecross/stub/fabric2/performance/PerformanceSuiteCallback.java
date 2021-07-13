package com.webank.wecross.stub.fabric2.performance;

public interface PerformanceSuiteCallback {
    void onSuccess(String message);

    void onFailed(String message);
}
