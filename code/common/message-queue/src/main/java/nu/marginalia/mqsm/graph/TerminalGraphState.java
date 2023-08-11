package nu.marginalia.mqsm.graph;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface TerminalGraphState {
    String name();
    String description() default "";
}