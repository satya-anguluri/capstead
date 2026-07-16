package io.capstead.sample;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;

import org.springframework.stereotype.Service;

/**
 * Feature example: <strong>daily-budget governance</strong>.
 *
 * <p>This capability has a deliberately tiny {@link DailyBudget} so you can watch Capstead enforce it.
 * Each call routes through the (priced) {@code claude-sonnet} model, so after a few invocations the
 * day's spend reaches the budget and Capstead blocks further calls by throwing a
 * {@code CapabilityBudgetException} — governance the runtime enforces, with no code in this method.
 *
 * <p>Drive it from {@code GET /demo/budget} (see {@link DemoController}).
 */
@Service
public class BudgetGuardService {

    private final DemoModel model;

    public BudgetGuardService(DemoModel model) {
        this.model = model;
    }

    @Capability(
            name = "Draft Tagline",
            domain = "Marketing",
            owner = "Growth Team",
            version = "1",
            tags = {"copy", "budget-demo"})
    @DailyBudget("$0.02")
    public String draftTagline(String product) {
        return model.call("claude-sonnet", "Write a one-line marketing tagline for " + product);
    }
}
