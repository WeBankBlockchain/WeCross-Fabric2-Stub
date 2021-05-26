package com.webank.wecross.stub.fabric.FabricCustomCommand;

public class CommitChaincodeRequest {

    private long sequence;
    private String name; // chaincode name
    private String channelName;
    private String version;
    private String packageId;
    private String[] orgNames;

    public static CommitChaincodeRequest build() {
        return new CommitChaincodeRequest();
    }

    public CommitChaincodeRequest setSequence(long sequence) {
        this.sequence = sequence;
        return this;
    }

    public CommitChaincodeRequest setName(String name) {
        this.name = name;
        return this;
    }

    public CommitChaincodeRequest setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public CommitChaincodeRequest setVersion(String version) {
        this.version = version;
        return this;
    }

    public CommitChaincodeRequest setPackageId(String packageId) {
        this.packageId = packageId;
        return this;
    }

    public CommitChaincodeRequest setOrgNames(String[] orgNames) {
        this.orgNames = orgNames;
        return this;
    }

    public void check() throws Exception {
        if (this.sequence == 0) {
            throw new Exception("sequence not set");
        }

        if (this.name == null) {
            throw new Exception("name not set");
        }

        if (this.channelName == null) {
            throw new Exception("channelName not set");
        }

        if (this.version == null) {
            throw new Exception("version not set");
        }

        if (this.packageId == null) {
            throw new Exception("packageId not set");
        }

        if (this.orgNames == null) {
            throw new Exception("orgNames not set");
        }
    }

    public long getSequence() {
        return sequence;
    }

    public String getName() {
        return name;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getVersion() {
        return version;
    }

    public String getPackageId() {
        return packageId;
    }

    public String[] getOrgNames() {
        return orgNames;
    }
}
