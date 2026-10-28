package com.example.demo.portfolio.it;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;

/** {@code POST /funds/deposit}. */
class DepositIT extends PortfolioITBase {

    @Test
    void depositThenBalanceReflectsIt() {
        String token = token("funds-dep");

        String body = api(token)
                .body("{\"amount\":500}")
                .post("/funds/deposit")
                .then().statusCode(200)
                .extract().asString();

        BigDecimal balance = balanceCash(token);
        assertSoftly(softly -> {
            softly.assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo("500");
            softly.assertThat(balance).isEqualByComparingTo("500");
        });
    }

    @ParameterizedTest(name = "amount {0} -> 400")
    @ValueSource(strings = {"-5", "0"})
    void aNonPositiveAmountIsRejectedWith400(String amount) {
        api(token("funds-bad"))
                .body("{\"amount\":" + amount + "}")
                .post("/funds/deposit")
                .then().statusCode(400);
    }
}
