package uk.gov.hmcts.reform.blobrouter.services.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.blobrouter.data.reports.EnvelopeSummary;
import uk.gov.hmcts.reform.blobrouter.data.reports.ReportRepository;
import uk.gov.hmcts.reform.blobrouter.model.out.EnvelopeSummaryItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.blobrouter.data.envelopes.Status.DISPATCHED;
import static uk.gov.hmcts.reform.blobrouter.data.envelopes.Status.REJECTED;
import static uk.gov.hmcts.reform.blobrouter.util.DateTimeUtils.instant;
import static uk.gov.hmcts.reform.blobrouter.util.DateTimeUtils.localDate;
import static uk.gov.hmcts.reform.blobrouter.util.DateTimeUtils.localTime;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository);
    }

    @Test
    void getDailyReport_should_convert_date_into_date_range() {
        // given
        LocalDate dt = LocalDate.of(2019, 1, 14);

        // when
        reportService.getDailyReport(dt);

        // then
        Instant expectedFrom = instant("2019-01-14 00:00:00");
        Instant expectedTo = instant("2019-01-15 00:00:00");

        verify(reportRepository).getEnvelopeSummary(expectedFrom, expectedTo);
        verifyNoMoreInteractions(reportRepository);
    }

    @Test
    void getDailyReport_should_convert_repo_result() {
        // given
        final String container1 = "cont1";
        final String fileName1 = "file1.zip";
        final String fileName2 = "file2.zip";

        EnvelopeSummary es1 = new EnvelopeSummary(
            container1,
            fileName1,
            instant("2019-01-14 10:11:12"),
            instant("2019-01-15 11:12:13"),
            DISPATCHED,
            true
        );
        EnvelopeSummary es2 = new EnvelopeSummary(
            container1,
            fileName2,
            instant("2019-01-16 12:13:14"),
            null,
            REJECTED,
            false
        );
        given(reportRepository.getEnvelopeSummary(any(Instant.class), any(Instant.class)))
            .willReturn(asList(es1, es2));

        LocalDate dt = LocalDate.of(2019, 1, 14);

        // when
        List<EnvelopeSummaryItem> res = reportService.getDailyReport(dt);

        // then
        assertThat(res)
            .usingFieldByFieldElementComparator()
            .containsExactlyInAnyOrder(
                new EnvelopeSummaryItem(
                    container1,
                    fileName1,
                    localDate("2019-01-14"),
                    localTime("10:11:12"),
                    localDate("2019-01-15"),
                    localTime("11:12:13"),
                    DISPATCHED.name(),
                    true
                ),
                new EnvelopeSummaryItem(
                    container1,
                    fileName2,
                    localDate("2019-01-16"),
                    localTime("12:13:14"),
                    null,
                    null,
                    REJECTED.name(),
                    false
                )
            );
    }
}
