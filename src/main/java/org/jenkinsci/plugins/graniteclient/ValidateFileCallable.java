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
import net.adamcin.granite.client.packman.validation.DefaultValidationOptions;
import net.adamcin.granite.client.packman.validation.PackageValidator;
import net.adamcin.granite.client.packman.validation.ValidationResult;

import java.io.File;
import java.io.IOException;

/**
 * Simple callable implementation for the ValidatePackagesBuilder
 */
public class ValidateFileCallable implements FilePath.FileCallable<Result> {

    final TaskListener listener;
    final DefaultValidationOptions options;

    public ValidateFileCallable(TaskListener listener, DefaultValidationOptions options) {
        this.listener = listener;
        this.options = options;
    }

    public Result invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {

        final ValidationResult result = PackageValidator.validate(f, options);
        if (result.getReason() == ValidationResult.Reason.SUCCESS) {
            return Result.SUCCESS;
        }

        switch (result.getReason()) {
            case ROOT_NOT_ALLOWED:
                listener.fatalError("Package workspace filter defines a filter root which is not covered by the validation filter.");
                listener.error("Invalid filter set: %n%s", result.getInvalidRoot().toSpec());
                break;
            case ROOT_MISSING_RULES:
                listener.fatalError("Package workspace filter defines a filter root which does not list the rules required by its covering validation filter.");
                listener.error("Invalid filter set: %n%s", result.getInvalidRoot().toSpec());
                listener.error("Covered by validation filter set: %n%s", result.getCoveringRoot().toSpec());
                break;
            case FORBIDDEN_EXTENSION:
                listener.fatalError("Package Jar contains an entry with a forbidden file extension.");
                listener.error("Invalid Jar entry: %s", result.getForbiddenEntry());
                break;
            case FAILED_TO_ID:
            case FAILED_TO_OPEN:
            case INVALID_META_INF:
            default:
                listener.fatalError("Package validation of %s did not succeed because of %s", f.getAbsolutePath(), result.getReason().name());
                if (result.getCause() != null) {
                    listener.error("Caused by %s", result.getCause());
                }
                break;
        }
        return Result.FAILURE;
    }
}
