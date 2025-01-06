/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.jarjar.selection.JarSelector;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

public class JarInJarDependencyLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        List<IModFile> dependenciesToLoad = JarSelector.detectAndSelect(
                loadedMods,
                this::loadResourceFromModFile,
                (file, path) -> loadModFileFrom(file, path, pipeline),
                this::identifyMod,
                this::exception);

        if (dependenciesToLoad.isEmpty()) {
            LOGGER.info("No dependencies to load found. Skipping!");
        } else {
            LOGGER.info("Found {} dependencies adding them to mods collection", dependenciesToLoad.size());
            for (var modFile : dependenciesToLoad) {
                pipeline.addModFile(modFile);
            }
        }
    }

    @SuppressWarnings("resource")
    protected Optional<IModFile> loadModFileFrom(IModFile file, String path, IDiscoveryPipeline pipeline) {
        try {
            var cacheDir = FMLLoader.current().getCacheDir();
            var jarInMemory = file.getContent().readFile(path);
            var md = MessageDigest.getInstance("MD5");
            var hash = HexFormat.of().formatHex(md.digest(jarInMemory));

            var lastSep = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
            var filename = path.substring(lastSep + 1);

            var jarCacheDir = cacheDir.resolve("embedded_jars").resolve(hash);
            Files.createDirectories(jarCacheDir);
            var cachedFile = jarCacheDir.resolve(filename);
            long expectedSize = jarInMemory.length;
            long existingSize = -1;
            try {
                existingSize = Files.size(cachedFile);
            } catch (IOException ignored) {}
            if (existingSize != expectedSize) {
                // TODO atomic move crap
                Files.write(cachedFile, jarInMemory);
            }

            return pipeline.addPath(cachedFile, ModFileDiscoveryAttributes.DEFAULT.withParent(file), IncompatibleFileReporting.ERROR);
        } catch (Exception e) {
            LOGGER.error("Failed to load mod file {} from {}", path, file.getFileName());
            final RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
            exception.initCause(e);

            throw exception;
        }
    }

    protected ModLoadingException exception(Collection<JarSelector.ResolutionFailureInformation<IModFile>> failedDependencies) {
        final List<ModLoadingIssue> errors = failedDependencies.stream()
                .filter(entry -> !entry.sources().isEmpty()) //Should never be the case, but just to be sure
                .map(this::buildExceptionData)
                .toList();

        return new ModLoadingException(errors);
    }

    private ModLoadingIssue buildExceptionData(final JarSelector.ResolutionFailureInformation<IModFile> entry) {
        var artifact = entry.identifier().group() + ":" + entry.identifier().artifact();
        var requestedBy = entry.sources()
                .stream()
                .flatMap(this::getModWithVersionRangeStream)
                .map(this::formatError)
                .collect(Collectors.joining(", "));
        return ModLoadingIssue.error(getErrorTranslationKey(entry), artifact, requestedBy);
    }

    private String getErrorTranslationKey(final JarSelector.ResolutionFailureInformation<IModFile> entry) {
        return entry.failureReason() == JarSelector.FailureReason.VERSION_RESOLUTION_FAILED ? "fml.modloadingissue.dependencyloading.conflictingdependencies" : "fml.modloadingissue.dependencyloading.mismatchedcontaineddependencies";
    }

    private Stream<ModWithVersionRange> getModWithVersionRangeStream(final JarSelector.SourceWithRequestedVersionRange<IModFile> file) {
        return file.sources()
                .stream()
                .map(IModFile::getModFileInfo)
                .flatMap(modFileInfo -> modFileInfo.getMods().stream())
                .map(modInfo -> new ModWithVersionRange(modInfo, file.requestedVersionRange(), file.includedVersion()));
    }

    private String formatError(final ModWithVersionRange modWithVersionRange) {
        return "\u00a7e" + modWithVersionRange.modInfo().getModId() + "\u00a7r - \u00a74" + modWithVersionRange.versionRange().toString() + "\u00a74 - \u00a72" + modWithVersionRange.artifactVersion().toString() + "\u00a72";
    }

    protected String identifyMod(final IModFile modFile) {
        if (modFile.getModFileInfo() == null) {
            return modFile.getFileName();
        }
        // If this is a library, it won't have any mod IDs, so we use the module name instead.
        if (modFile.getModInfos().isEmpty()) {
            // Prefix to ensure this cannot collide with any true mod ID.
            return "library:" + modFile.getModFileInfo().moduleName();
        }

        return modFile.getModInfos().stream().map(IModInfo::getModId).collect(Collectors.joining());
    }

    private record ModWithVersionRange(IModInfo modInfo, VersionRange versionRange, ArtifactVersion artifactVersion) {}

    protected Optional<InputStream> loadResourceFromModFile(final IModFile modFile, final String path) {
        try {
            return Optional.of(Files.newInputStream(modFile.findResource(path)));
        } catch (final NoSuchFileException e) {
            LOGGER.trace("Failed to load resource {} from {}, it does not contain dependency information.", path, modFile.getFileName());
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("Failed to load resource {} from mod {}, cause {}", path, modFile.getFileName(), e);
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "jarinjar";
    }
}
