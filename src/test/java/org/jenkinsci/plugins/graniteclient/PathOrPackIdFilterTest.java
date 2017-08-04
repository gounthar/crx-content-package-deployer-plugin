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
import hudson.remoting.VirtualChannel;
import net.adamcin.commons.testing.junit.FailUtil;
import net.adamcin.granite.client.packman.PackId;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
/**
 * Created by madamcin on 3/20/14.
 */
public class PathOrPackIdFilterTest {

    @Test
    public void testPathFilter() {

        PackId packId = PackId.createPackId("test-group", "test-name", "1.0");

        PathOrPackIdFilter zipFilter = PathOrPackIdFilter.parse("**/*.zip");

        assertTrue("should be path filter", zipFilter.isPathFilter());
        assertTrue(zipFilter.getSource() + " includes packId: " + packId.toString(), zipFilter.includes(packId));

        PathOrPackIdFilter jarFilter = PathOrPackIdFilter.parse("**/*.jar");

        assertTrue("should be path filter", jarFilter.isPathFilter());
        assertTrue(jarFilter.getSource() + " includes packId: " + packId.toString(), jarFilter.includes(packId));

        FilePath baseDir = new FilePath(new File("target/testPathFilter"));
        FilePath packFile = new FilePath(baseDir, packId.getInstallationPath().substring(1) + ".zip");
        FilePath winBaseDir = new FilePath((VirtualChannel) null, "C:\\target\\testPathFilter");
        FilePath winPackFile = new FilePath((VirtualChannel) null, "C:\\target\\testPathFilter\\" + packId.getInstallationPath().substring(1).replace("/", "\\") + ".zip");

        FilePath groupDir = packFile.getParent();

        try {
            groupDir.mkdirs();
            packFile.touch(System.currentTimeMillis());

            PathOrPackIdFilter packNameFilter = PathOrPackIdFilter.parse("**/test-name-*.zip");
            assertTrue("packNameFilter should include file", packNameFilter.includes(baseDir, packFile));
            assertTrue("packNameFilter should include win file", packNameFilter.includes(winBaseDir, winPackFile));

            PathOrPackIdFilter winPackNameFilter = PathOrPackIdFilter.parse("**\\test-name-*.zip");
            assertTrue("winPackNameFilter should include win file", winPackNameFilter.includes(winBaseDir, winPackFile));

            assertTrue("packNameFilter should include file (when absolutized)", packNameFilter.includes(baseDir.absolutize(), packFile.absolutize()));

            PathOrPackIdFilter badNameFilter = PathOrPackIdFilter.parse("**/bad-name-*.zip");
            assertFalse("badNameFilter should not include file", badNameFilter.includes(baseDir, packFile));

            PathOrPackIdFilter installationPathFilter = PathOrPackIdFilter.parse((packId.getInstallationPath() + ".zip").substring(1));
            assertTrue("installationPathFilter should include file (when absolutized)",
                    installationPathFilter.includes(baseDir.absolutize(), packFile.absolutize()));

            PathOrPackIdFilter groupFilter = PathOrPackIdFilter.parse("**/test-group/*.zip");
            assertTrue("groupFilter should include file", groupFilter.includes(baseDir, packFile));

            PathOrPackIdFilter badGroupFilter = PathOrPackIdFilter.parse("**/bad-group/*.zip");
            assertFalse("badGroupFilter should not include file", badGroupFilter.includes(baseDir, packFile));

        } catch (Exception e) {
            FailUtil.sprintFail(e);
        }


    }
}
