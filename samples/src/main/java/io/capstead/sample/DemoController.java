package io.capstead.sample;

import io.capstead.core.CapabilityBudgetException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trigger the capabilities on demand to generate more execution history for the dashboard. */
@RestController
public class DemoController {

    private final CourseService courses;
    private final TutorService tutor;
    private final ReportService reports;
    private final QuizCapability quiz;
    private final BudgetGuardService budgetGuard;

    public DemoController(CourseService courses, TutorService tutor, ReportService reports,
                          QuizCapability quiz, BudgetGuardService budgetGuard) {
        this.courses = courses;
        this.tutor = tutor;
        this.reports = reports;
        this.quiz = quiz;
        this.budgetGuard = budgetGuard;
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

    @GetMapping("/demo/quiz")
    public Quiz quiz(@RequestParam(defaultValue = "the Java Stream API") String topic) {
        return quiz.execute(topic);
    }

    /**
     * Feature example: watch a {@code @DailyBudget} get enforced. Calls the budgeted capability
     * repeatedly and returns the point at which Capstead blocked it.
     */
    @GetMapping("/demo/budget")
    public String budget(@RequestParam(defaultValue = "Capstead") String product,
                         @RequestParam(defaultValue = "25") int attempts) {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= attempts; i++) {
            try {
                budgetGuard.draftTagline(product);
                out.append("call ").append(i).append(": ok\n");
            } catch (CapabilityBudgetException e) {
                out.append("call ").append(i).append(": BLOCKED — ")
                        .append("budget=").append(e.budget())
                        .append(", spent today=").append(e.spentToday()).append('\n');
                out.append("\nCapstead enforced the daily budget after ").append(i - 1)
                        .append(" successful call(s). Governance applied with no code in the method.");
                return out.toString();
            }
        }
        out.append("\nBudget not reached in ").append(attempts)
                .append(" calls — raise ?attempts= or lower the @DailyBudget on BudgetGuardService.");
        return out.toString();
    }
}
