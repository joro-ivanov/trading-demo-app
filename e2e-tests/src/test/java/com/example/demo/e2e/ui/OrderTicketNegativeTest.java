package com.example.demo.e2e.ui;

import static com.example.demo.e2e.framework.support.HttpStatusCodes.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.demo.e2e.framework.model.OrderType;
import com.example.demo.e2e.framework.pages.OrderTicketPage;

import io.qameta.allure.Allure;

/**
 * Negative + field-validation UI e2e for the order ticket (scenarios reviewed with the
 * grizzled-trader). Backend responses are simulated with Playwright <b>network interception</b>
 * (owned by {@link OrderTicketPage}), so these are deterministic and need no market-hours gate — the
 * order POST is intercepted and never reaches the server. The core risk on this ticket is that
 * <i>success is silent</i>: the only signal a trader gets is the absence of an error line, so these
 * tests prove the UI never looks idle/submitted when the order did not go through, and that the
 * in-flight lock prevents a double-fire.
 */
class OrderTicketNegativeTest extends BaseUiTest {

    /** Below the quantity field's minimum. */
    private static final String ZERO_QUANTITY = "0";
    private static final String VALID_QUANTITY = "10";

    private OrderTicketPage openTicket() {
        loginAsPrimaryUser();
        return orderTicketPage();
    }

    // A rejected order must look rejected (not idle/submitted) and leave the ticket usable again.
    // 422 = market-closed OR short-sell; 401 = expired/invalid session; 400 = bad input; 500 = server.
    @ParameterizedTest(name = "a {0} response shows ''Order rejected ({0})''")
    @ValueSource(ints = {400, 401, 422, 500})
    void rejectedOrderSurfacesAnErrorAndUnlocksTheTicket(int status) {
        OrderTicketPage ticket = openTicket();
        Allure.step("The order POST is rejected with " + status);
        ticket.stubOrders(status);
        ticket.clickPlace();
        ticket.awaitOrderRejected(status);
        ticket.awaitButtonIdle();
        assertThat(ticket.orderRequestCount()).isEqualTo(1);
    }

    // Double-submit guard: while the order is in flight the button is disabled, so a trader cannot
    // fire twice — exactly one request leaves the browser.
    @Test
    void theTicketLocksWhileAnOrderIsInFlight() {
        OrderTicketPage ticket = openTicket();
        Allure.step("The order POST stays in flight long enough to assert the locked state");
        ticket.hangOrders();
        ticket.clickPlace();
        ticket.awaitButtonPendingAndDisabled();
        assertThat(ticket.orderRequestCount()).isEqualTo(1);
    }

    // Backend down: the failure surfaces as an error and the ticket is not left looking idle.
    @Test
    void aBackendFailureSurfacesAnErrorAndDoesNotLookSubmitted() {
        OrderTicketPage ticket = openTicket();
        Allure.step("The order POST is aborted (backend down)");
        ticket.abortOrders();
        ticket.clickPlace();
        ticket.awaitOrderErrorVisible();
        ticket.awaitButtonIdle();
    }

    // Hung backend: the trader stays visibly blocked (button locked) with no false success/failure.
    @Test
    void aHungBackendKeepsTheTicketLockedWithNoFalseOutcome() {
        OrderTicketPage ticket = openTicket();
        Allure.step("The order POST stays in flight long enough to look hung to the UI");
        ticket.hangOrders();
        ticket.clickPlace();
        ticket.awaitButtonPendingAndDisabled();
        assertThat(ticket.hasOrderError()).isFalse();
    }

    // Field validation (UI-owned): the conditional price field appears only for the matching type.
    @Test
    void theOrderTypeRevealsOnlyTheRelevantPriceField() {
        OrderTicketPage ticket = openTicket();
        ticket.selectType(OrderType.MARKET);
        assertSoftly(softly -> {
            softly.assertThat(ticket.limitPriceFieldPresent()).isFalse();
            softly.assertThat(ticket.stopPriceFieldPresent()).isFalse();
        });
        ticket.selectType(OrderType.LIMIT);
        assertSoftly(softly -> {
            softly.assertThat(ticket.limitPriceFieldPresent()).isTrue();
            softly.assertThat(ticket.stopPriceFieldPresent()).isFalse();
        });
        ticket.selectType(OrderType.STOP);
        assertSoftly(softly -> {
            softly.assertThat(ticket.stopPriceFieldPresent()).isTrue();
            softly.assertThat(ticket.limitPriceFieldPresent()).isFalse();
        });
    }

    // Field validation: a zero quantity violates the field's minimum (so it can't reach the wire).
    @Test
    void theQuantityFieldRejectsBelowItsMinimum() {
        OrderTicketPage ticket = openTicket();
        ticket.fillQuantity(ZERO_QUANTITY);
        assertThat(ticket.quantityIsValid()).isFalse();
        ticket.fillQuantity(VALID_QUANTITY);
        assertThat(ticket.quantityIsValid()).isTrue();
    }

    // Documents the trust model: a successful order is silent on the ticket (no error, exactly one
    // request). A future regression that adds a phantom error or a second request gets caught here.
    @Test
    void aSuccessfulOrderIsSilentOnTheTicket() {
        OrderTicketPage ticket = openTicket();
        Allure.step("The order POST succeeds (201)");
        ticket.stubOrders(CREATED);
        ticket.clickPlace();
        ticket.awaitButtonIdle();
        assertSoftly(softly -> {
            softly.assertThat(ticket.hasOrderError()).isFalse();
            softly.assertThat(ticket.orderRequestCount()).isEqualTo(1);
        });
    }
}
