package org.nhindirect.config.processor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Calendar;

import org.apache.commons.io.FileUtils;

import org.nhindirect.config.SpringBaseTest;
import org.nhindirect.config.model.TrustBundle;
import org.nhindirect.config.model.TrustBundleAnchor;
import org.nhindirect.config.processor.BundleCacheUpdateProcessor;
import org.nhindirect.config.processor.BundleRefreshProcessor;
import org.nhindirect.config.resources.TrustBundleResource;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Mono;

public class DefaultBundleCacheUpdateProcessorImpl_springInitTest extends SpringBaseTest
{
	@Autowired
	protected TrustBundleResource trustService;

	@Autowired
	protected BundleRefreshProcessor refreshProcessor;
	
	@Autowired
	protected BundleCacheUpdateProcessor updateProcessor;
	
	@Test
	public void testLoadConfigService_validSpringConfig_assertComponentsLoaded() throws Exception
	{

		assertNotNull(trustService);
		assertNotNull(updateProcessor);
		assertNotNull(refreshProcessor);	
			
	}
	
	@Test
	public void testLoadConfigService_addTrustBundle_bundleAnchorsAdded() throws Exception
	{
		File bundleLocation = new File("./src/test/resources/bundles/signedbundle.p7b");
		
		
		final TrustBundle bundle = new TrustBundle();
		bundle.setBundleName("Test Bundle");
		bundle.setBundleURL(filePrefix + bundleLocation.getAbsolutePath());
		
		webClient.put().uri("trustbundle/")
		.body(Mono.just(bundle), TrustBundle.class)
		.retrieve().bodyToMono(String.class).block();
		
		final Mono<TrustBundle> addedBundle = trustService.getTrustBundleByName("Test Bundle");
		final TrustBundle addBundle = addedBundle.block();
		
		assertNotNull(addBundle);
		
		assertTrue(addBundle.getTrustBundleAnchors().size() > 0);		
		
		for (TrustBundleAnchor anchor : addBundle.getTrustBundleAnchors())
			assertNotNull(anchor.getAnchorData());
	}
	
	@Test
	public void testLoadConfigService_refreshBundle_assertBundleRefreshed() throws Exception
	{
		File bundleLocation = new File("./src/test/resources/bundles/signedbundle.p7b");
		
		final TrustBundle bundle = new TrustBundle();
		bundle.setBundleName("Test Bundle");
		bundle.setBundleURL(filePrefix + bundleLocation.getAbsolutePath());
		
		webClient.put().uri("trustbundle/")
		.body(Mono.just(bundle), TrustBundle.class)
		.retrieve().bodyToMono(String.class).block();
		
		final Mono<TrustBundle> addedBundle = trustService.getTrustBundleByName("Test Bundle");
		final TrustBundle addBundle = addedBundle.block();
		assertTrue(addBundle.getTrustBundleAnchors().size() > 0);	
		final Calendar lastRefreshAttemp = addBundle.getLastRefreshAttempt();
		final Calendar lastSuccessfulRefresh = addBundle.getLastSuccessfulRefresh();
		
		// now refresh
		trustService.refreshTrustBundle(addBundle.getBundleName()).block();
		
		final Mono<TrustBundle> refreshedBundle = trustService.getTrustBundleByName("Test Bundle");
		final TrustBundle refreshBunle = refreshedBundle.block();
		assertEquals(lastSuccessfulRefresh.getTimeInMillis(), refreshBunle.getLastSuccessfulRefresh().getTimeInMillis());
		assertTrue(refreshBunle.getLastRefreshAttempt().getTimeInMillis() > lastRefreshAttemp.getTimeInMillis());
	}
	
	@Test
	public void testLoadConfigService_refreshBundle_newBundleData_assertBundleRefreshed() throws Exception
	{
		final File originalBundleLocation = new File("./src/test/resources/bundles/signedbundle.p7b");
		final File updatedBundleLocation = new File("./src/test/resources/bundles/providerTestBundle.p7b");
		
		final File targetTempFileLocation = new File("./target/tempFiles/bundle.p7b");
		
		// copy the original bundle to the target location
		FileUtils.copyFile(originalBundleLocation, targetTempFileLocation);
		
		final TrustBundle bundle = new TrustBundle();
		bundle.setBundleName("Test Bundle");
		bundle.setBundleURL(filePrefix + targetTempFileLocation.getAbsolutePath());
		
		webClient.put().uri("trustbundle/")
		.body(Mono.just(bundle), TrustBundle.class)
		.retrieve().bodyToMono(String.class).block();
		
		
		final Mono<TrustBundle> addedBundle = trustService.getTrustBundleByName("Test Bundle");
		final TrustBundle addBundle = addedBundle.block();
		
		
		assertTrue(addBundle.getTrustBundleAnchors().size() > 0);
		
		// validate the contents of the bundle
		final Mono<TrustBundle> firstBundleInsert = trustService.getTrustBundleByName("Test Bundle");
		assertEquals(1, firstBundleInsert.block().getTrustBundleAnchors().size());
		
		// copy in the new bundle
		FileUtils.copyFile(updatedBundleLocation, targetTempFileLocation);
		
		// now refresh
		trustService.refreshTrustBundle(addBundle.getBundleName()).block();
		
		final Mono<TrustBundle> refreshedBundle = trustService.getTrustBundleByName("Test Bundle");
		assertEquals(6, refreshedBundle.block().getTrustBundleAnchors().size());
	}
}
