package com.example.demo.e2e.framework.pages;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import com.example.demo.common.json.Json;
import com.example.demo.e2e.framework.model.Login;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.ui.BasePage;
import com.example.demo.e2e.framework.ui.LocalStorage;
import com.example.demo.e2e.framework.ui.SessionStorage;

/**
 * Dashboard Page Object: place an order via the ticket form and read the portfolio panel. Playwright
 * locators/assertions are confined here (auto-waiting); tests call only these methods and assert on
 * the plain values returned (docs/DESIGN.md §8.6, §13.1).
 */
public final class DashboardPage extends BasePage {

    private static final String SYMBOL_SELECT = "select[name=symbol]";
    private static final String SIDE_SELECT = "select[name=side]";
    private static final String TYPE_SELECT = "select[name=type]";
    private static final String QUANTITY_INPUT = "input[name=quantity]";
    private static final String LIMIT_PRICE_INPUT = "input[name=limitPrice]";
    private static final String STOP_PRICE_INPUT = "input[name=stopPrice]";
    private static final String PLACE_BUTTON = "Place";
    private static final String ZERO_PRICE = "0";
    private static final String PORTFOLIO_EMPTY = ".portfolio .empty";
    private static final String PORTFOLIO_ROWS = ".portfolio tbody tr";
    private static final String TICKER_ROWS = ".ticker tr";
    private static final String TICKER_PRICE = ".price";
    private static final String FILLS = ".fills li";
    private static final String FUNDS_AMOUNT_INPUT = ".funds input[name=amount]";
    private static final String DEPOSIT_BUTTON = ".funds button:has-text('Deposit')";
    private static final String FUNDS_BALANCE = ".funds .balance";
    private static final String LOGOUT_BUTTON = "Log out";
    private static final String SESSION_USERNAME = ".session span";
    private static final String HEADER_IMAGES = ".header img";

    public DashboardPage(Page page) {
        super(page);
    }

    public DashboardPage placeOrder(String symbol, Side side, int quantity) {
        return placeOrder(symbol, side, quantity, OrderType.MARKET, ZERO_PRICE);
    }

    public DashboardPage placeOrder(String symbol, String side, int quantity) {
        return placeOrder(symbol, Side.valueOf(side), quantity);
    }

    public DashboardPage placeOrder(String symbol, String side, int quantity, String type, String price) {
        return placeOrder(symbol, Side.valueOf(side), quantity, OrderType.valueOf(type), price);
    }

