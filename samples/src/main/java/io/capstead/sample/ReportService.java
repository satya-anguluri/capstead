package io.capstead.sample;

import org.springframework.stereotype.Service;

/**
 * A plain service with <strong>no</strong> {@code @Capability} annotation. It is promoted to a
 * governed capability purely from {@code application.yml} ({@code capstead.capabilities}) — showing
 * that Capstead governs annotation-free / third-party / generically-named methods too.
 */
@Service
public class ReportService {

    private final DemoModel model;

    public ReportService(DemoModel model) {
        this.model = model;
    }

    public String summarize(String text) {
        return model.call("claude-haiku", "Summarize: " + text);
    }
}
