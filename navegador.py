"""API local de automação para a WebView do PyBrowser."""

import json
import time


class NoSuchElementException(LookupError):
    pass


class TimeoutException(TimeoutError):
    pass


class By:
    ID = "id"
    NAME = "name"
    CLASS_NAME = "class name"
    TAG_NAME = "tag name"
    CSS_SELECTOR = "css selector"
    XPATH = "xpath"
    LINK_TEXT = "link text"
    PARTIAL_LINK_TEXT = "partial link text"


class Browser:
    def __init__(self, bridge):
        self._bridge = bridge

    def _command(self, operation, *args):
        bridge = _current._bridge if _current is not None else self._bridge
        return json.loads(str(bridge.command(operation, json.dumps(args))))

    def get(self, url):
        self._command("get", str(url))

    def _find(self, parent, by, value, multiple):
        result = self._command("find", parent, by, value, multiple)
        if multiple:
            return [Element(self, key) for key in result]
        if result is None:
            raise NoSuchElementException(f"Elemento ausente: {by}={value!r}")
        return Element(self, result)

    def find_element(self, by=By.ID, value=None):
        return self._find(None, by, value, False)

    def find_elements(self, by=By.ID, value=None):
        return self._find(None, by, value, True)

    @property
    def title(self):
        return self._command("title")

    @property
    def current_url(self):
        return self._command("url")

    @property
    def page_source(self):
        return self._command("html")

    def scroll_by(self, x=0, y=0):
        self._command("scroll", x, y)

    def execute_script(self, source, *args):
        arguments = [
            ["element", arg._key] if isinstance(arg, Element) else ["value", arg]
            for arg in args
        ]
        return self._command("script", source, arguments)


class Element:
    def __init__(self, browser, key):
        self._browser = browser
        self._key = key

    def _command(self, operation, *args):
        return self._browser._command(operation, self._key, *args)

    def click(self):
        self._command("click")

    def send_keys(self, *values):
        self._command("type", "".join(map(str, values)))

    def clear(self):
        self._command("clear")

    @property
    def text(self):
        return self._command("text")

    def get_attribute(self, name):
        return self._command("attribute", name)

    def get_property(self, name):
        return self._command("property", name)

    def is_displayed(self):
        return self._command("visible")

    def is_enabled(self):
        return self._command("enabled")

    def find_element(self, by=By.ID, value=None):
        return self._browser._find(self._key, by, value, False)

    def find_elements(self, by=By.ID, value=None):
        return self._browser._find(self._key, by, value, True)


class WebDriverWait:
    def __init__(self, browser, timeout, poll_frequency=0.2):
        self.browser = browser
        self.timeout = float(timeout)
        self.poll_frequency = max(0.02, float(poll_frequency))

    def until(self, condition):
        end = time.monotonic() + self.timeout
        while True:
            try:
                result = condition(self.browser)
                if result:
                    return result
            except NoSuchElementException:
                pass
            remaining = end - time.monotonic()
            if remaining <= 0:
                raise TimeoutException("A condição não foi atendida no tempo previsto.")
            time.sleep(min(remaining, self.poll_frequency))


class EC:
    @staticmethod
    def presence_of_element_located(locator):
        return lambda browser: browser.find_element(*locator)

    @staticmethod
    def element_to_be_clickable(locator):
        def check(browser):
            element = browser.find_element(*locator)
            return element if element.is_displayed() and element.is_enabled() else False
        return check


class _CurrentBrowser:
    def __getattr__(self, name):
        if _current is None:
            raise RuntimeError("Nenhum script Python está em execução.")
        return getattr(_current, name)


_current = None
driver = _CurrentBrowser()

○
○

