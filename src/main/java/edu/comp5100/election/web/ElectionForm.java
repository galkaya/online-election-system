package edu.comp5100.election.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form for creating or editing a draft election. Times are text in the display time zone. */
public class ElectionForm {

    private static final String DATE_TIME_REGEX = "^$|^\\s*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\\s*$";
    private static final String DATE_TIME_MESSAGE = "Use the format YYYY-MM-DD HH:MM, for example 2026-11-19 09:00.";

    @NotBlank(message = "Enter a title.")
    @Size(max = 120, message = "The title must be at most 120 characters long.")
    private String title;

    @Size(max = 1000, message = "The description must be at most 1000 characters long.")
    private String description;

    @NotBlank(message = "Enter a start time.")
    @Pattern(regexp = DATE_TIME_REGEX, message = DATE_TIME_MESSAGE)
    private String startsAt;

    @NotBlank(message = "Enter an end time.")
    @Pattern(regexp = DATE_TIME_REGEX, message = DATE_TIME_MESSAGE)
    private String endsAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(String startsAt) {
        this.startsAt = startsAt;
    }

    public String getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(String endsAt) {
        this.endsAt = endsAt;
    }
}
