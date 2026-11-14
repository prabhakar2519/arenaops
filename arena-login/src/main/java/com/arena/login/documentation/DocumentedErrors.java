package com.arena.login.documentation;
import java.lang.annotation.*;
import com.arena.login.exception.ErrorCode;
/** Verified business failures reachable from this controller operation, in addition to MVC/security failures. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface DocumentedErrors { ErrorCode[] value(); }
