package com.genersoft.iot.vmp.media.security;

import java.util.List;
import java.util.Objects;

/** Server-side delivery instruction stored only in the ticket store. */
public record DownloadPayload(
        DownloadDeliveryType deliveryType,
        List<DownloadFileGrant> files,
        String opaqueId,
        String phoneNumber,
        Integer channelNumber,
        String contentType,
        String fileName,
        boolean inline) {

    public DownloadPayload {
        deliveryType = Objects.requireNonNull(
                deliveryType, "deliveryType must not be null");
        files = files == null ? List.of() : List.copyOf(files);
        opaqueId = trimToNull(opaqueId);
        phoneNumber = trimToNull(phoneNumber);
        contentType = requireText(contentType, "contentType");
        fileName = requireText(fileName, "fileName");

        switch (deliveryType) {
            case FILE -> requireFileCount(files, 1);
            case ZIP -> {
                if (files.isEmpty()) {
                    throw new IllegalArgumentException("zip payload requires files");
                }
            }
            case JT_RECORD -> {
                if (opaqueId == null || !files.isEmpty()) {
                    throw new IllegalArgumentException("JT record payload is invalid");
                }
            }
            case JT_SNAPSHOT -> {
                if (phoneNumber == null
                        || channelNumber == null
                        || channelNumber <= 0
                        || !files.isEmpty()) {
                    throw new IllegalArgumentException("JT snapshot payload is invalid");
                }
            }
        }
    }

    private static void requireFileCount(List<DownloadFileGrant> files, int count) {
        if (files.size() != count) {
            throw new IllegalArgumentException("file payload requires exactly one file");
        }
    }

    private static String requireText(String value, String fieldName) {
        String result = trimToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
