from playwright.sync_api import Locator, Page


class HeaderComponent:
    """app header shown after login, reusable by any page that renders it"""

    def __init__(self, page: Page) -> None:
        self._root: Locator = page.get_by_role("banner")
        self.user_email: Locator = self._root.get_by_test_id("user-email")

    def logout(self) -> None:
        self._root.get_by_role("button", name="Log out").click()
