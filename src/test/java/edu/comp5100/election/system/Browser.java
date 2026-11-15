package edu.comp5100.election.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * One real headless Chrome window driven through Selenium WebDriver. Creating it fails loudly
 * when Chrome or its driver is not available; nothing in the system suite is skipped silently.
 * All waiting is condition-based (no fixed sleeps).
 */
public final class Browser implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String baseUrl;

    public Browser(int port) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1366,1000", "--lang=en-US", "--no-first-run",
                "--disable-search-engine-choice-screen", "--force-device-scale-factor=1", "--hide-scrollbars");
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", "en-US,en"));
        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, TIMEOUT);
        this.baseUrl = "http://localhost:" + port;
    }

    public WebDriver driver() {
        return driver;
    }

    public void open(String path) {
        driver.get(baseUrl + path);
    }

    /** Signs in through the real login form, exactly as a person would. */
    public void login(String username, String password) {
        open("/login");
        type(By.id("username"), username);
        type(By.id("password"), password);
        click(By.id("login-submit"));
        waitUntil(() -> !path().startsWith("/login"));
    }

    public void logout() {
        click(By.cssSelector(".session button[type='submit']"));
        waitUntil(() -> driver.getCurrentUrl().contains("/login?logout"));
    }

    public WebElement find(By locator) {
        return wait.ignoring(StaleElementReferenceException.class).until(current -> {
            List<WebElement> found = current.findElements(locator);
            return found.isEmpty() || !found.get(0).isDisplayed() ? null : found.get(0);
        });
    }

    public List<WebElement> all(By locator) {
        return driver.findElements(locator);
    }

    public boolean exists(By locator) {
        return !driver.findElements(locator).isEmpty();
    }

    public void click(By locator) {
        find(locator).click();
    }

    public void type(By locator, String text) {
        WebElement field = find(locator);
        field.clear();
        field.sendKeys(text);
    }

    public String text(By locator) {
        return find(locator).getText().trim();
    }

    public String attribute(By locator, String name) {
        return find(locator).getDomAttribute(name);
    }

    /** Path and query of the current address, without scheme, host and port. */
    public String path() {
        return driver.getCurrentUrl().substring(baseUrl.length());
    }

    public String pageSource() {
        return driver.getPageSource();
    }

    /** Visible text of the whole page. */
    public String bodyText() {
        return find(By.tagName("body")).getText();
    }

    public void waitUntil(Supplier<Boolean> condition) {
        wait.ignoring(StaleElementReferenceException.class).until(current -> condition.get());
    }

    public void waitForText(By locator, String expected) {
        waitUntil(() -> {
            List<WebElement> found = driver.findElements(locator);
            return !found.isEmpty() && found.get(0).getText().contains(expected);
        });
    }

    /** Selects the radio button of the candidate with this exact name on the ballot page. */
    public void chooseCandidate(String candidateName) {
        click(By.xpath("//div[contains(@class,'choice')][.//span[contains(@class,'choice-name') and normalize-space()='"
                + candidateName + "']]//label"));
        waitUntil(() -> find(By.xpath("//div[contains(@class,'choice')][.//span[contains(@class,'choice-name') "
                + "and normalize-space()='" + candidateName + "']]//input")).isSelected());
    }

    /** Presses keys on the real keyboard pipeline of the browser; they go to whatever has the focus. */
    public void pressKeys(CharSequence... keys) {
        new Actions(driver).sendKeys(keys).perform();
    }

    public WebElement focused() {
        return driver.switchTo().activeElement();
    }

    public void openNewTab() {
        driver.switchTo().newWindow(WindowType.TAB);
    }

    public void switchToTab(int index) {
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(handles.get(index));
    }

    public Object script(String javascript, Object... arguments) {
        return ((JavascriptExecutor) driver).executeScript(javascript, arguments);
    }

    public void resize(int width, int height) {
        driver.manage().window().setSize(new Dimension(width, height));
    }

    public void screenshot(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save screenshot " + file, failure);
        }
    }

    @Override
    public void close() {
        driver.quit();
    }
}
