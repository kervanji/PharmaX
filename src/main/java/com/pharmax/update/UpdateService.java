package com.pharmax.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmax.util.AppConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLParameters;

public class UpdateService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);

    private static final String KEY_OWNER = "update.github.owner";
    private static final String KEY_REPO = "update.github.repo";

    private static final String DEFAULT_OWNER = "kervanji";
    private static final String DEFAULT_REPO = "PharmaX";

    private static final String ASSET_SUFFIX = ".exe";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = buildHttpClient();

    private final AppConfigStore configStore = new AppConfigStore();

    private static HttpClient buildHttpClient() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});

        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(10))
                .sslParameters(sslParameters)
                .build();
    }

    public CompletableFuture<UpdateCheckResult> checkForUpdateAsync(String currentVersion) {
        return CompletableFuture.supplyAsync(() -> checkForUpdate(currentVersion));
    }

    public UpdateCheckResult checkForUpdate(String currentVersion) {
        String owner = DEFAULT_OWNER;
        String repo = DEFAULT_REPO;

        try {
            Properties p = configStore.load();
            owner = p.getProperty(KEY_OWNER, DEFAULT_OWNER).trim();
            repo = p.getProperty(KEY_REPO, DEFAULT_REPO).trim();
            if (owner.isEmpty()) {
                owner = DEFAULT_OWNER;
            }
            if (repo.isEmpty()) {
                repo = DEFAULT_REPO;
            }
        } catch (Exception e) {
            logger.warn("Failed to load update config", e);
        }

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PharmaX-Updater")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                logger.warn("Update check failed: status={} body={}", resp.statusCode(), resp.body());
                return new UpdateCheckResult(false, currentVersion, null, null, null);
            }

            JsonNode root = mapper.readTree(resp.body());
            String tag = text(root, "tag_name");
            String body = text(root, "body");

            String latestVersion = VersionUtil.normalizeTagToVersion(tag);
            if (latestVersion.isEmpty()) {
                return new UpdateCheckResult(false, currentVersion, tag, null, body);
            }

            String downloadUrl = findInstallerAssetUrl(root);
            if (downloadUrl == null || downloadUrl.isBlank()) {
                return new UpdateCheckResult(false, currentVersion, tag, null, body);
            }

            boolean newer = VersionUtil.compareVersions(currentVersion, latestVersion) < 0;
            return new UpdateCheckResult(newer, latestVersion, tag, downloadUrl, body);
        } catch (Exception e) {
            logger.warn("Update check error", e);
            throw new RuntimeException("Update check failed", e);
        }
    }

    public CompletableFuture<Path> downloadInstallerAsync(String downloadUrl, String fileName) {
        return CompletableFuture.supplyAsync(() -> downloadInstaller(downloadUrl, fileName));
    }

    public Path downloadInstaller(String downloadUrl, String fileName) {
        try {
            Path target = Path.of(System.getProperty("java.io.tmpdir"), fileName);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(10))
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "PharmaX-Updater")
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("Download failed, status=" + resp.statusCode());
            }
            try (InputStream in = resp.body()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String findInstallerAssetUrl(JsonNode root) {
        JsonNode assets = root.get("assets");
        if (assets == null || !assets.isArray()) {
            return null;
        }

        for (JsonNode a : assets) {
            String name = text(a, "name");
            if (name != null && name.toLowerCase().endsWith(ASSET_SUFFIX)) {
                String url = text(a, "browser_download_url");
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }
        }

        return null;
    }

    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText(null);
    }
}