    public DashboardPage placeOrder(String symbol, Side side, int quantity, OrderType type, String price) {
        logUi("place order symbol=" + symbol + " side=" + side + " quantity=" + quantity
                + " type=" + type + " price=" + price);
        page.locator(SYMBOL_SELECT).selectOption(symbol);
        page.locator(SIDE_SELECT).selectOption(side.name());
        page.locator(TYPE_SELECT).selectOption(type.name());
        if (type == OrderType.LIMIT) {
            page.locator(LIMIT_PRICE_INPUT).fill(price);
        }
        if (type == OrderType.STOP) {
            page.locator(STOP_PRICE_INPUT).fill(price);
        }
        page.locator(QUANTITY_INPUT).fill(Integer.toString(quantity));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(PLACE_BUTTON)).click();
        return this;
    }

    /** Auto-waits (up to 30s) for the portfolio row for {@code symbol} to appear. */
    public void waitForPosition(String symbol) {
        logUi("await position symbol=" + symbol);
        waitForVisible(positionRow(symbol));
    }

    public String positionRowText(String symbol) {
        return positionRow(symbol).innerText();
    }

    /** Auto-waits for the portfolio to show its empty state (no positions for this user). */
    public void awaitEmptyPortfolio() {
        logUi("await empty portfolio");
        waitForVisible(page.locator(PORTFOLIO_EMPTY));
    }

    public boolean hasPosition(String symbol) {
        return positionRow(symbol).count() > 0;
    }

    public void awaitPositionRemoved(String symbol) {
        logUi("await position removed symbol=" + symbol);
        waitForCount(positionRow(symbol), 0);
    }

    public void refresh() {
        logUi("refresh page");
        page.reload();
    }

    public String tickerPrice(String symbol) {
        return page.locator(TICKER_ROWS)
                .filter(new Locator.FilterOptions().setHasText(symbol))
                .locator(TICKER_PRICE)
                .innerText();
    }

    /** Auto-waits for the ticker to show a real (formatted "$…") price for {@code symbol}. */
    public void awaitTickerPrice(String symbol) {
        logUi("await ticker price symbol=" + symbol);
        Locator price = page.locator(TICKER_ROWS)
                .filter(new Locator.FilterOptions().setHasText(symbol))
                .locator(TICKER_PRICE);
        waitForTextContains(price, "$");
    }

    /** Auto-waits for at least one fill to appear in the fills panel. */
    public void awaitFill() {
        logUi("await fill");
        waitForVisible(page.locator(FILLS).first());
    }

    public void awaitFillCount(int count) {
        logUi("await fill count=" + count);
        waitForCount(page.locator(FILLS), count);
    }

    public int fillCount() {
        return page.locator(FILLS).count();
    }

    public String firstFillText() {
        return page.locator(FILLS).first().innerText();
    }

    public List<String> fillTexts() {
        return page.locator(FILLS).allInnerTexts();
    }

    public DashboardPage deposit(String amount) {
        logUi("deposit amount=" + amount);
        page.locator(FUNDS_AMOUNT_INPUT).fill(amount);
        page.locator(DEPOSIT_BUTTON).click();
        return this;
    }

    /** Auto-waits for the funds panel's balance to contain the given text (e.g. "$1,000.00"). */
    public void awaitBalance(String containing) {
        logUi("await balance contains=" + containing);
        waitForTextContains(page.locator(FUNDS_BALANCE), containing);
    }

    public BigDecimal balanceAmount() {
        return parseMoney(page.locator(FUNDS_BALANCE).innerText());
    }

    private Locator positionRow(String symbol) {
        return page.locator(PORTFOLIO_ROWS).filter(new Locator.FilterOptions().setHasText(symbol));
    }

    // ── security probes ────────────────────────────────────────────────────────

    public void logout() {
        logUi("logout");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGOUT_BUTTON)).click();
    }

    /** Auto-waits for the header to show the given username (used to assert output encoding). */
    public void awaitSessionUsername(String username) {
        logUi("await session username=" + username);
        waitForText(page.locator(SESSION_USERNAME), username);
    }

    /** Number of real {@code <img>} elements in the header — 0 unless markup was injected unescaped. */
    public int headerImageCount() {
        return page.locator(HEADER_IMAGES).count();
    }

    /** True if any local-storage value looks like a JWT (persistent browser storage must stay empty). */
    public boolean tokenIsInLocalStorage() {
        return tokenIsStored(LocalStorage.getItem("session"));
    }

    /** True if any session-storage value looks like a JWT (survives refresh in the same tab). */
    public boolean tokenIsInSessionStorage() {
        return tokenIsStored(SessionStorage.getItem("session"));
    }

    public void awaitTokenInSessionStorage() {
        logUi("await token in sessionStorage");
        page.waitForFunction("() => sessionStorage.getItem('session')");
    }

    private static boolean tokenIsStored(String sessionJson) {
        if (sessionJson == null || sessionJson.isBlank()) {
            return false;
        }
        Login session = Json.read(sessionJson, Login.class);
        String token = session.token();
        return token != null && token.matches("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$");
    }

    private static BigDecimal parseMoney(String text) {
        Matcher matcher = Pattern.compile("\\$(-?[\\d,]+(?:\\.\\d+)?)").matcher(text);

        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "No dollar amount found in balance text: " + text);
        }

        return new BigDecimal(matcher.group(1).replace(",", ""));
    }
}
