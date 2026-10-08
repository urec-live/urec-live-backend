package com.ureclive.urec_live_backend.rag;

/** Safe diagnostic codes; never contains the answer, query, or source text. */
final class EvidenceValidationException extends IllegalArgumentException {
    enum Reason { INVALID_ASSESSMENT, INVALID_SOURCE_ID, MISSING_PASSAGES, UNKNOWN_PASSAGE, PASSAGE_SOURCE_MISMATCH,
        UNSUPPORTED_CITATION, MISSING_CITATIONS, UNSUPPORTED_ABSTENTION_CITATION }
    final Reason reason;
    EvidenceValidationException(Reason reason) { super(reason.name()); this.reason = reason; }
}
