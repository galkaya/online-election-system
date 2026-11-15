package edu.comp5100.election.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NFR-05, NFR-07: converts between stored UTC instants and the configured display time zone.
 * Output is always formatted with the US English locale and always carries the zone abbreviation.
 */
@Component("displayTime")
public class DisplayTime {

    public static final String INPUT_PATTERN_HINT = "YYYY-MM-DD HH:MM";
    private static final DateTimeFormatter INPUT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.US).withResolverStyle(ResolverStyle.STRICT);

    private final ZoneId zone;
    private final DateTimeFormatter outputFormat;

    public DisplayTime(@Value("${election.display-zone:America/Chicago}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
        this.outputFormat = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a z", Locale.US).withZone(zone);
    }

    /** Example: "Nov 19, 2026, 9:00 AM CST". */
    public String format(Instant instant) {
        return outputFormat.format(instant);
    }

    /** Example: "America/Chicago". */
    public String zoneLabel() {
        return zone.getId();
    }

    /** Value for a date-time text field, in the display zone: "2026-11-19 09:00". */
    public String toInput(Instant instant) {
        return INPUT_FORMAT.format(LocalDateTime.ofInstant(instant, zone));
    }

    /**
     * Reads a date-time text field as a time in the display zone.
     *
     * @throws java.time.format.DateTimeParseException when the text is not a real date and time
     */
    public Instant parseInput(String text) {
        return LocalDateTime.parse(text.trim(), INPUT_FORMAT).atZone(zone).toInstant();
    }
}
