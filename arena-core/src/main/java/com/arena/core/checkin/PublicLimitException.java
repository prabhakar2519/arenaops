package com.arena.core.checkin;
/** Commits only an abuse cooldown before the normal structured 429 response. */
public class PublicLimitException extends com.arena.core.exception.ArenaOpsException {
 public PublicLimitException(){super(com.arena.core.exception.ErrorCode.TOO_MANY_REQUESTS);}
}
