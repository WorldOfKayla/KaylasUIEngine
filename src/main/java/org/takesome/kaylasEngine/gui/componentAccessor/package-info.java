/*
 * Copyright (c) KaylasWorld.
 */

/**
 * Stable public API for indexing, scoped lookup, annotation binding, snapshots, and semantic value
 * access over KaylasUI and standard Swing component graphs.
 *
 * <p>The supported API is intentionally kept in this root package:</p>
 * <ul>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource} supplies
 *     logical panel and component graphs.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions} defines
 *     traversal, duplicate, form, and refresh policies.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueRegistry} resolves
 *     readable and writable semantic values.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor} coordinates
 *     indexing and exposes lookup, binding, and form APIs.</li>
 * </ul>
 *
 * <p>Implementation mechanisms are separated below the {@code internal} package by responsibility:
 * graph indexing, reflection binding, source adaptation, value extraction, and shared support.
 * Applications must not depend on those internal packages.</p>
 *
 * <p>Constructor-runtime children are addressed by their fully qualified ids, for example
 * {@code volume.slider}, or through scoped lookup methods such as
 * {@code requireLocal("volume", "slider", Slider.class)}.</p>
 */
package org.takesome.kaylasEngine.gui.componentAccessor;
