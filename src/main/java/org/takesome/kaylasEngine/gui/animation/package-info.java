/**
 * Public animation API for easing curves, shared frame scheduling, timelines, overlays, progress
 * controls, scripted windows and snapshot-backed drawers.
 *
 * <p>Stable application-facing types remain in this package. Execution details are separated into
 * responsibility-focused subpackages under {@code animation.internal}; those packages are engine
 * implementation details and are not a supported integration surface.</p>
 *
 * <p>The shared runtime also exposes opt-in lifecycle listening through
 * {@link org.takesome.kaylasEngine.gui.animation.AnimationListener} and low-overhead timing
 * diagnostics through {@link org.takesome.kaylasEngine.gui.animation.AnimationPulse.Diagnostics}.
 * This keeps animation behaviour observable without coupling UI components to logging or profiling
 * infrastructure.</p>
 */
package org.takesome.kaylasEngine.gui.animation;
