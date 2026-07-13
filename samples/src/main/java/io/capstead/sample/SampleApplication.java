package io.capstead.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Capstead sample application.
 *
 * <p>Run it, then open the dashboard at <a href="http://localhost:8080/capstead">/capstead</a> and the
 * actuator endpoints — the capabilities below have already been exercised at startup, so scorecards
 * and execution history are populated. Note the declarative {@code @CapabilityClient} runs with no
 * Spring AI: it is powered by a plain {@link StubModelInvoker} bean.
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
