package com.example.demo.portfolio.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.common.json.Json;
import com.example.demo.portfolio.it.harness.PortfolioITBase;
import com.example.demo.portfolio.web.FundsDtos.BalanceResponse;

/** {@code POST /funds/withdraw}. */
class WithdrawIT extends PortfolioITBase {

    @Test
    void withdrawReducesTheBalance() {
        String token = token("funds-wd");
        deposit(token, "500");

        String body = api(token)
                .body("{\"amount\":200}")
                .post("/funds/withdraw")
                .then().statusCode(200)
                .extract().asString();

        assertThat(Json.read(body, BalanceResponse.class).cash()).isEqualByComparingTo("300");
    }

    @Test
    void withdrawingMoreThanAvailableIsRejectedWith422() {
        api(token("funds-over"))
                .body("{\"amount\":100}")
                .post("/funds/withdraw")
                .then().statusCode(422);
    }
}
