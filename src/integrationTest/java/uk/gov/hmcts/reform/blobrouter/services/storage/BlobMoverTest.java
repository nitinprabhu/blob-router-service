package uk.gov.hmcts.reform.blobrouter.services.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.blobrouter.util.BlobStorageBaseTest;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class BlobMoverTest extends BlobStorageBaseTest {

    BlobMover mover;

    @BeforeEach
    void setUp() {
        mover = new BlobMover(storageClient);
    }

    @AfterEach
    void tearDown() {
        this.deleteAllContainers();
    }

    @Test
    @SuppressWarnings("checkstyle:variabledeclarationusagedistance")
    void should_move_file_to_rejected_container() {
        // given
        BlobContainerClient normalContainer = createContainer("sample-container");
        BlobContainerClient rejectedContainer = createContainer("sample-container-rejected");

        var blobName = "hello.zip";

        normalContainer
            .getBlobClient(blobName)
            .uploadFromFile("src/integrationTest/resources/storage/test1.zip");

        // when
        mover.moveToRejectedContainer(blobName, "sample-container");

        // then
        assertSoftly(softly -> {
            softly
                .assertThat(normalContainer.listBlobs())
                .as("File should be removed from normal container")
                .hasSize(0);

            softly
                .assertThat(rejectedContainer.listBlobs().stream().map(BlobItem::getName))
                .as("File should be moved to rejected container")
                .containsExactly("hello.zip");
        });
    }

    @Test
    void should_create_snapshot_of_file_if_it_already_exists_in_the_rejected_container() {
        // given
        BlobContainerClient normalContainer = createContainer("hello");
        BlobContainerClient rejectedContainer = createContainer("hello-rejected");

        var blobName = "foo.zip";

        normalContainer
            .getBlobClient(blobName)
            .uploadFromFile("src/integrationTest/resources/storage/test1.zip");

        rejectedContainer
            .getBlobClient(blobName)
            .uploadFromFile("src/integrationTest/resources/storage/test1.zip");

        // when
        mover.moveToRejectedContainer(blobName, "hello");


        List<BlobItem> blobsAndSnapshots =
            rejectedContainer
                .listBlobs(new ListBlobsOptions().setDetails(new BlobListDetails().setRetrieveSnapshots(true)), null)
                .stream().collect(toList());

        // then
        assertSoftly(softly -> {
            softly
                .assertThat(blobsAndSnapshots)
                .extracting(BlobItem::getName)
                .as("Snapshot should be created")
                .containsExactly(
                    blobName,
                    blobName
                );

            softly
                .assertThat(normalContainer.listBlobs())
                .as("File should be removed from source container")
                .isEmpty();
        });
    }
}
