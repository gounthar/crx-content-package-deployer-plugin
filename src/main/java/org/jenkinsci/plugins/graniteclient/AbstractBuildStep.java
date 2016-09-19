package org.jenkinsci.plugins.graniteclient;

import java.io.IOException;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;

/**
 * Base class with some usefulness
 */
abstract class AbstractBuildStep extends Builder {

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new AbortException("no workspace for " + build);
        }
        return this.perform(build, workspace, launcher, listener);
    }

    abstract boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                             @Nonnull BuildListener listener) throws InterruptedException, IOException;
}
