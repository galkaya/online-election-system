package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.web.DisplayTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for time display and input (NFR-05, NFR-07). */
@DisplayName("Display time")
class DisplayTimeTest {

    private final DisplayTime displayTime = new DisplayTime("America/Chicago");

    @Test
    @DisplayName("NFR-05/NFR-07: instants are shown in English with the zone abbreviation")
    void formatsInEnglishWithZone() {
        assertThat(displayTime.format(Instant.parse("2026-11-19T15:00:00Z"))).isEqualTo("Nov 19, 2026, 9:00 AM CST");
        assertThat(displayTime.format(Instant.parse("2026-07-04T23:30:00Z"))).isEqualTo("Jul 4, 2026, 6:30 PM CDT");
        assertThat(displayTime.zoneLabel()).isEqualTo("America/Chicago");
    }

    @Test
    @DisplayName("NFR-07: form input is read as a time in the display zone and stored as a UTC instant")
    void parsesInputInDisplayZone() {
        assertThat(displayTime.parseInput("2026-11-19 09:00")).isEqualTo(Instant.parse("2026-11-19T15:00:00Z"));
        assertThat(displayTime.parseInput(" 2026-07-04 18:30 ")).isEqualTo(Instant.parse("2026-07-04T23:30:00Z"));
        assertThat(displayTime.toInput(Instant.parse("2026-11-19T15:00:00Z"))).isEqualTo("2026-11-19 09:00");
    }

    @Test
    @DisplayName("FR-03: impossible dates and times are rejected")
    void rejectsImpossibleDates() {
        assertThatThrownBy(() -> displayTime.parseInput("2026-02-30 10:00")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> displayTime.parseInput("2026-11-19 25:00")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> displayTime.parseInput("next Tuesday")).isInstanceOf(DateTimeParseException.class);
    }
}
