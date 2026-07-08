package org.takesome.kaylasEngine.utils.HTTP;

import java.lang.annotation.*;

@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface HttpParam {
    String value() default "";
}