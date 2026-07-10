/*
 * Copyright (c) KaylasWorld.
 */

/**
 * Provides indexing, scoped lookup, annotation binding, snapshots, and semantic value access for
 * KaylasUI and standard Swing component graphs.
 *
 * <p>The package is organized around four extension points:</p>
 * <ul>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource} supplies
 *     logical panel and component graphs.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessorOptions} defines
 *     traversal, duplicate, form, and refresh policies.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentValueRegistry} resolves
 *     readable and writable semantic values.</li>
 *     <li>{@link org.takesome.kaylasEngine.gui.componentAccessor.ComponentsAccessor} builds the
 *     live index and exposes lookup, binding, and form APIs.</li>
 * </ul>
 *
 * <p>Constructor-runtime children are addressed by their fully qualified ids, for example
 * {@code volume.slider}, or through scoped lookup methods such as
 * {@code requireLocal("volume", "slider", Slider.class)}.</p>
 */
package org.takesome.kaylasEngine.gui.componentAccessor;
