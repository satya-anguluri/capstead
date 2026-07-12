package io.capstead.runtime;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;
import io.capstead.core.CapabilityMetadata;

import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Resolves capability metadata from the {@code @Capability} (and {@code @DailyBudget}) annotations —
 * the original, annotation-driven declaration style.
 */
public class AnnotationCapabilityMetadataResolver implements CapabilityMetadataResolver {

    @Override
    public CapabilityMetadata resolve(Method method) {
        Capability annotation = AnnotatedElementUtils.findMergedAnnotation(method, Capability.class);
        if (annotation == null) {
            return null;
        }
        return new CapabilityMetadata(
                annotation.name(),
                annotation.description(),
                annotation.domain(),
                annotation.owner(),
                annotation.version(),
                Arrays.asList(annotation.tags()));
    }

    @Override
    public String dailyBudget(Method method) {
        DailyBudget dailyBudget = AnnotatedElementUtils.findMergedAnnotation(method, DailyBudget.class);
        return dailyBudget == null ? null : dailyBudget.value();
    }
}
