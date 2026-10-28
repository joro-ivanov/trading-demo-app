package com.example.demo.portfolio.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;

/** {@code POST /funds/deposit}. */
class DepositIT extends PortfolioITBase {

    private static final String DEPOSITING_OWNER = "funds-dep";
    private static final String INVALID_DEPOSIT_OWNER = "funds-bad";
    private static final String DEPOSIT_AMOUNT = "500";

    @Test
    void depositThenBalanceReflectsIt() {
        String token = token(DEPOSITING_OWNER);

        String body = api(token)
                .body(amountBody(DEPOSIT_AMOUNT))
                .post(DEPOSIT_PATH)
                .then().statusCode(HttpStatus.OK.value())
                .extract().asString();

        BigDecimal balance = balanceCash(token);
        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo(DEPOSIT_AMOUNT);
            softly.assertThat(balance).isEqualByComparingTo(DEPOSIT_AMOUNT);
        });
    }

    @ParameterizedTest(name = "amount {0} -> 400")
    @ValueSource(strings = {"-5", "0"})
    void aNonPositiveAmountIsRejectedWith400(String amount) {
        api(token(INVALID_DEPOSIT_OWNER))
                .body(amountBody(amount))
                .post(DEPOSIT_PATH)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
