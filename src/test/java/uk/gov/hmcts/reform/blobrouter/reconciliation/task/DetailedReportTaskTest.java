package uk.gov.hmcts.reform.blobrouter.reconciliation.task;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.blobrouter.reconciliation.service.DetailedReportService;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.blobrouter.config.TargetStorageAccount.CFT;

class DetailedReportTaskTest {

    @Test
    void should_call_summary_report_service() {
        // given
        var detailedReportService = mock(DetailedReportService.class);

        var task = new DetailedReportTask(detailedReportService);

        // when
        task.run();

        // then
        verify(detailedReportService, times(1)).process(LocalDate.now(), CFT);
    }

}
