package uk.gov.hmcts.reform.blobrouter.services.storage;

import com.azure.core.http.HttpResponse;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.blobrouter.config.TargetStorageAccount;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BlobContainerClientProxyTest {

    @Mock BlobContainerClient crimeClient;
    @Mock SasTokenCache sasTokenCache;
    @Mock BlobContainerClientBuilder blobContainerClientBuilder;
    @Mock BlobContainerClientBuilderProvider blobContainerClientBuilderProvider;

    BlobContainerClientProxy blobContainerClientProxy;

    @Mock BlobContainerClient blobContainerClient;
    @Mock BlobClient blobClient;
    @Mock BlockBlobClient blockBlobClient;

    final String containerName = "container123";
    final String blobName = "hello.zip";
    final byte[] blobContent = "some data".getBytes();

    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(40);

    @BeforeEach
    private void setUp() {
        this.blobContainerClientProxy = new BlobContainerClientProxy(
            crimeClient,
            blobContainerClientBuilderProvider,
            sasTokenCache
        );
    }

    @Test
    void should_upload_to_crime_storage_when_target_storage_crime() {
        given(crimeClient.getBlobClient(blobName)).willReturn(blobClient);
        given(blobClient.getBlockBlobClient()).willReturn(blockBlobClient);

        blobContainerClientProxy.upload(
            blobName,
            blobContent,
            containerName,
            TargetStorageAccount.CRIME
        );

        // then
        ArgumentCaptor<ByteArrayInputStream> data = ArgumentCaptor.forClass(ByteArrayInputStream.class);

        verify(blockBlobClient)
            .uploadWithResponse(
                data.capture(),
                eq(Long.valueOf(blobContent.length)),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(UPLOAD_TIMEOUT),
                eq(Context.NONE)
            );

        assertThat(data.getValue().readAllBytes()).isEqualTo(blobContent);

        verify(sasTokenCache, never()).getSasToken(containerName);
    }

    @Test
    void should_upload_to_bulk_scan_storage_when_target_storage_bulk_scan() {

        given(sasTokenCache.getSasToken(any())).willReturn("token1");

        given(blobContainerClientBuilderProvider.getBlobContainerClientBuilder())
            .willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.containerName(containerName)).willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.sasToken("token1")).willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.buildClient()).willReturn(blobContainerClient);

        given(blobContainerClient.getBlobClient(blobName)).willReturn(blobClient);
        given(blobClient.getBlockBlobClient()).willReturn(blockBlobClient);


        blobContainerClientProxy.upload(
            blobName,
            blobContent,
            containerName,
            TargetStorageAccount.CFT
        );

        verify(sasTokenCache).getSasToken(containerName);

        // then
        ArgumentCaptor<ByteArrayInputStream> data = ArgumentCaptor.forClass(ByteArrayInputStream.class);

        verify(blockBlobClient)
            .uploadWithResponse(
                data.capture(),
                eq(Long.valueOf(blobContent.length)),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(UPLOAD_TIMEOUT),
                eq(Context.NONE)
            );

        assertThat(data.getValue().readAllBytes()).isEqualTo(blobContent);

        verify(sasTokenCache, never()).removeFromCache(containerName);

    }

    @ParameterizedTest
    @EnumSource(
        value = TargetStorageAccount.class,
        names = {"CFT", "PCQ"}
    )
    void should_invalidate_cache_when_upload_returns_error_response_40x(TargetStorageAccount storageAccount) {
        // given
        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        given(mockHttpResponse.getStatusCode()).willReturn(401);

        if (storageAccount == TargetStorageAccount.PCQ) {
            given(blobContainerClientBuilderProvider.getPcqBlobContainerClientBuilder())
                .willReturn(blobContainerClientBuilder);
        } else if (storageAccount == TargetStorageAccount.CFT) {
            given(blobContainerClientBuilderProvider.getBlobContainerClientBuilder())
                .willReturn(blobContainerClientBuilder);
        }

        given(blobContainerClientBuilder.sasToken(any())).willThrow(
            new BlobStorageException("Sas invalid 401", mockHttpResponse, null));

        // then
        assertThatThrownBy(
            () -> blobContainerClientProxy.upload(
                blobName,
                blobContent,
                containerName,
                storageAccount
            )
        ).isInstanceOf(BlobStorageException.class);

        verify(sasTokenCache).removeFromCache(containerName);

    }

    @Test
    void should_not_invalidate_cache_when_target_storage_crime_and_error_response_40x() {

        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        given(crimeClient.getBlobClient(any())).willThrow(
            new BlobStorageException("Sas invalid 401", mockHttpResponse, null));

        assertThatThrownBy(
            () -> blobContainerClientProxy.upload(
                blobName,
                blobContent,
                containerName,
                TargetStorageAccount.CRIME
            )
        ).isInstanceOf(BlobStorageException.class);

        verify(sasTokenCache, never()).removeFromCache(containerName);

    }

    @Test
    void should_upload_to_pcq_storage_when_target_storage_is_pcq() {
        // given
        given(sasTokenCache.getPcqSasToken(any())).willReturn("token1");

        given(blobContainerClientBuilderProvider.getPcqBlobContainerClientBuilder())
            .willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.containerName(containerName)).willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.sasToken("token1")).willReturn(blobContainerClientBuilder);
        given(blobContainerClientBuilder.buildClient()).willReturn(blobContainerClient);

        given(blobContainerClient.getBlobClient(blobName)).willReturn(blobClient);
        given(blobClient.getBlockBlobClient()).willReturn(blockBlobClient);

        // when
        blobContainerClientProxy.upload(
            blobName,
            blobContent,
            containerName,
            TargetStorageAccount.PCQ
        );

        verify(sasTokenCache).getPcqSasToken(containerName);

        // then
        ArgumentCaptor<ByteArrayInputStream> data = ArgumentCaptor.forClass(ByteArrayInputStream.class);

        verify(blockBlobClient)
            .uploadWithResponse(
                data.capture(),
                eq(Long.valueOf(blobContent.length)),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(UPLOAD_TIMEOUT),
                eq(Context.NONE)
            );

        assertThat(data.getValue().readAllBytes()).isEqualTo(blobContent);
        verify(sasTokenCache, never()).removeFromCache(containerName);
    }

}
