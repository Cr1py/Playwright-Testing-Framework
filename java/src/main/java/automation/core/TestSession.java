package automation.core;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import automation.api.common.AuthTokens;
import automation.api.common.BaseApiClient;
import automation.config.ProjectConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/*
 * everything one test uses: a browser page and API contexts, all created lazily so an API test never starts a browser
 * handles screenshot / trace / video capture according to the shared config
 * must be used from the thread that created it
*/
public final class TestSession implements AutoCloseable {
    private static final ThreadLocal<TestSession> CURRENT = new ThreadLocal<>();

    private final ProjectConfig config;
    private final Path artifactDir = Path.of("build", "artifacts", UUID.randomUUID().toString());
    private final ArtifactMode video;
    private final ArtifactMode screenshot;
    private final ArtifactMode trace;

    private BrowserContext context;
    private Page page;
    private Path videoPath;
    private boolean tracing;
    private boolean traceStopped;
    private boolean failed;
    private APIRequestContext api;
    private APIRequestContext anonymousApi;

    private TestSession(ProjectConfig config) {
        this.config = config;
        this.video = ArtifactMode.of(config.video());
        this.screenshot = ArtifactMode.of(config.screenshot());
        this.trace = ArtifactMode.of(config.trace());
    }

    public static TestSession begin(ProjectConfig config) {
        TestSession session = new TestSession(config);
        CURRENT.set(session);
        return session;
    }

    public static Optional<TestSession> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    // browser 

    /* fresh page in its own browser context (test isolation), relative URLs use the frontend baseURL */
    public Page page() {
        if (page == null) {
            Browser.NewContextOptions options = new Browser.NewContextOptions()
                    .setBaseURL(config.frontend().baseURL())
                    .setViewportSize(config.viewport().width(), config.viewport().height());
            if (video.enabled()) options.setRecordVideoDir(artifactDir.resolve("video"));

            context = PlaywrightManager.browser(config).newContext(options);
            context.setDefaultTimeout(config.timeouts().action());
            context.setDefaultNavigationTimeout(config.timeouts().navigation());
            if (trace.enabled()) {
                context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
                tracing = true;
            }
            page = context.newPage();
            if (video.enabled() && page.video() != null) videoPath = page.video().path();
        }
        return page;
    }

    // API 
    public APIRequestContext api() {
        if (api == null) {
            Map<String, String> headers = new HashMap<>();
            String token = AuthTokens.token(config);
            if (!token.isEmpty()) headers.put("Authorization", "Bearer " + token);
            api = newApiContext(headers);
        }
        return api;
    }

    /* 401/403 checks */
    public APIRequestContext anonymousApi() {
        if (anonymousApi == null) anonymousApi = newApiContext(Map.of());
        return anonymousApi;
    }

    private APIRequestContext newApiContext(Map<String, String> headers) {
        return PlaywrightManager.playwright().request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(BaseApiClient.baseUrl(config))
                .setExtraHTTPHeaders(headers)
                .setTimeout(config.api().timeouts().request()));
    }

    // failure capture

    /* called by FailureExtension right after a failed test body, while the page is still open. */
    public void onFailure() {
        failed = true;
        if (page == null || page.isClosed()) return;
        if (screenshot.keepOnFailure()) attachScreenshot();
        if (tracing && trace.keepOnFailure()) stopTrace(true);
    }

    @Override
    public void close() {
        try {
            if (context != null) {
                if (screenshot.keepAlways() && !failed && !page.isClosed()) attachScreenshot();
                if (tracing && !traceStopped) {
                    if (trace.keepAlways() && !failed) stopTrace(true);
                    else stopTrace(false);
                }
                context.close(); // finalizes the video file
                if (videoPath != null && Files.exists(videoPath) && (failed || video.keepAlways())) {
                    attachFile("video", "video/webm", videoPath, "webm");
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Could not finish browser artifacts: " + e.getMessage());
        } finally {
            if (api != null) api.dispose();
            if (anonymousApi != null) anonymousApi.dispose();
            deleteQuietly(artifactDir);
            CURRENT.remove();
        }
    }

    private void attachScreenshot() {
        try {
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.attachment("screenshot", "image/png", new ByteArrayInputStream(png), AttachmentOptions.withFileExtension("png"));
        } catch (RuntimeException e) {
            System.err.println("Could not take screenshot: " + e.getMessage());
        }
    }

    private void stopTrace(boolean keep) {
        traceStopped = true;
        try {
            if (keep) {
                Path zip = artifactDir.resolve("trace.zip");
                context.tracing().stop(new Tracing.StopOptions().setPath(zip));
                attachFile("trace", "application/zip", zip, "zip");
            } else {
                context.tracing().stop();
            }
        } catch (RuntimeException e) {
            System.err.println("Could not stop tracing: " + e.getMessage());
        }
    }

    private static void attachFile(String name, String type, Path file, String extension) {
        try (InputStream in = Files.newInputStream(file)) {
            Allure.attachment(name, type, in, AttachmentOptions.withFileExtension(extension));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // leftover artifacts under build/ are harmless
        }
    }
}
