package org.takesome.kaylasEngine.crash;

import java.util.concurrent.CompletionStage;

/** Application callback used by the engine crash dialog to submit a report asynchronously. */
@FunctionalInterface
public interface CrashReportSendListener {
    CompletionStage<CrashReportSendResult> sendCrashReport(CrashReportSubmission submission);
}
