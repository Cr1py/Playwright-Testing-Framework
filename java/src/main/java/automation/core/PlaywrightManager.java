package automation.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import automation.config.ProjectConfig;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * one Playwright and one Browser per thread 
 * playwright objects are not thread-safe, so with JUnit's parallel execution every worker thread lazily gets its own instances
 */
public final class PlaywrightManager {
    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final Queue<AutoCloseable> OPEN = new ConcurrentLinkedQueue<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PlaywrightManager::closeAll, "playwright-shutdown"));
    }

    private PlaywrightManager() {}

    public static Playwright playwright() {
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright == null) {
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            PLAYWRIGHT.set(playwright);
            OPEN.add(playwright);
        }
        return playwright;
    }

    /* launches the browser on first use, so API-only tests never start one */
    public static Browser browser(ProjectConfig config) {
        Browser browser = BROWSER.get();
        if (browser == null) {
            browser = BrowserFactory.launch(playwright(), config);
            BROWSER.set(browser);
            OPEN.add(browser);
        }
        return browser;
    }

    private static void closeAll() {
        AutoCloseable item;
        while ((item = OPEN.poll()) != null) {
            try {
                item.close();
            } catch (Exception | LinkageError ignored) {
                // nothing useful to do during shutdown (classes may already be unloaded)
            }
        }
    }
}
