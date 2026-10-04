from playwright.sync_api import Page


class BasePage:
    """base for all page objects. Pages expose actions and locators/state (they never assert)
    navigation is relative to the frontend base URL (the ``base_url`` fixture)
    """

    path: str = "/"

    def __init__(self, page: Page) -> None:
        self._page = page

    def goto(self) -> None:
        self._page.goto(self.path)
