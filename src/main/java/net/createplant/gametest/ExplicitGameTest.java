package net.createplant.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Keeps a slow GameTest class out of the normal test server; only a run that names it in createplant.gameTestClasses registers it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExplicitGameTest { }
