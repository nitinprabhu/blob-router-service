package uk.gov.hmcts.reform.blobrouter.services.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.blobrouter.config.TargetStorageAccount.CFT;

@ExtendWith(MockitoExtension.class)
class BlobDispatcherTest {

    @Mock BlobContainerClientProxy blobContainerClientProxy;
    @Mock BlobContainerClient blobContainerClient;
    @Mock BlobClient blobClient;
    @Mock BlockBlobClient blockBlobClient;

    private BlobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new BlobDispatcher(blobContainerClientProxy);
    }

    @Test
    void should_use_blob_client_to_dispatch_file() {
        // given
        final String blobName = "hello.zip";
        final byte[] blobContent = "some data".getBytes();
        final String container = "container";

        doNothing().when(blobContainerClientProxy).upload(blobName, blobContent, container, CFT);

        // when
        dispatcher.dispatch(blobName, blobContent, container, CFT);

        // then
        verify(blobContainerClientProxy)
            .upload(blobName, blobContent, container, CFT);

    }

    @Test
    void should_rethrow_exceptions() {
        // given
        willThrow(new BlobStorageException("test exception", null, null))
            .given(blobContainerClientProxy)
            .upload(any(), any(), any(), any());

        // when
        Throwable exc = catchThrowable(
            () -> dispatcher.dispatch("foo.zip", "data".getBytes(), "some_container", CFT)
        );

        // then
        assertThat(exc).isInstanceOf(BlobStorageException.class);
    }
}
