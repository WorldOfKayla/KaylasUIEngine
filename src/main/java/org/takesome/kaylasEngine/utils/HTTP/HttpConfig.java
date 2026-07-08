package org.takesome.kaylasEngine.utils.HTTP;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface HttpConfig {
    int connectTimeout() default 5000;
    int readTimeout() default 5000;
}
