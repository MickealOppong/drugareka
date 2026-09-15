package com.pages.interfaces;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPublicPost {
    // Drop this on any method that requires a user to have a public post first
}
