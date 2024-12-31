/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides context for various FML plugins about the current launch operation.
 */
public interface ILaunchContext {
    Logger LOGGER = LoggerFactory.getLogger(ILaunchContext.class);

    Dist getRequiredDistribution();

    /**
     * The game directory.
     */
    Path gameDirectory();

    <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass);

    /**
     * Checks if a given path was already found by a previous locator, or may be already loaded.
     */
    boolean isLocated(Path path);

    /**
     * Marks a path as being located and returns true if it was not previously located.
     */
    boolean addLocated(Path path);

    /**
     * Returns the list of yet {@link #addLocated(Path) unclaimed} class path entries.
     */
    List<File> getUnclaimedClassPathEntries();

    /**
     * Set a more descriptive source for a Jar file.
     * Use this to set source info for Jar files extracted to a shared cache, for example.
     */
    void setJarSourceDescription(Path path, String description);

    /**
     * Retrieves information set via {@link #setJarSourceDescription(Path, String)}.
     */
    @Nullable
    String getJarSourceDescription(Path path);

    /**
     * Converts a path to a human-readable representation that tries to omit the game directory or other
     * well-known locations from a given path.
     */
    String relativizePath(Path path);
}
