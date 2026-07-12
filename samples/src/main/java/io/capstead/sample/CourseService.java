package io.capstead.sample;

import io.capstead.annotation.Capability;

import org.springframework.stereotype.Service;

/**
 * A <em>composed</em> capability: it makes one model call of its own and then calls the
 * {@code Generate Lesson} capability twice. Because those are cross-bean calls through governed
 * proxies, Capstead links each nested execution to this one automatically — so
 * {@code GET /actuator/capabilityexecutions/{id}} shows a parent-child execution tree.
 */
@Service
public class CourseService {

    private final LessonService lessons;
    private final DemoModel model;

    public CourseService(LessonService lessons, DemoModel model) {
        this.lessons = lessons;
        this.model = model;
    }

    @Capability(
            name = "Build Course",
            domain = "Learning",
            owner = "Content Team",
            version = "1",
            tags = {"course"})
    public String buildCourse(String subject) {
        model.call("claude-sonnet", "Outline a course on " + subject);
        String basics = lessons.generateLesson(subject + " basics");
        String advanced = lessons.generateLesson(subject + " advanced");
        return "Course[" + subject + "]: " + basics + " | " + advanced;
    }
}
