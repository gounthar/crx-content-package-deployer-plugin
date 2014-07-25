package org.jenkinsci.plugins.graniteclient;

import hudson.model.Result;
import hudson.model.TaskListener;
import net.adamcin.granite.client.packman.PackId;
import net.adamcin.granite.client.packman.PackageManagerClient;
import net.adamcin.granite.client.packman.SimpleResponse;

import java.io.Serializable;
import java.util.List;

public class ReplicatePackagesClientCallable implements PackageManagerClientCallable<Result>, Serializable {

    private static final long serialVersionUID = -3352654487234220595L;
    private final TaskListener listener;
    private final List<PackId> packIds;
    private final boolean ignoreErrors;
    
    public ReplicatePackagesClientCallable(TaskListener listener, List<PackId> packIds, boolean ignoreErrors) {
    	this.listener = listener;
		this.packIds = packIds;
		this.ignoreErrors = ignoreErrors;
	}
	
	public Result doExecute(PackageManagerClient client) throws Exception {
		Result result = Result.SUCCESS;
        for (PackId packId : packIds) {
            client.waitForService();
            listener.getLogger().printf(
                    "Checking for package %s on server %s%n", packId, client.getBaseUrl()
            );
            if (client.existsOnServer(packId)) {
                listener.getLogger().printf("Found package: %s%n", client.getConsoleUiUrl(packId));
                listener.getLogger().printf("Replicating %s from %s%n", packId, client.getConsoleUiUrl(packId));

                SimpleResponse r_replicate = client.replicate(packId);
                if (r_replicate.isSuccess()) {
                    listener.getLogger().printf("Replication successful: %s%n", r_replicate.getMessage());
                    result = result.combine(Result.SUCCESS);
                } else {
                    listener.fatalError(r_replicate.getMessage());
                    return Result.FAILURE;
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
