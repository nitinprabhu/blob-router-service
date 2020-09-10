package uk.gov.hmcts.reform.blobrouter.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.blobrouter.config.ServiceConfiguration;
import uk.gov.hmcts.reform.blobrouter.config.StorageConfigItem;
import uk.gov.hmcts.reform.blobrouter.config.TargetStorageAccount;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.reports.ReconciliationReportRepository;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.reports.model.NewReconciliationReport;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.statements.SupplierStatementRepository;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.statements.model.EnvelopeSupplierStatement;
import uk.gov.hmcts.reform.blobrouter.reconciliation.model.in.SupplierStatement;
import uk.gov.hmcts.reform.blobrouter.reconciliation.report.SummaryReportItem;
import uk.gov.hmcts.reform.blobrouter.services.EnvelopeService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static org.slf4j.LoggerFactory.getLogger;

@Service
public class SummaryReportService {

    private static final Logger logger = getLogger(SummaryReportService.class);

    private final SupplierStatementRepository repository;
    private final ReconciliationReportRepository reconciliationReportRepository;
    private final ObjectMapper objectMapper;
    private final EnvelopeService envelopeService;
    private final Map<String, StorageConfigItem> storageConfig; // container-specific configuration, by container name
    private SummaryReportCreator summaryReportCreator;

    public SummaryReportService(
        SupplierStatementRepository repository,
        ReconciliationReportRepository reconciliationReportRepository,
        ObjectMapper objectMapper,
        EnvelopeService envelopeService,
        ServiceConfiguration serviceConfiguration,
        SummaryReportCreator summaryReportCreator
    ) {
        this.repository = repository;
        this.reconciliationReportRepository = reconciliationReportRepository;
        this.objectMapper = objectMapper;
        this.envelopeService = envelopeService;
        this.storageConfig =  serviceConfiguration.getStorageConfig();
        this.summaryReportCreator = summaryReportCreator;
    }

    public void process(LocalDate date) {
        Optional<EnvelopeSupplierStatement> optSupplierStatement = repository.findLatest(date);

        if (!optSupplierStatement.isPresent()) {
            logger.error("No supplier statement found for {}", date);
            return;
        }

        EnvelopeSupplierStatement envelopeSupplierStatement = optSupplierStatement.get();

        SupplierStatement supplierStatement;
        try {
            supplierStatement = objectMapper
                .readValue(envelopeSupplierStatement.content, SupplierStatement.class);
        } catch (JsonProcessingException jsonEx) {
            logger.error(
                "Error while parsing supplier statement. Supplier id:{}, date:{}",
                envelopeSupplierStatement.id,
                date,
                jsonEx
            );
            return;
        }

        var envelopeList = envelopeService.getEnvelopes(date);

        if (CollectionUtils.isEmpty(envelopeList)) {
            logger.info("No envelope found for {}", date);
        }

        Map<TargetStorageAccount, List<SummaryReportItem>>
            supplierEnvelopesMap = supplierStatement
            .envelopes
            .stream()
            .map(e -> new SummaryReportItem(e.zipFileName, e.container))
            .collect(groupingBy(s -> storageConfig.get(s.container).getTargetStorageAccount()));

        Map<TargetStorageAccount, List<SummaryReportItem>> processedEnvelopesMap =
            envelopeList
                .stream()
                .map(e -> new SummaryReportItem(e.fileName, e.container))
                .collect(groupingBy(s -> storageConfig.get(s.container).getTargetStorageAccount()));

        for (var targetStorage : TargetStorageAccount.values()) {
            try {

                var summaryReport = summaryReportCreator.createSummaryReport(
                    processedEnvelopesMap.get(targetStorage),
                    supplierEnvelopesMap.get(targetStorage)
                );

                String summaryContent = objectMapper.writeValueAsString(summaryReport);
                var report = new NewReconciliationReport(
                    envelopeSupplierStatement.id,
                    targetStorage.name(),
                    summaryContent,
                    null,
                    "1.0"
                );
                reconciliationReportRepository.save(report);
            } catch (Exception ex) {
                logger.error(
                    "Error creating summary report. Account: {}, supplier Id: {}, date: {}",
                    targetStorage,
                    envelopeSupplierStatement.id,
                    date,
                    ex
                );
            }
        }
    }

}