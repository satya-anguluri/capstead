package io.capstead.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Exercises every capability once at startup so the dashboard, scorecard and execution history are
 * populated the moment the app is up — no clicking required.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final CourseService courses;
    private final TutorService tutor;
    private final ReportService reports;
    private final QuizCapability quiz;

    public DemoRunner(CourseService courses, TutorService tutor, ReportService reports, QuizCapability quiz) {
        this.courses = courses;
        this.tutor = tutor;
        this.reports = reports;
        this.quiz = quiz;
    }

    @Override
    public void run(String... args) {
        courses.buildCourse("Java Concurrency");          // composed -> parent + 2 child executions
        tutor.answer("What is a happens-before relationship?");
        reports.summarize("Capstead governs AI capabilities in Spring Boot.");  // config-declared
        quiz.execute("the Java Stream API");               // declarative -> generated + governed

        log.info("");
        log.info("  Capstead sample is ready. Explore:");
        log.info("    dashboard   -> http://localhost:8080/capstead");
        log.info("    catalog     -> http://localhost:8080/actuator/capabilities");
        log.info("    scorecards  -> http://localhost:8080/actuator/capabilityscorecard");
        log.info("    executions  -> http://localhost:8080/actuator/capabilityexecutions");
        log.info("    trigger more-> http://localhost:8080/demo/course?subject=Kafka");
        log.info("    declarative -> http://localhost:8080/demo/quiz?topic=Records");
        log.info("");
    }
}
