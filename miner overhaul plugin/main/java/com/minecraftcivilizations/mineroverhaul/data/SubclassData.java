package com.minecraftcivilizations.mineroverhaul.data;

import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;

public class SubclassData {

    private String uuid;
    private MinerSubclass subclass;
    private long selectedAt;
    private int lastPromptedLevel;

    public SubclassData() {}

    public SubclassData(String uuid) {
        this.uuid = uuid;
        this.subclass = null;
        this.selectedAt = 0L;
        this.lastPromptedLevel = 0;
    }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public MinerSubclass getSubclass() { return subclass; }
    public void setSubclass(MinerSubclass subclass) { this.subclass = subclass; }

    public long getSelectedAt() { return selectedAt; }
    public void setSelectedAt(long selectedAt) { this.selectedAt = selectedAt; }

    public int getLastPromptedLevel() { return lastPromptedLevel; }
    public void setLastPromptedLevel(int lastPromptedLevel) { this.lastPromptedLevel = lastPromptedLevel; }

    public boolean hasSubclass() { return subclass != null; }
}
