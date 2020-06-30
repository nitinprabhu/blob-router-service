package uk.gov.hmcts.reform.blobrouter.storage;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.common.implementation.Constants;

import java.io.ByteArrayInputStream;

public final class StorageHelper {

    private StorageHelper() {
        // utility class
    }

    public static void uploadFile(
        BlobServiceClient client,
        String containerName,
        String fileName,
        byte[] fileContent
    ) {
        BlobRequestConditions blobRequestConditions = new BlobRequestConditions();
        blobRequestConditions.setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);

        BlobHttpHeaders headers = new BlobHttpHeaders();

        client
            .getBlobContainerClient(containerName)
            .getBlobClient(fileName)
            .getBlockBlobClient()
            .uploadWithResponse(
                new ByteArrayInputStream(fileContent),
                fileContent.length,
                headers.setContentType("application/zip"),
                null,
                null,
                null,
                blobRequestConditions,
                null,
                Context.NONE
            );

    }

    public static boolean blobExists(
        BlobServiceClient client,
        String containerName,
        String fileName
    ) {
        return client
            .getBlobContainerClient(containerName)
            .getBlobClient(fileName)
            .getBlockBlobClient()
            .exists();
    }
}
