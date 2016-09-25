package org.jenkinsci.plugins.graniteclient;

import java.io.File;
import java.io.IOException;

import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import net.adamcin.granite.client.packman.PackId;

/**
 * Extracted the oft-used one-line callable to solve serialization issues
 */
public class IdentifyPackageCallable extends MasterToSlaveFileCallable<PackId> {

    @Override
    public PackId invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        return PackId.identifyPackage(f);
    }
}
