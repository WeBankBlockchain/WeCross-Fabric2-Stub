package com.webank.wecross.stub.fabric2.performance;

public interface PerformanceSuite {
    String getName();

    void call(PerformanceSuiteCallback callback);
}
