package automation.pages;

import com.microsoft.playwright.Page;

/**
 * base for all page objects, pages expose actions and locators/state
 * navigation is relative to the frontend baseURL configured on the browser context
 */
public abstract class BasePage {
    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    protected abstract String path();

    public void goTo() {
        page.navigate(path());
    }
}
