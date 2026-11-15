package edu.comp5100.election.web;

import edu.comp5100.election.service.ReceiptVerificationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Confirmation code check (FR-13). The code is sent in a POST body, never in a URL, the page is
 * not cacheable, and the entered code is not written back into the response.
 */
@Controller
public class ReceiptController {

    private final ReceiptVerificationService receiptVerificationService;

    public ReceiptController(ReceiptVerificationService receiptVerificationService) {
        this.receiptVerificationService = receiptVerificationService;
    }

    @GetMapping("/receipt")
    public String form(HttpServletResponse response) {
        forbidCaching(response);
        return "receipt";
    }

    @PostMapping("/receipt")
    public String verify(@RequestParam(name = "code", required = false) String code, Model model,
            HttpServletResponse response) {
        forbidCaching(response);
        model.addAttribute("status", receiptVerificationService.verify(code));
        return "receipt";
    }

    private static void forbidCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
