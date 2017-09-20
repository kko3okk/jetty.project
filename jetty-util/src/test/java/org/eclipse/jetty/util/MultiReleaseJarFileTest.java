//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jetty.toolchain.test.AdvancedRunner;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(AdvancedRunner.class)
public class MultiReleaseJarFileTest
{
    private File testResources = MavenTestingUtils.getTestResourcesDir().getAbsoluteFile();
    private File example = new File(testResources,"example.jar");

    @Test
    public void testExampleJarIsMR() throws Exception
    {
        MultiReleaseJarFile jarFile = new MultiReleaseJarFile(example);
        assertTrue(jarFile.isMultiRelease());
    }

    @Test
    public void testBase() throws Exception
    {
        MultiReleaseJarFile jarFile = new MultiReleaseJarFile(example,8,false);
        assertThat(jarFile.getEntry("META-INF/MANIFEST.MF").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/OnlyInBase.class").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/InBoth$InnerBase.class").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/InBoth$InnerBoth.class").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/InBoth.class").getVersion(), is(0));

        assertThat(jarFile.stream().count(), is(5L));
    }

    @Test
    public void test9() throws Exception
    {
        MultiReleaseJarFile jarFile = new MultiReleaseJarFile(example,9,false);
        assertThat(jarFile.getEntry("META-INF/MANIFEST.MF").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/OnlyInBase.class").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/InBoth$InnerBoth.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/InBoth.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/OnlyIn9.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/onlyIn9/OnlyIn9.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/InBoth$Inner9.class").getVersion(), is(9));

        assertThat(jarFile.stream().count(), is(7L));
    }

    @Test
    public void test10() throws Exception
    {
        MultiReleaseJarFile jarFile = new MultiReleaseJarFile(example,10,false);
        assertThat(jarFile.getEntry("META-INF/MANIFEST.MF").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/OnlyInBase.class").getVersion(), is(0));
        assertThat(jarFile.getEntry("org/example/InBoth.class").getVersion(), is(10));
        assertThat(jarFile.getEntry("org/example/OnlyIn9.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/onlyIn9/OnlyIn9.class").getVersion(), is(9));
        assertThat(jarFile.getEntry("org/example/In10Only.class").getVersion(), is(10));

        assertThat(jarFile.stream().count(), is(6L));
    }


}
