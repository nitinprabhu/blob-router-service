package uk.gov.hmcts.reform.blobrouter.data.events;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class EventRecordMapper implements RowMapper<EventRecord> {

    @Override
    public EventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EventRecord(
            rs.getLong("id"),
            rs.getString("container"),
            rs.getString("file_name"),
            rs.getTimestamp("created_at").toInstant(),
            EventType.valueOf(rs.getString("event")),
            rs.getString("notes")
        );
    }
}