package io.capstead.sample;

import io.capstead.annotation.Capability;

import org.springframework.stereotype.Service;

/** Another annotation-declared capability, on a different model. */
@Service
public class TutorService {

    private final DemoModel model;

    public TutorService(DemoModel model) {
        this.model = model;
    }

    @Capability(
            name = "Lesson Tutor",
            domain = "Learning",
            owner = "Content Team",
            version = "1",
            tags = {"tutor"})
    public String answer(String question) {
        return model.call("nova-pro", "Answer the learner's question: " + question);
    }
}
