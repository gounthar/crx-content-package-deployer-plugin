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

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of {@link hudson.FilePath.FileCallable} used by the {@link DownloadPackagesBuilder}
 */
public class DownloadPackagesCallable implements FilePath.FileCallable<Result> {

    private static final long serialVersionUID = 5909791609148794746L;
    protected final GraniteClientConfig clientConfig;
    protected final TaskListener listener;
    private final List<PackId> packIds;
    private final boolean ignoreErrors;
    private final boolean rebuild;
    private final ResponseProgressListener progressListener;

    public DownloadPackagesCallable(GraniteClientConfig clientConfig, TaskListener listener,
                                   List<PackId> packIds, boolean ignoreErrors,
                                   boolean rebuild) {
        this.clientConfig = clientConfig;
        this.listener = listener;
        this.packIds = packIds;
        this.ignoreErrors = ignoreErrors;
        this.rebuild = rebuild;
        this.progressListener = new JenkinsResponseProgressListener(listener);
    }

    private class Execution implements PackageManagerClientCallable<Result> {
        final File toDirectory;

        private Execution(File toDirectory) {
            this.toDirectory = toDirectory;
        }

        public Result doExecute(PackageManagerClient client) throws Exception {
            Result result = Result.SUCCESS;

            for (PackId packId : packIds) {
                client.waitForService();
                listener.getLogger().printf(
                        "Checking for package %s on server %s%n", packId, clientConfig.getBaseUrl()
                );
                if (client.existsOnServer(packId)) {
                    listener.getLogger().printf("Found package: %s%n", client.getConsoleUiUrl(packId));
                    if (rebuild) {
                        listener.getLogger().printf("Rebuilding %s.%n", packId);
                        DetailedResponse r_rebuild = client.build(packId, progressListener);
                        if (!r_rebuild.isSuccess()) {
                            listener.error(r_rebuild.getMessage());
                            if (ignoreErrors) {
                                result = result.combine(Result.UNSTABLE);
                            } else {
                                return Result.FAILURE;
                            }

                        } else {
                            if (r_rebuild.hasErrors()) {
                                result = result.combine(Result.UNSTABLE);
                            }
                        }
                    }
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

                } else {
                    listener.error("Package %s does not exist on server.", packId);
                    if (ignoreErrors) {
                        result = Result.UNSTABLE.combine(result);
                    } else  {
                        return Result.FAILURE;
                    }
                }
            }

            return result;
        }
    }

    public Result invoke(File toDirectory, VirtualChannel channel) throws IOException, InterruptedException {

        try {
            return GraniteClientExecutor.execute(new Execution(toDirectory), clientConfig, listener);
        } catch (Exception e) {
            e.printStackTrace(listener.fatalError("Failed to download packages.", e.getMessage()));
            if (ignoreErrors) {
                return Result.UNSTABLE;
            } else {
                return Result.FAILURE;
            }
        }
    }
}
