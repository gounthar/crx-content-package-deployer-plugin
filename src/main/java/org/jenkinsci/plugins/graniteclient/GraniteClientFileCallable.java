package org.jenkinsci.plugins.graniteclient;

import jenkins.MasterToSlaveFileCallable;

/**
 * Common Base class for FileCallable implementations
 */
public abstract class GraniteClientFileCallable<T> extends MasterToSlaveFileCallable<T> {

}
