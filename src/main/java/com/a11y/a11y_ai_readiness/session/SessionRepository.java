package com.a11y.a11y_ai_readiness.session;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SessionRepository {

    private final List<WorkshopSession> sessions = List.of(
            new WorkshopSession("accessible-forms", "Building Accessible Forms in Java", "A. Rivera", "Tue 10:00"),
            new WorkshopSession("spring-ai-agents", "Spring AI: Building Tool-Calling Agents", "J. Okafor", "Tue 13:30"),
            new WorkshopSession("playwright-java", "Playwright for Java: Role-Based Locators", "S. Nakamura", "Wed 09:00"),
            new WorkshopSession("virtual-threads", "Virtual Threads in Production", "M. Lindqvist", "Wed 11:15"),
            new WorkshopSession("vaadin-a11y", "Vaadin Components: Accessible by Default", "P. Dubois", "Wed 14:00")
    );

    public List<WorkshopSession> findAll(String query) {
        if (query == null || query.isBlank()) {
            return sessions;
        }
        String needle = query.toLowerCase();
        return sessions.stream()
                .filter(s -> s.title().toLowerCase().contains(needle) || s.speaker().toLowerCase().contains(needle))
                .toList();
    }

    public Optional<WorkshopSession> findById(String id) {
        return sessions.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
