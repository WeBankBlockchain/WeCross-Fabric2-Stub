package com.webank.wecross.stub.fabric2.FabricCustomCommand;

public class QueryCommittedRequest {
    private String name; // chaincode name
    private String channelName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public void check() throws Exception {
        if (this.name == null) {
            throw new Exception("name not set");
        }

        if (this.channelName == null) {
            throw new Exception("channelName not set");
        }
    }
}
