package io.capstead.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trigger the capabilities on demand to generate more execution history for the dashboard. */
@RestController
public class DemoController {

    private final CourseService courses;
    private final TutorService tutor;
    private final ReportService reports;

    public DemoController(CourseService courses, TutorService tutor, ReportService reports) {
        this.courses = courses;
        this.tutor = tutor;
        this.reports = reports;
    }

    @GetMapping("/demo/course")
    public String course(@RequestParam(defaultValue = "Distributed Systems") String subject) {
        return courses.buildCourse(subject);
    }

    @GetMapping("/demo/tutor")
    public String tutor(@RequestParam(defaultValue = "Explain backpressure") String question) {
        return tutor.answer(question);
    }

    @GetMapping("/demo/report")
    public String report(@RequestParam(defaultValue = "A quarterly summary of AI capability usage.") String text) {
        return reports.summarize(text);
    }
}
