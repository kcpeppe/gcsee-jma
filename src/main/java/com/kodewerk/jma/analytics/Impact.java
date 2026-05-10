package com.kodewerk.jma.analytics;

/**
 * Severity tier for an {@link AnalyticsFinding}. Three levels —
 * {@link #OK} (the check ran and saw nothing concerning),
 * {@link #CONCERNING} (something present that warrants attention) and
 * {@link #SIGNIFICANT} (something that's almost certainly a problem
 * worth fixing).
 * <p>
 * Kept deliberately small. We picked three so the UI can colour-code
 * with green / amber / red without sliding into a five-tier system that
 * forces fine-grained judgement calls. If a finding is genuinely binary
 * ("this happened / this didn't"), it should still pick {@code OK} or
 * one of the two raised levels — there is no "informational" tier.
 */
public enum Impact {
    OK,
    CONCERNING,
    SIGNIFICANT
}
