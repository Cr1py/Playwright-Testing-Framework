package automation.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import automation.config.Env;
import automation.config.ProjectConfig;

public final class BrowserFactory {
    private BrowserFactory() {}

    public static Browser launch(Playwright playwright, ProjectConfig config) {
        BrowserType type = switch (config.browser()) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
        boolean headless = config.headless() && !Env.flag("HEADED");
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        Env.get("BROWSER_CHANNEL").ifPresent(options::setChannel);
        return type.launch(options);
    }
}
