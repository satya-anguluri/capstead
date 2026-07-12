package io.capstead.sample;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;

import org.springframework.stereotype.Service;

/** Annotation-declared capability with a daily budget. */
@Service
public class LessonService {

    private final DemoModel model;

    public LessonService(DemoModel model) {
        this.model = model;
    }

    @Capability(
            name = "Generate Lesson",
            domain = "Learning",
            owner = "Content Team",
            version = "1",
            tags = {"lesson"})
    @DailyBudget("$10")
    public String generateLesson(String topic) {
        return model.call("claude-sonnet", "Write a lesson on " + topic);
    }
}
