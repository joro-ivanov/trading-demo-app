package com.example.demo.watchlist.client;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.demo.common.http.HttpClients;
import com.example.demo.common.security.Jwt;
import com.example.demo.watchlist.config.MarketDataProperties;

/**
 * market-data-service over HTTP: symbol validation ({@code GET /prices/{symbol}}) and batch prices
 * ({@code GET /prices?symbols=}). Each call carries a short-lived token for the acting owner. Any
 * failure other than "unknown symbol" surfaces as {@link MarketDataUnavailableException}.
 */
@Component
public class MarketDataClient {

    private static final long TOKEN_TTL_SECONDS = 60;

    private final RestClient restClient;
    private final String secret;

    public MarketDataClient(RestClient.Builder builder,
                            MarketDataProperties properties,
                            @Value("${security.jwt.secret}") String secret) {
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(HttpClients.withTimeouts(properties.connectTimeout(), properties.readTimeout()))
                .build();
        this.secret = secret;
    }

    /** Whether market-data knows the symbol (has a price for it). */
    public boolean isKnownSymbol(String symbol, String owner) {
        try {
            restClient.get()
                    .uri("/prices/{symbol}", symbol)
                    .header("Authorization", bearer(owner))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound unknown) {
            return false;
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("market-data lookup failed for " + symbol, failure);
        }
    }

    /**
     * Latest prices for {@code symbols} in one call, keyed by symbol. Symbols market-data does not
     * know are absent from the map.
     */
    public Map<String, BigDecimal> latestPrices(Collection<String> symbols, String owner) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        try {
            PriceResponse[] prices = restClient.get()
                    .uri(uri -> uri.path("/prices").queryParam("symbols", String.join(",", symbols)).build())
                    .header("Authorization", bearer(owner))
                    .retrieve()
                    .body(PriceResponse[].class);
            Map<String, BigDecimal> bySymbol = new LinkedHashMap<>();
            for (PriceResponse price : prices == null ? new PriceResponse[0] : prices) {
                bySymbol.put(price.symbol(), price.price());
            }
            return bySymbol;
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("market-data batch price lookup failed for " + List.copyOf(symbols),
                    failure);
        }
    }

    private String bearer(String owner) {
        return "Bearer " + Jwt.issue(owner, secret, TOKEN_TTL_SECONDS);
    }

    private record PriceResponse(String symbol, BigDecimal price) {
    }
}
