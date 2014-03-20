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

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import net.adamcin.granite.client.packman.DetailedResponse;
import net.adamcin.granite.client.packman.DownloadResponse;
import net.adamcin.granite.client.packman.PackId;
import net.adamcin.granite.client.packman.PackageManagerClient;
import net.adamcin.granite.client.packman.ResponseProgressListener;
import net.adamcin.granite.client.packman.SimpleResponse;
import net.adamcin.granite.client.packman.WspFilter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of {@link hudson.FilePath.FileCallable} used by the {@link org.jenkinsci.plugins.graniteclient.BuildPackageBuilder}
 */
public class BuildPackageCallable implements FilePath.FileCallable<Result> {

    private static final long serialVersionUID = 1329103722879551699L;
    private final GraniteClientConfig clientConfig;
    private final TaskListener listener;
    private final PackId packId;
    private final WspFilter wspFilter;
    private final boolean download;
    private final ResponseProgressListener progressListener;

    public BuildPackageCallable(GraniteClientConfig clientConfig, TaskListener listener,
                                PackId packId, WspFilter wspFilter, boolean download) {
        this.clientConfig = clientConfig;
        this.listener = listener;
        this.packId = packId;
        this.wspFilter = wspFilter;
        this.download = download;
        this.progressListener = new JenkinsResponseProgressListener(listener);
    }

    private class Execution implements PackageManagerClientCallable<Result> {
        final File toDirectory;

        private Execution(File toDirectory) {
            this.toDirectory = toDirectory;
        }

        public Result doExecute(PackageManagerClient client) throws Exception {
            Result result = Result.SUCCESS;

            client.waitForService();
            listener.getLogger().printf(
                    "Checking for package %s on server %s%n", packId, clientConfig.getBaseUrl()
            );

            // first, create the package if it doesn't exist.
            if (client.existsOnServer(packId)) {
                listener.getLogger().printf("Found package: %s%n", client.getConsoleUiUrl(packId));
            } else {
                listener.getLogger().printf("Creating package.%n");
                SimpleResponse r_create = client.create(packId);
                if (r_create.isSuccess()) {
                    listener.getLogger().println(r_create.getMessage());
                } else {
                    listener.fatalError(r_create.getMessage());
                    return Result.FAILURE;
                }
            }

            // next, update the workspace filter if it is defined
            if (wspFilter != null) {
                SimpleResponse r_updateFilter = client.updateFilter(packId, wspFilter);
                if (r_updateFilter.isSuccess()) {
                    listener.getLogger().println(r_updateFilter.getMessage());
                } else {
                    listener.fatalError(r_updateFilter.getMessage());
                    return Result.FAILURE;
                }
            }

            // next, build the package
            listener.getLogger().printf("Building package %s.%n", packId);
            DetailedResponse r_rebuild = client.build(packId, progressListener);
            if (r_rebuild.isSuccess()) {
                if (r_rebuild.hasErrors()) {
                    result = result.combine(Result.UNSTABLE);
                }
                listener.getLogger().println(r_rebuild.getMessage());
                listener.getLogger().printf("Package location: %s%n", client.getConsoleUiUrl(packId));
            } else {
                listener.fatalError(r_rebuild.getMessage());
                return Result.FAILURE;
            }

            // finally, download the package if requested
            if (download) {
                listener.getLogger().printf("Downloading %s to %s%n", packId, toDirectory);

                DownloadResponse response = client.downloadToDirectory(packId, toDirectory);
                listener.getLogger().printf("Downloaded %d bytes to file %s.%n", response.getLength(), response.getContent());
                listener.getLogger().printf("Verifying downloaded package...%n");
                PackId reId = PackId.identifyPackage(response.getContent());
                if (packId.equals(reId)) {
                    listener.getLogger().printf("Package verified as %s.%n", packId);
                } else {
                    throw new Exception("Package verification failed: " + response.getContent());
                }
            }

            return result;
        }
    }

    public Result invoke(File toDirectory, VirtualChannel channel) throws IOException, InterruptedException {

        try {
            return GraniteClientExecutor.execute(new Execution(toDirectory), clientConfig, listener);
        } catch (Exception e) {
            e.printStackTrace(listener.fatalError("Failed to build package.", e.getMessage()));
            return Result.FAILURE;
        }
    }
}
