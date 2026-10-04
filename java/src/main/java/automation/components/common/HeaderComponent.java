package automation.components.common;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/* app header shown after login (reusable by any page that renders it)*/
public class HeaderComponent {
    private final Locator root;
    public final Locator userEmail;

    public HeaderComponent(Page page) {
        this.root = page.getByRole(AriaRole.BANNER);
        this.userEmail = root.getByTestId("user-email");
    }

    public void logout() {
        root.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Log out")).click();
    }
}
