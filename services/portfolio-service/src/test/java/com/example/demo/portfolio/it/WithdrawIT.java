package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;

/** {@code POST /funds/withdraw}. */
class WithdrawIT extends PortfolioITBase {

    private static final String WITHDRAWING_OWNER = "funds-wd";
    private static final String OVERDRAWING_OWNER = "funds-over";
    private static final String DEPOSIT_AMOUNT = "500";
    private static final String WITHDRAWAL_AMOUNT = "200";
    private static final String REMAINING_CASH = "300";
    /** The overdrawing owner never deposited, so any positive withdrawal exceeds their cash. */
    private static final String OVERDRAW_AMOUNT = "100";

    @Test
    void withdrawReducesTheBalance() {
        String token = token(WITHDRAWING_OWNER);
        deposit(token, DEPOSIT_AMOUNT);

        String body = api(token)
                .body(amountBody(WITHDRAWAL_AMOUNT))
                .post(WITHDRAW_PATH)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo(REMAINING_CASH);
    }

    @Test
    void withdrawingMoreThanAvailableIsRejectedWith422() {
        api(token(OVERDRAWING_OWNER))
                .body(amountBody(OVERDRAW_AMOUNT))
                .post(WITHDRAW_PATH)
                .then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }
}
