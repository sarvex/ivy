/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.osgi.obr;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;

import junit.framework.TestCase;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.osgi.core.BundleInfo;
import org.apache.ivy.osgi.core.ManifestParser;
import org.apache.ivy.osgi.obr.OBRResolver;
import org.apache.ivy.osgi.repo.BundleInfoAdapter;
import org.apache.ivy.osgi.repo.BundleRepoResolver.RequirementStrategy;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.DualResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;

public class OBRResolverTest extends TestCase {

    private static final ModuleRevisionId MRID_TEST_BUNDLE = ModuleRevisionId.newInstance("",
        "org.apache.ivy.osgi.testbundle", "1.2.3", BundleInfoAdapter.OSGI_BUNDLE);

    private static final ModuleRevisionId MRID_TEST_BUNDLE_IMPORTING = ModuleRevisionId
            .newInstance("", "org.apache.ivy.osgi.testbundle.importing", "3.2.1",
                BundleInfoAdapter.OSGI_BUNDLE);

    private static final ModuleRevisionId MRID_TEST_BUNDLE_IMPORTING_VERSION = ModuleRevisionId
            .newInstance("", "org.apache.ivy.osgi.testbundle.importing.version", "3.2.1",
                BundleInfoAdapter.OSGI_BUNDLE);

    private static final ModuleRevisionId MRID_TEST_BUNDLE_IMPORTING_OPTIONAL = ModuleRevisionId
            .newInstance("", "org.apache.ivy.osgi.testbundle.importing.optional", "3.2.1",
                BundleInfoAdapter.OSGI_BUNDLE);

    private static final ModuleRevisionId MRID_TEST_BUNDLE_USE = ModuleRevisionId.newInstance("",
        "org.apache.ivy.osgi.testbundle.use", "2.2.2", BundleInfoAdapter.OSGI_BUNDLE);

    private static final ModuleRevisionId MRID_TEST_BUNDLE_EXPORTING_AMBIGUITY = ModuleRevisionId
            .newInstance("", "org.apache.ivy.osgi.testbundle.exporting.ambiguity", "3.3.3",
                BundleInfoAdapter.OSGI_BUNDLE);

    private IvySettings settings;

    private File cache;

    private ResolveData data;

    private Ivy ivy;

    private OBRResolver bundleResolver;

    private OBRResolver bundleUrlResolver;

    private DualResolver dualResolver;

    public void setUp() throws Exception {
        settings = new IvySettings();

        bundleResolver = new OBRResolver();
        bundleResolver.setRepoXmlFile(new File("test/test-repo/bundlerepo/repo.xml")
                .getAbsolutePath());
        bundleResolver.setName("bundle");
        bundleResolver.setSettings(settings);
        settings.addResolver(bundleResolver);

        bundleUrlResolver = new OBRResolver();
        bundleUrlResolver.setRepoXmlURL(new File("test/test-repo/bundlerepo/repo.xml").toURI()
                .toURL().toExternalForm());
        bundleUrlResolver.setName("bundleurl");
        bundleUrlResolver.setSettings(settings);
        settings.addResolver(bundleUrlResolver);

        dualResolver = new DualResolver();
        OBRResolver resolver = new OBRResolver();
        resolver.setRepoXmlFile(new File("test/test-repo/ivyrepo/repo.xml").getAbsolutePath());
        resolver.setName("dual-bundle");
        resolver.setSettings(settings);
        dualResolver.add(resolver);
        dualResolver.setName("dual");
        File ivyrepo = new File("test/test-repo/ivyrepo");
        FileSystemResolver fileSystemResolver = new FileSystemResolver();
        fileSystemResolver.addIvyPattern(ivyrepo.getAbsolutePath()
                + "/[organisation]/[module]/[revision]/ivy.xml");
        fileSystemResolver.addArtifactPattern(ivyrepo.getAbsolutePath()
                + "/[organisation]/[module]/[revision]/[type]s/[artifact]-[revision].[ext]");
        fileSystemResolver.setName("dual-file");
        fileSystemResolver.setSettings(settings);
        dualResolver.add(fileSystemResolver);
        settings.addResolver(dualResolver);

        settings.setDefaultResolver("bundle");

        cache = new File("build/cache");
        cache.mkdirs();
        settings.setDefaultCache(cache);

        ivy = new Ivy();
        ivy.setSettings(settings);
        ivy.bind();

        ivy.getResolutionCacheManager().clean();
        RepositoryCacheManager[] caches = settings.getRepositoryCacheManagers();
        for (int i = 0; i < caches.length; i++) {
            caches[i].clean();
        }

        data = new ResolveData(ivy.getResolveEngine(), new ResolveOptions());
    }

    public void testSimpleResolve() throws Exception {
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("", "org.apache.ivy.osgi.testbundle",
            "1.2.3", BundleInfoAdapter.OSGI_BUNDLE);
        genericTestResolveDownload(bundleResolver, mrid);
    }

    public void testSimpleUrlResolve() throws Exception {
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("", "org.apache.ivy.osgi.testbundle",
            "1.2.3", BundleInfoAdapter.OSGI_BUNDLE);
        genericTestResolveDownload(bundleUrlResolver, mrid);
    }

    public void testResolveDual() throws Exception {
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("", "org.apache.ivy.osgi.testbundle",
            "1.2.3", BundleInfoAdapter.OSGI_BUNDLE);
        genericTestResolveDownload(dualResolver, mrid);
    }

