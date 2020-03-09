package uk.gov.hmcts.reform.blobrouter.tasks.processors;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.blobrouter.data.envelopes.Envelope;
import uk.gov.hmcts.reform.blobrouter.services.EnvelopeService;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Component
public class DuplicateFinder {

    private final BlobServiceClient storageClient;
    private final EnvelopeService envelopeService;

    public DuplicateFinder(
        BlobServiceClient storageClient,
        EnvelopeService envelopeService
    ) {
        this.storageClient = storageClient;
        this.envelopeService = envelopeService;
    }

    public List<Envelope> findIn(String containerName) {
        return storageClient
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .map(blob -> envelopeService.findEnvelope(blob.getName(), containerName))
            .flatMap(Optional::stream)
            .filter(envelope -> envelope.isDeleted) // is deleted -> has already been processed before
            .collect(toList());
    }
}
