package uk.gov.hmcts.reform.blobrouter.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.statements.SupplierStatementRepository;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.statements.model.EnvelopeSupplierStatement;
import uk.gov.hmcts.reform.blobrouter.data.reconciliation.statements.model.NewEnvelopeSupplierStatement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.validation.ClockProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ActiveProfiles({"integration-test", "db-test"})
@SpringBootTest
public class SupplierStatementRepositoryTest {

    @Autowired SupplierStatementRepository repo;
    @Autowired ObjectMapper objectMapper;
    @Autowired ClockProvider clockProvider;

    @Test
    void should_save_and_find_statement() throws Exception {
        // given
        var statement =
            new NewEnvelopeSupplierStatement(
                LocalDate.now(),
                "{ \"a\": 100 }",
                "v1.0.0"
            );

        var start = LocalDateTime.now(clockProvider.getClock());

        // when
        UUID id = repo.save(statement);
        var statementInDb = repo.findById(id);

        var finish = LocalDateTime.now(clockProvider.getClock());

        // then
        assertThat(statementInDb).isNotEmpty();
        assertThat(statementInDb).hasValueSatisfying(s -> {
            assertThat(s.id).isEqualTo(id);
            assertThat(s.date).isEqualTo(statement.date);
            assertThat(s.content).isEqualTo(statement.content);
            assertThat(s.contentTypeVersion).isEqualTo(statement.contentTypeVersion);
            assertThat(s.createdAt).isNotNull();
            assertThat(s.createdAt).isAfter(start);
            assertThat(s.createdAt).isBefore(finish);
        });
    }

    @Test
    void should_throw_exception_if_invalid_json_is_passed() throws Exception {
        // given
        var statement =
            new NewEnvelopeSupplierStatement(
                LocalDate.now(),
                "&ASD^AS^DAS^",
                "v1.0.0"
            );

        // when
        var exc = catchThrowable(() -> repo.save(statement));

        // then
        assertThat(exc).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(exc.getMessage()).contains("invalid input syntax for type json");
    }

    @Test
    void should_return_empty_optional_if_statement_does_not_exist() {
        // given
        UUID uuid = UUID.randomUUID();

        // when
        Optional<EnvelopeSupplierStatement> statement = repo.findById(uuid);

        // then
        assertThat(statement).isEmpty();
    }
}