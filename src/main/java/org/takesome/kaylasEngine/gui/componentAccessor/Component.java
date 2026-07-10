package org.takesome.kaylasEngine.gui.componentAccessor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a named Swing component from a {@link ComponentsAccessor} index to a field.
 *
 * <p>The annotation is evaluated for fields declared on the concrete accessor class and every
 * superclass up to, but excluding, {@link ComponentsAccessor}. The field type is validated before
 * assignment. Existing declarations such as {@code @Component("login")} remain fully supported.</p>
 *
 * <h2>Exact id binding</h2>
 * <pre>{@code
 * @Component("login")
 * private TextField login;
 * }</pre>
 *
 * <h2>Field-name binding</h2>
 * <pre>{@code
 * @Component
 * private TextArea settingsInfo; // resolves id "settingsInfo"
 * }</pre>
 *
 * <h2>Constructor scope binding</h2>
 * <pre>{@code
 * @Component(scope = "volume", localId = "slider")
 * private Slider slider; // resolves id "volume.slider"
 * }</pre>
 *
 * <h2>Optional binding</h2>
 * <pre>{@code
 * @Component(value = "experimentalWidget", required = false)
 * private JComponent experimentalWidget;
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Component {

    /**
     * Component id to resolve. When blank, the annotated field name is used.
     *
     * @return exact or scope-relative component id.
     */
    String value() default "";

    /**
     * Optional composite scope. When present, a non-qualified {@link #value()} is resolved as
     * {@code scope.value}.
     *
     * @return composite instance id, for example {@code volume}.
     */
    String scope() default "";

    /**
     * Optional local node id inside {@link #scope()}.
     *
     * <p>When set, it takes precedence over {@link #value()} and resolves as
     * {@code scope.localId}. A non-blank scope is required.</p>
     *
     * @return local constructor node id, for example {@code slider}.
     */
    String localId() default "";

    /**
     * Controls whether a missing component fails field injection.
     *
     * @return {@code true} to throw when the component is absent; {@code false} to leave the field
     * unchanged or inject {@code Optional.empty()}.
     */
    boolean required() default true;
}
