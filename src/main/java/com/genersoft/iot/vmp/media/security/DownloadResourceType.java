package com.genersoft.iot.vmp.media.security;

public enum DownloadResourceType {

    CLOUD_RECORD,
    CLOUD_RECORD_ZIP,
    ALARM_SNAPSHOT,
    GB_SNAPSHOT,
    GB_RECORD,
    JT_SNAPSHOT,
    JT_RECORD;

    public boolean clientIssuable() {
        return this != GB_RECORD && this != JT_RECORD;
    }
}
