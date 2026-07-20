package com.genersoft.iot.vmp.media.security;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class DownloadPathGuard {

    public DownloadFileGrant resolveLocal(
            String allowedRoot,
            String filePath,
            String fileName) {
        try {
            Path root = realRoot(allowedRoot);
            Path candidate = candidate(allowedRoot, filePath).toRealPath();
            requireInside(root, candidate);
            if (!Files.isRegularFile(candidate)) {
                throw unavailable("下载目标不是普通文件", null);
            }
            return new DownloadFileGrant(
                    DownloadStorageType.LOCAL,
                    candidate.toString(),
                    root.toString(),
                    safeFileName(fileName, candidate.getFileName().toString()),
                    null);
        } catch (IOException | InvalidPathException exception) {
            throw unavailable("下载文件不可用", exception);
        }
    }

    public DownloadFileGrant resolveMediaFile(
            String allowedRoot,
            String filePath,
            String fileName,
            String mediaServerId,
            boolean remoteZlmAllowed) {
        Path normalizedRoot = normalizedRoot(allowedRoot);
        Path normalizedCandidate = candidate(allowedRoot, filePath);
        requireInside(normalizedRoot, normalizedCandidate);

        if (Files.exists(normalizedRoot) && Files.exists(normalizedCandidate)) {
            return resolveLocal(allowedRoot, filePath, fileName);
        }
        if (!remoteZlmAllowed) {
            throw unavailable("媒体文件未挂载且节点不支持受控远程下载", null);
        }
        return new DownloadFileGrant(
                DownloadStorageType.REMOTE_ZLM,
                normalizedCandidate.toString(),
                normalizedRoot.toString(),
                safeFileName(fileName, normalizedCandidate.getFileName().toString()),
                mediaServerId);
    }

    public DownloadFileGrant revalidate(DownloadFileGrant target) {
        if (target.storageType() == DownloadStorageType.LOCAL) {
            return resolveLocal(
                    target.allowedRoot(), target.path(), target.fileName());
        }
        Path root = normalizedRoot(target.allowedRoot());
        Path candidate = candidate(target.allowedRoot(), target.path());
        requireInside(root, candidate);
        return new DownloadFileGrant(
                target.storageType(),
                candidate.toString(),
                root.toString(),
                safeFileName(target.fileName(), candidate.getFileName().toString()),
                target.mediaServerId());
    }

    public String safeFileName(String value, String fallback) {
        String candidate = value == null ? "" : value.trim();
        candidate = candidate.replace('\r', '_').replace('\n', '_');
        try {
            Path name = Path.of(candidate).getFileName();
            candidate = name == null ? "" : name.toString();
        } catch (InvalidPathException exception) {
            candidate = "";
        }
        candidate = candidate.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            candidate = fallback == null ? "download.bin" : fallback;
        }
        if (candidate.length() > 180) {
            String extension = extension(candidate);
            int stemLength = Math.max(1, 180 - extension.length());
            candidate = candidate.substring(0, stemLength) + extension;
        }
        return candidate;
    }

    private Path realRoot(String value) throws IOException {
        Path root = normalizedRoot(value).toRealPath();
        if (!Files.isDirectory(root)) {
            throw new IOException("allowed root is not a directory");
        }
        return root;
    }

    private Path normalizedRoot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("下载根目录不能为空");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private Path candidate(String rootValue, String filePath) {
        if (filePath == null || filePath.isBlank() || filePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("下载文件路径无效");
        }
        Path root = normalizedRoot(rootValue);
        Path raw = Path.of(filePath);
        Path resolved = raw.isAbsolute()
                ? raw.toAbsolutePath().normalize()
                : root.resolve(raw).normalize();
        requireInside(root, resolved);
        return resolved;
    }

    private void requireInside(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("下载文件超出允许目录");
        }
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index >= fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(index).toLowerCase(Locale.ROOT);
        return extension.length() <= 16 ? extension : "";
    }

    private DownloadTicketException unavailable(
            String message,
            Throwable cause) {
        return new DownloadTicketException(
                DownloadTicketFailureReason.TARGET_UNAVAILABLE,
                message,
                cause);
    }
}
