package com.a11y.a11y_ai_readiness.web;

import com.a11y.a11y_ai_readiness.registration.RegistrationForm;
import com.a11y.a11y_ai_readiness.session.SessionRepository;
import com.a11y.a11y_ai_readiness.session.WorkshopSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Serves both the /accessible and /inaccessible variants from one controller and one backend —
 * only the template resolved per {variant} differs. Sharing the controller, the validation and
 * the CSS is deliberate: markup semantics are then the only thing that varies between the two
 * pages, so any difference in how a screen reader or an agent handles them has one cause.
 */
@Controller
@RequestMapping("/{variant:accessible|inaccessible}")
public class SessionController {

    private final SessionRepository sessionRepository;

    public SessionController(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public String list(@PathVariable String variant,
                        @RequestParam(name = "q", required = false) String query,
                        Model model) {
        model.addAttribute("sessions", sessionRepository.findAll(query));
        model.addAttribute("query", query);
        return variant + "/sessions";
    }

    @GetMapping("/register/{id}")
    public String showForm(@PathVariable String variant, @PathVariable String id, Model model) {
        model.addAttribute("workshop", findSessionOrNotFound(id));
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return variant + "/register";
    }

    @PostMapping("/register/{id}")
    public String submitForm(@PathVariable String variant,
                              @PathVariable String id,
                              @Valid @ModelAttribute("registrationForm") RegistrationForm form,
                              BindingResult bindingResult,
                              Model model) {
        WorkshopSession workshop = findSessionOrNotFound(id);
        model.addAttribute("workshop", workshop);

        if (bindingResult.hasErrors()) {
            return variant + "/register";
        }

        model.addAttribute("confirmationCode", generateConfirmationCode());
        model.addAttribute("attendeeName", form.getName());
        return variant + "/confirmation";
    }

    private WorkshopSession findSessionOrNotFound(String id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such session: " + id));
    }

    private String generateConfirmationCode() {
        return "JCON-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
