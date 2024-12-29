/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;

/**
 * @param gameDirectory
 * @param headless
 * @param forcedDist                If set to null, the distribution being launched is auto-detected, otherwise it is set to this.
 *                                  In a dev-environment where a "joined" distribution is being used, this parameter also disables
 *                                  access to classes and resources of the inactive distribution.
 * @param programArgs
 * @param claimedFiles
 * @param unclaimedClassPathEntries
 * @param parentClassLoader
 */
public record StartupArgs(
        File gameDirectory,
        boolean headless,
        @Nullable Dist forcedDist,
        String[] programArgs,
        Set<File> claimedFiles,
        List<File> unclaimedClassPathEntries,
        @Nullable ClassLoader parentClassLoader) {
    public Path cacheRoot() {
        return gameDirectory.toPath().resolve(".neoforgecache");
    }
}
