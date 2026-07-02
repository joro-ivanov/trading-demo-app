package com.example.demo.e2e.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import com.example.demo.e2e.framework.model.OrderStatus;
import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.model.Side;
import com.example.demo.e2e.framework.model.TradingSymbols;
import com.example.demo.e2e.framework.ui.BasePage;
import com.example.demo.e2e.framework.ui.RouteStub;

/**
 * Order-ticket Page Object for negative / field-validation scenarios. It owns the Playwright
 * <b>network interception</b> so the e2e tests never touch the Playwright API (ArchUnit §13.1): the
 * ticket's {@code POST /api/orders} can be stubbed with any status, aborted (backend down) or left
 * in flight long enough to simulate a hung backend. Tests drive only these intent-revealing methods
 * and assert on the returned values or via the auto-waiting {@code await*} methods.
 */
public final class OrderTicketPage extends BasePage {

    private static final String ORDERS_ROUTE = "**/api/orders";
    private static final String TYPE_SELECT = "select[name=type]";
    private static final String QUANTITY_INPUT = "input[name=quantity]";
    private static final String LIMIT_PRICE_INPUT = "input[name=limitPrice]";
    private static final String STOP_PRICE_INPUT = "input[name=stopPrice]";
    private static final String PLACE = ".order button[type=submit]";
    private static final String ERROR = ".order .error";
    private static final String STUB_ORDER_ID = "stub-order";
    private static final int STUB_QUANTITY = 10;

    private final RouteStub orders = intercept(ORDERS_ROUTE);

    public OrderTicketPage(Page page) {
        super(page);
    }

    // ── network interception (register before placing) ───────────────────────

    /** Fulfil every order POST with the given HTTP status and a body compatible with the UI client. */
    public OrderTicketPage stubOrders(int status) {
        logUi("stub orders status=" + status);
        orders.fulfill(status, status >= 200 && status < 300 ? successfulOrderBody() : "{}");
        return this;
    }

    /** Abort every order POST at the transport layer (simulates the backend being down). */
    public OrderTicketPage abortOrders() {
        logUi("abort orders");
        orders.abort();
        return this;
    }

    /** Keep the order POST in flight for a while (simulates a hung backend to the UI). */
    public OrderTicketPage hangOrders() {
        logUi("hang orders");
        orders.hang();
        return this;
    }

    public int orderRequestCount() {
        return orders.count();
    }

    // ── ticket interaction ───────────────────────────────────────────────────

    public OrderTicketPage selectType(String type) {
        return selectType(OrderType.valueOf(type));
    }

    public OrderTicketPage selectType(OrderType type) {
        logUi("select order type=" + type);
        page.locator(TYPE_SELECT).selectOption(type.name());
        return this;
    }

    public OrderTicketPage fillQuantity(String quantity) {
        logUi("fill order quantity=" + quantity);
        page.locator(QUANTITY_INPUT).fill(quantity);
        return this;
    }

    public OrderTicketPage clickPlace() {
        logUi("click place order");
        page.locator(PLACE).click();
        return this;
    }

    // ── queries (plain values for the test to assert) ────────────────────────

    public boolean limitPriceFieldPresent() {
        return page.locator(LIMIT_PRICE_INPUT).count() > 0;
    }

    public boolean stopPriceFieldPresent() {
        return page.locator(STOP_PRICE_INPUT).count() > 0;
    }

    public boolean quantityIsValid() {
        return (boolean) page.locator(QUANTITY_INPUT).evaluate("el => el.checkValidity()");
    }

    public boolean hasOrderError() {
        return page.locator(ERROR).count() > 0;
    }

    // ── auto-waiting assertions (Playwright web-first, like DashboardPage.waitForPosition) ──

    public OrderTicketPage awaitOrderRejected(int status) {
        logUi("await order rejected status=" + status);
        waitForText(page.locator(ERROR), "Order rejected (" + status + ")");
        return this;
    }

    public OrderTicketPage awaitOrderErrorVisible() {
        logUi("await order error visible");
        waitForVisible(page.locator(ERROR));
        return this;
    }

    public OrderTicketPage awaitButtonPendingAndDisabled() {
        logUi("await place button pending and disabled");
        Locator button = page.locator(PLACE);
        waitForDisabled(button);
        waitForTextContains(button, "Placing");
        return this;
    }

    public OrderTicketPage awaitButtonIdle() {
        logUi("await place button idle");
        Locator button = page.locator(PLACE);
        waitForEnabled(button);
        waitForText(button, "Place");
        return this;
    }

    private static String successfulOrderBody() {
        return """
                {
                  "id": "%s",
                  "symbol": "%s",
                  "side": "%s",
                  "quantity": %d,
                  "type": "%s",
                  "limitPrice": null,
                  "stopPrice": null,
                  "status": "%s"
                }""".formatted(
                        STUB_ORDER_ID,
                        TradingSymbols.AAPL,
                        Side.BUY,
                        STUB_QUANTITY,
                        OrderType.MARKET,
                        OrderStatus.SUBMITTED);
    }
}
