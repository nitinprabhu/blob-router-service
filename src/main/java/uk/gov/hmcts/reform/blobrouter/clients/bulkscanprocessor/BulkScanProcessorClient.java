package uk.gov.hmcts.reform.blobrouter.clients.bulkscanprocessor;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(
    name = "bulk-scan-processor-client",
    url = "${bulk-scan-processor-url}"
)
public interface BulkScanProcessorClient {

    @GetMapping(value = "/token/{service}", consumes = APPLICATION_JSON_VALUE)
    SasTokenResponse getSasToken(@PathVariable String service);
}