    private void genericTestResolveDownload(DependencyResolver resolver, ModuleRevisionId mrid)
            throws ParseException {
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid,
                false), data);
        assertNotNull(rmr);
        assertEquals(mrid, rmr.getId());

        Artifact artifact = rmr.getDescriptor().getAllArtifacts()[0];
        DownloadReport report = resolver.download(new Artifact[] {artifact}, new DownloadOptions());
        assertNotNull(report);

        assertEquals(1, report.getArtifactsReports().length);

        ArtifactDownloadReport ar = report.getArtifactReport(artifact);
        assertNotNull(ar);

        assertEquals(artifact, ar.getArtifact());
        assertEquals(DownloadStatus.SUCCESSFUL, ar.getDownloadStatus());

        // test to ask to download again, should use cache
        report = resolver.download(new Artifact[] {artifact}, new DownloadOptions());
        assertNotNull(report);

        assertEquals(1, report.getArtifactsReports().length);

        ar = report.getArtifactReport(artifact);
        assertNotNull(ar);

        assertEquals(artifact, ar.getArtifact());
        assertEquals(DownloadStatus.NO, ar.getDownloadStatus());
    }

    public void testResolveImporting() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE});
    }

    public void testResolveImportingOptional() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.optional_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {});
        genericTestResolve(jarName, "optional", new ModuleRevisionId[] {MRID_TEST_BUNDLE});
        genericTestResolve(jarName, "transitive-optional",
            new ModuleRevisionId[] {MRID_TEST_BUNDLE});
    }

    public void testResolveImportingTransitiveOptional() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.transitiveoptional_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {});
        genericTestResolve(jarName, "optional",
            new ModuleRevisionId[] {MRID_TEST_BUNDLE_IMPORTING_OPTIONAL});
        genericTestResolve(jarName, "transitive-optional", new ModuleRevisionId[] {
                MRID_TEST_BUNDLE, MRID_TEST_BUNDLE_IMPORTING_OPTIONAL});
    }

    public void testResolveImportingVersion() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.version_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE});
    }

    public void testResolveImportingRangeVersion() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.rangeversion_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE});
    }

    public void testResolveUse() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.use_2.2.2.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {});
    }

    public void testResolveImportingUse() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.use_3.2.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE_USE,
                MRID_TEST_BUNDLE_IMPORTING, MRID_TEST_BUNDLE});
    }

    public void testResolveRequire() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.require_1.1.1.jar";
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE,
                MRID_TEST_BUNDLE_IMPORTING_VERSION});
    }

    public void testResolveOptionalConf() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.require_1.1.1.jar";
        genericTestResolve(jarName, "optional", new ModuleRevisionId[] {MRID_TEST_BUNDLE,
                MRID_TEST_BUNDLE_IMPORTING_VERSION});
    }

    public void testResolveImportAmbiguity() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.ambiguity_3.2.1.jar";
        bundleResolver.setImportPackageStrategy(RequirementStrategy.first);
        genericTestResolve(jarName, "default",
            new ModuleRevisionId[] {MRID_TEST_BUNDLE_EXPORTING_AMBIGUITY});
    }

    public void testResolveImportNoAmbiguity() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.importing.ambiguity_3.2.1.jar";
        bundleResolver.setImportPackageStrategy(RequirementStrategy.noambiguity);
        genericTestFailingResolve(jarName, "default");
    }

    public void testResolveRequireAmbiguity() throws Exception {
        String jarName = "org.apache.ivy.osgi.testbundle.require.ambiguity_1.1.1.jar";
        bundleResolver.setImportPackageStrategy(RequirementStrategy.noambiguity);
        genericTestResolve(jarName, "default", new ModuleRevisionId[] {MRID_TEST_BUNDLE,
                MRID_TEST_BUNDLE_IMPORTING_VERSION});
    }

    private void genericTestResolve(String jarName, String conf, ModuleRevisionId[] expectedMrids)
            throws Exception {
        JarInputStream in = new JarInputStream(new FileInputStream("test/test-repo/bundlerepo/"
                + jarName));
        BundleInfo bundleInfo = ManifestParser.parseManifest(in.getManifest());
        DefaultModuleDescriptor md = BundleInfoAdapter.toModuleDescriptor(bundleInfo, null);
        ResolveReport resolveReport = ivy.resolve(md,
            new ResolveOptions().setConfs(new String[] {conf}).setOutputReport(false));
        assertFalse("resolve failed " + resolveReport.getProblemMessages(),
            resolveReport.hasError());
        Set/* <ModuleRevisionId> */actual = new HashSet/* <ModuleRevisionId> */();
        List/* <Artifact> */artifacts = resolveReport.getArtifacts();
        Iterator itArtfact = artifacts.iterator();
        while (itArtfact.hasNext()) {
            Artifact artfact = (Artifact) itArtfact.next();
            actual.add(artfact.getModuleRevisionId());
        }
        Set/* <ModuleRevisionId> */expected = new HashSet/* <ModuleRevisionId> */(
                Arrays.asList(expectedMrids));
        assertEquals(expected, actual);
    }

    private void genericTestFailingResolve(String jarName, String conf) throws Exception {
        JarInputStream in = new JarInputStream(new FileInputStream("test/test-repo/bundlerepo/"
                + jarName));
        BundleInfo bundleInfo = ManifestParser.parseManifest(in.getManifest());
        DefaultModuleDescriptor md = BundleInfoAdapter.toModuleDescriptor(bundleInfo, null);
        ResolveReport resolveReport = ivy.resolve(md,
            new ResolveOptions().setConfs(new String[] {conf}).setOutputReport(false));
        assertTrue(resolveReport.hasError());
    }

}
