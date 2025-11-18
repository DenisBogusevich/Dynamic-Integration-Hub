package org.example.model;

/**
 * Defines the fault tolerance policy for a specific pipeline step.
 * Used by the AOP layer to wrap step execution in a retry proxy.
 *
 * @param maxAttempts The maximum number of total execution attempts (1 = no retry, just initial attempt).
 * Must be >= 1.
 * @param delay       The backoff delay between attempts in <b>milliseconds</b>.
 */
public record RetryPolicyDefinition(
        int maxAttempts,
        long delay
) {
}
