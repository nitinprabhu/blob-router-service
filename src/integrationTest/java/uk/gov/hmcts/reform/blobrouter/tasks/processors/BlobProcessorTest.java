package uk.gov.hmcts.reform.blobrouter.tasks.processors;

import com.azure.core.test.TestBase;
import com.azure.storage.blob.BlobServiceClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.blobrouter.data.DbHelper;
import uk.gov.hmcts.reform.blobrouter.data.EnvelopeRepository;
import uk.gov.hmcts.reform.blobrouter.data.model.NewEnvelope;
import uk.gov.hmcts.reform.blobrouter.data.model.Status;
import uk.gov.hmcts.reform.blobrouter.services.storage.BlobDispatcher;
import uk.gov.hmcts.reform.blobrouter.util.StorageClientsHelper;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ActiveProfiles("db-test")
@SpringBootTest
class BlobProcessorTest extends TestBase {

    private static final String NEW_BLOB_NAME = "new.blob";
    private static final String CONTAINER = "bulkscan";
    private static final String BOGUS_CONTAINER = "bogus";

    @Autowired
    private EnvelopeRepository envelopeRepository;
    @Autowired
    private DbHelper dbHelper;

    private BlobDispatcher dispatcher;
    private BlobProcessor blobProcessor;

    @BeforeAll
    static void setUpTestMode() {
        StorageClientsHelper.setAzureTestMode();

        setupClass();
    }

    @Override
    protected void beforeTest() {
        // cleanup db
        dbHelper.deleteAll();

        // set up processor
        BlobServiceClient storageClient = StorageClientsHelper.getStorageClient(interceptorManager);
        dispatcher = spy(new BlobDispatcher(storageClient));
        blobProcessor = new BlobProcessor(storageClient, dispatcher, envelopeRepository);
    }

    @Test
    void should_find_blobs_and_process() {
        // when
        blobProcessor.process(NEW_BLOB_NAME, CONTAINER);

        // then
        assertThat(envelopeRepository.find(NEW_BLOB_NAME, CONTAINER))
            .isNotEmpty()
            .map(envelope -> envelope.status)
            .contains(Status.DISPATCHED);
    }

    @Test
    void should_catch_BlobStorageException_and_suppress_it() {
        // when
        blobProcessor.process(NEW_BLOB_NAME, BOGUS_CONTAINER);

        // then
        assertThat(envelopeRepository.find(NEW_BLOB_NAME, BOGUS_CONTAINER)).isEmpty();
    }

    @Test
    void should_not_process_blob_if_present_in_db() {
        // given
        NewEnvelope newEnvelope = new NewEnvelope(
            CONTAINER,
            NEW_BLOB_NAME,
            now(),
            now().plusSeconds(100),
            Status.REJECTED
        );
        envelopeRepository.insert(newEnvelope);

        // when
        blobProcessor.process(NEW_BLOB_NAME, CONTAINER);

        // then
        verify(dispatcher, never()).dispatch(eq(NEW_BLOB_NAME), any(), eq(CONTAINER));
    }
}