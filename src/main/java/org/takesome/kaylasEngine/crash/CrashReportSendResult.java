package org.takesome.kaylasEngine.crash;

/** Server acknowledgement returned after a crash report has been accepted. */
public record CrashReportSendResult(String reportId, String message) {
    public CrashReportSendResult {
        reportId = reportId == null ? "" : reportId.trim();
        message = message == null || message.isBlank() ? "Crash report sent." : message.trim();
    }
}
