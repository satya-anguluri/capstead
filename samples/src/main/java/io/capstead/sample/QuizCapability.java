package io.capstead.sample;

import io.capstead.annotation.Capability;
import io.capstead.annotation.CapabilityClient;
import io.capstead.annotation.ModelProfile;
import io.capstead.annotation.Prompt;

/**
 * A <strong>declarative</strong> capability: no implementation, no {@code ChatClient} boilerplate.
 *
 * <p>Capstead renders the {@link Prompt}, routes to the model resolved from
 * {@link ModelProfile @ModelProfile("reasoning")} (see {@code capstead.ai.profiles} in
 * {@code application.yml}), calls the model, binds the JSON response to {@link Quiz}, and records the
 * whole thing as a governed execution — it appears on the dashboard next to the annotation- and
 * config-declared capabilities, with model, tokens and cost.
 */
@CapabilityClient
@ModelProfile("reasoning")
public interface QuizCapability {

    @Capability(
            name = "Generate Quiz",
            domain = "Learning",
            owner = "Content Team",
            version = "1",
            tags = {"quiz", "declarative"})
    @Prompt("""
            Create a single multiple-idea quiz question about {{topic}}.
            Respond with a question and a concise answer.
            """)
    Quiz execute(String topic);
}
