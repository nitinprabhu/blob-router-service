package uk.gov.hmcts.reform.blobrouter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Disabled
public class ReconciliationApiTest {

    private static final String RECONCILIATION_ENDPOINT_PATH = "/reconciliation-report/{date}";

    private static Config config;
    private static String apiGatewayUrl;
    private static String validApiKey;

    @BeforeAll
    static void loadConfig() {
        config = ConfigFactory.load();
        apiGatewayUrl = getApiGatewayUrl();
        validApiKey = getValidReconciliationApiKey();
    }

    @Test
    void should_accept_request_with_valid_api_key() {
        // sends request with valid api Key and invalid body
        String invalidStatementsReport = "{\"test\": {}}";
        Response response = callReconciliationEndpoint(validApiKey, invalidStatementsReport).thenReturn();

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.value());
    }

    @Test
    void should_reject_request_with_invalid_api_key() {
        String validStatementsReport = "{\"envelopes\": []}";
        Response response = callReconciliationEndpoint("invalid-api-key123", validStatementsReport).thenReturn();

        assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED.value());
        assertThat(response.body().asString()).contains("Invalid API Key");
    }

    @Test
    void should_reject_request_without_api_key() {
        String validStatementsReport = "{\"envelopes\": []}";
        Response response = callReconciliationEndpoint("invalid-api-key123", validStatementsReport).thenReturn();

        assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED.value());
        assertThat(response.body().asString()).contains("API Key is missing");
    }

    @Test
    void should_not_expose_http_version() {
        String invalidStatementsReport = "{\"test\": {}}";
        Response response = RestAssured
            .given()
            .baseUri(apiGatewayUrl.replace("https://", "http://"))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Blob Router Service Smoke test")
            .body(invalidStatementsReport)
            .when()
            .post(RECONCILIATION_ENDPOINT_PATH, LocalDate.now().toString())
            .thenReturn();

        assertThat(response.statusCode()).isEqualTo(NOT_FOUND.value());
        assertThat(response.body().asString()).contains("Resource not found");
    }

    private Response callReconciliationEndpoint(String apiKey, String statementsReport) {
        return RestAssured
            .given()
            .baseUri(apiGatewayUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, apiKey)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Blob Router Service Smoke test")
            .body(statementsReport)
            .post(RECONCILIATION_ENDPOINT_PATH, LocalDate.now().toString());
    }

    private static String getApiGatewayUrl() {
        String apiUrl = config.resolve().getString("api-gateway-url");
        assertThat(apiUrl).as("API gateway URL").isNotEmpty();
        return apiUrl;
    }

    private static String getValidReconciliationApiKey() {
        String apiKey = config.resolve().getString("test_reconciliation_api_key");
        assertThat(apiKey).as("Reconciliation API Key").isNotEmpty();
        return "Bearer " + apiKey;
    }

}