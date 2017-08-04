/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package org.jenkinsci.plugins.graniteclient;

import java.io.File;

import hudson.FilePath;
import net.adamcin.granite.client.packman.PackId;
import net.adamcin.granite.client.packman.PackIdFilter;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.tools.ant.types.selectors.SelectorUtils;

/**
 * Default implementation of {@link PackIdFilter} which parses a standard filter string
 * format, matching "*:*:*", for "group:name:version". Also supports filtering on FilePath,
 * such that the {@link #includes(hudson.FilePath,hudson.FilePath)} method includes
 */
public final class PathOrPackIdFilter implements PackIdFilter {
    public static final PathOrPackIdFilter INCLUDE_ALL_FILTER = new PathOrPackIdFilter("", null, null, null);

    public static final String WILDCARD = "*";

    private final String source;
    private final boolean pathFilter;
    private final String pathPattern;
    private final String group;
    private final String name;
    private final String version;

    public PathOrPackIdFilter(String source, String group, String name, String version) {
        this.source = source;
        this.pathFilter = false;
        this.pathPattern = null;
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public PathOrPackIdFilter(String pathPattern) {
        this.source = pathPattern;
        this.pathFilter = true;
        this.pathPattern = pathPattern;
        this.group = null;
        this.name = null;
        this.version = null;
    }

    public boolean isPathFilter() {
        return pathFilter;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getSource() {
        return source;
    }

    public boolean includes(PackId packId) {
        if (isPathFilter()) {
            return SelectorUtils.matchPath(normalizeSlashes(this.pathPattern),
                    normalizeSlashes(packId.getInstallationPath() + ".zip")) ||
                    SelectorUtils.matchPath(normalizeSlashes(this.pathPattern),
                            normalizeSlashes(packId.getInstallationPath() + ".jar"));

        } else {
            boolean includes = true;
            if (group != null && group.length() > 0) {
                includes = includes && (group.equals(WILDCARD) || group.equals(packId.getGroup()));
            }
            if (name != null && name.length() > 0) {
                includes = includes && (name.equals(WILDCARD) || name.equals(packId.getName()));
            }
            if (version != null && version.length() > 0) {
                includes = includes && (version.equals(WILDCARD) || version.equals(packId.getVersion()));
            }
            return includes;
        }
    }

    /**
     * Replace windows backslashes with forward slashes.
     * @param path the path to normalize
     * @return the normalized path
     */
    private static String normalizeSlashes(String path) {
        if (File.separatorChar == '\\') {
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }

    public boolean includes(FilePath basePath, FilePath filePath) {
        if (isPathFilter()) {
            String relPath = PathUtil.getRelativeFilePath(normalizeSlashes(basePath.getRemote()),
                    normalizeSlashes(filePath.getRemote()));
            return SelectorUtils.matchPath(normalizeSlashes(this.pathPattern), relPath);
        } else {
            return false;
        }
    }

    public static PathOrPackIdFilter parse(String filterString) {
        if (filterString == null) {
            return INCLUDE_ALL_FILTER;
        } else if (filterString.endsWith(".zip") || filterString.endsWith(".jar")) {
            return new PathOrPackIdFilter(filterString);
        } else {
            String[] parts = filterString.trim().split(":");
            switch (parts.length) {
                case 0: return INCLUDE_ALL_FILTER;
                case 1: return new PathOrPackIdFilter(filterString, null, parts[0], null);
                case 2: return new PathOrPackIdFilter(filterString, parts[0], parts[1], null);
                default: return new PathOrPackIdFilter(filterString, parts[0], parts[1], parts[2]);
            }
        }
    }

}
