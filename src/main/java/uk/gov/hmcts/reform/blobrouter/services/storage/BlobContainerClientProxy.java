package uk.gov.hmcts.reform.blobrouter.services.storage;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.blobrouter.config.TargetStorageAccount;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class BlobContainerClientProxy {

    private static final Logger logger = getLogger(BlobContainerClientProxy.class);

    private final BlobContainerClient crimeClient;
    private final BlobContainerClientBuilderProvider blobContainerClientBuilderProvider;
    private final SasTokenCache sasTokenCache;

    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(40);

    public BlobContainerClientProxy(
        @Qualifier("crime-storage-client") BlobContainerClient crimeClient,
        BlobContainerClientBuilderProvider blobContainerClientBuilderProvider,
        SasTokenCache sasTokenCache
    ) {
        this.crimeClient = crimeClient;
        this.blobContainerClientBuilderProvider = blobContainerClientBuilderProvider;
        this.sasTokenCache = sasTokenCache;
    }

    private BlobContainerClient get(TargetStorageAccount targetStorageAccount, String containerName) {
        switch (targetStorageAccount) {
            case CFT:
                return blobContainerClientBuilderProvider
                    .getBlobContainerClientBuilder()
                    .sasToken(sasTokenCache.getSasToken(containerName))
                    .containerName(containerName)
                    .buildClient();
            case CRIME:
                return crimeClient;
            case PCQ:
                return blobContainerClientBuilderProvider
                    .getPcqBlobContainerClientBuilder()
                    .sasToken(sasTokenCache.getPcqSasToken(containerName))
                    .containerName(containerName)
                    .buildClient();
            default:
                throw new UnknownStorageAccountException(
                    String.format("Client requested for an unknown storage account: %s", targetStorageAccount)
                );
        }
    }

    public void upload(
        String blobName,
        byte[] blobContents,
        String destinationContainer,
        TargetStorageAccount targetStorageAccount
    ) {
        long uploadStartTime = 0;
        try {
            final BlockBlobClient blockBlobClient =
                get(targetStorageAccount, destinationContainer)
                    .getBlobClient(blobName)
                    .getBlockBlobClient();

            logger.info("Uploading content of blob {} to Container: {}", blobName, destinationContainer);
            uploadStartTime = System.currentTimeMillis();
            blockBlobClient
                .uploadWithResponse(
                    new ByteArrayInputStream(blobContents),
                    blobContents.length,
                    null,
                    null,
                    null,
                    null,
                    null,
                    UPLOAD_TIMEOUT,
                    Context.NONE
            );

            logger.info("Finished uploading content of blob {} to Container: {}", blobName, destinationContainer);
        } catch (HttpResponseException ex) {
            logger.info(
                "Uploading failed for blob {} to Container: {}, upload duration in Ms: {}, error code: {}",
                blobName,
                destinationContainer,
                (System.currentTimeMillis() - uploadStartTime),
                ex.getResponse() == null ? ex.getMessage() : ex.getResponse().getStatusCode()
            );
            if ((targetStorageAccount == TargetStorageAccount.CFT
                || targetStorageAccount == TargetStorageAccount.PCQ)
                && HttpStatus.valueOf(ex.getResponse().getStatusCode()).is4xxClientError()) {
                sasTokenCache.removeFromCache(destinationContainer);
            }
            throw ex;
        }
    }
}
