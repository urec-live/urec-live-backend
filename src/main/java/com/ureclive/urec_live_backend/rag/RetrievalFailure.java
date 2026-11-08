package com.ureclive.urec_live_backend.rag;

/** Only these controlled categories/statuses may enter logs; never provider bodies. */
final class RetrievalFailure extends RuntimeException {
    enum Code { MISSING_KEY, PROVIDER_HTTP, INVALID_RESPONSE, EMPTY_RESPONSE, TRUNCATED_RESPONSE }
    final Code code;
    final int status;
    RetrievalFailure(Code code) { this(code, 0); }
    RetrievalFailure(Code code, int status) { super(code.name()); this.code = code; this.status = status; }
}
