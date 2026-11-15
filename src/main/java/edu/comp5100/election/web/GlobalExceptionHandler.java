package edu.comp5100.election.web;

import edu.comp5100.election.service.BusinessRuleException;
import edu.comp5100.election.service.NotFoundException;
import edu.comp5100.election.service.ResultsNotAvailableException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns expected failures into plain English pages (FR-11, NFR-04). No handler exposes an
 * exception message from a framework or a stack trace. Unexpected exceptions fall through to
 * Spring Boot's error handling, which renders error.html with status 500 and no details.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException notFound, Model model, HttpServletResponse response) {
        return errorPage(model, response, HttpServletResponse.SC_NOT_FOUND, notFound.getMessage());
    }

    @ExceptionHandler(ResultsNotAvailableException.class)
    public String resultsNotAvailable(ResultsNotAvailableException notAvailable, Model model,
            HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return "results-unavailable";
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String businessRule(BusinessRuleException refused, Model model, HttpServletResponse response) {
        return errorPage(model, response, HttpServletResponse.SC_CONFLICT, refused.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String badRequest(Model model, HttpServletResponse response) {
        return errorPage(model, response, HttpServletResponse.SC_BAD_REQUEST,
                "The request contains a missing or invalid value.");
    }

    private static String errorPage(Model model, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("safeMessage", message);
        return "error";
    }
}
