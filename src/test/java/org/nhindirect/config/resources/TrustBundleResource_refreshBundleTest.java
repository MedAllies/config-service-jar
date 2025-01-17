package org.nhindirect.config.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.nhindirect.config.BaseTestPlan;
import org.nhindirect.config.SpringBaseTest;
import org.nhindirect.config.model.BundleRefreshError;
import org.nhindirect.config.model.TrustBundle;
import org.nhindirect.config.repository.TrustBundleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

public class TrustBundleResource_refreshBundleTest extends SpringBaseTest
{
	@Autowired
	protected TrustBundleResource bundleService;
	
		
		abstract class TestPlan extends BaseTestPlan 
		{	
			@Override
			protected void tearDownMocks()
			{

			}

			protected abstract Collection<TrustBundle> getBundlesToAdd();
			
			protected abstract String getBundleNameToRefresh();
			
			@Override
			protected void performInner() throws Exception
			{				
				
				final Collection<TrustBundle> bundlesToAdd = getBundlesToAdd();
				
				if (bundlesToAdd != null)
				{
					bundlesToAdd.forEach(addBundle->
					{
						final HttpEntity<TrustBundle> requestEntity = new HttpEntity<>(addBundle);
						final ResponseEntity<Void> resp = testRestTemplate.exchange("/trustbundle", HttpMethod.PUT, requestEntity, Void.class);
						if (resp.getStatusCodeValue() != 201)
							throw new HttpClientErrorException(resp.getStatusCode());
					});
				}
				
				final ResponseEntity<Void> resp = testRestTemplate.exchange("/trustbundle/{bundle}/refreshBundle/", HttpMethod.POST, null, Void.class,
						getBundleNameToRefresh());
				if (resp.getStatusCodeValue() != 204)
					throw new HttpClientErrorException(resp.getStatusCode());
				
				final ResponseEntity<TrustBundle> getBundle = testRestTemplate.getForEntity("/trustbundle/" + getBundleNameToRefresh(), TrustBundle.class);

				int statusCode = getBundle.getStatusCodeValue();
				if (statusCode == 404)
					doAssertions(null);
				else if (statusCode == 200)
					doAssertions(getBundle.getBody());
				else
					throw new HttpClientErrorException(getBundle.getStatusCode());	
				
			}
				
			protected void doAssertions(TrustBundle bundle) throws Exception
			{
				
			}
		}	
		
		@Test
		public void testRefershBundle_assertBundleRefreshed()  throws Exception
		{
			new TestPlan()
			{
				protected Collection<TrustBundle> bundles;
				
				@Override
				protected Collection<TrustBundle> getBundlesToAdd()
				{
					try
					{
						bundles = new ArrayList<TrustBundle>();
						
						TrustBundle bundle = new TrustBundle();
						bundle.setBundleName("testBundle1");
						File fl = new File("src/test/resources/bundles/providerTestBundle.p7b");
						bundle.setBundleURL(filePrefix + fl.getAbsolutePath());	
						bundle.setRefreshInterval(24);
						bundle.setSigningCertificateData(null);		
						bundles.add(bundle);
						
						return bundles;
					}
					catch (Exception e)
					{
						throw new RuntimeException (e);
					}
				}
				
				@Override
				protected String getBundleNameToRefresh()
				{
					return "testBundle1";
				}
				
				protected void doAssertions(TrustBundle bundle) throws Exception
				{
					assertTrue(bundle.getLastRefreshAttempt() != null);
					assertEquals(BundleRefreshError.SUCCESS, bundle.getLastRefreshError());
				}
			}.perform();
		}	
		
		@Test
		public void testRefershBundle_bundleDoesNotExist_assertNotFound()  throws Exception
		{
			new TestPlan()
			{
				protected Collection<TrustBundle> bundles;
				
				@Override
				protected Collection<TrustBundle> getBundlesToAdd()
				{
					try
					{
						bundles = new ArrayList<TrustBundle>();
						
						TrustBundle bundle = new TrustBundle();
						bundle.setBundleName("testBundle1");
						File fl = new File("src/test/resources/bundles/providerTestBundle.p7b");
						bundle.setBundleURL(filePrefix + fl.getAbsolutePath());	
						bundle.setRefreshInterval(24);
						bundle.setSigningCertificateData(null);		
						bundles.add(bundle);
						
						return bundles;
					}
					catch (Exception e)
					{
						throw new RuntimeException (e);
					}
				}
				
				@Override
				protected String getBundleNameToRefresh()
				{
					return "testBundle2";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(404, ex.getRawStatusCode());
				}
			}.perform();
		}	
		
		@Test
		public void testRefershBundle_errorInRefresh_assertServiceError()  throws Exception
		{
			new TestPlan()
			{
				@Override
				protected void setupMocks()
				{
					try
					{
						super.setupMocks();
						
						TrustBundleRepository mockDAO = mock(TrustBundleRepository.class);
						
						doThrow(new RuntimeException()).when(mockDAO).findByBundleNameIgnoreCase(eq("testBundle1"));
						bundleService.setTrustBundleRepository(mockDAO);
					}
					catch (Throwable t)
					{
						throw new RuntimeException(t);
					}
				}
				
				@Override
				protected void tearDownMocks()
				{
					super.tearDownMocks();
					
					bundleService.setTrustBundleRepository(bundleRepo);
				}	
				
				@Override
				protected Collection<TrustBundle> getBundlesToAdd()
				{
					return null;
				}
				
				@Override
				protected String getBundleNameToRefresh()
				{
					return "testBundle1";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(500, ex.getRawStatusCode());
				}
			}.perform();
		}		
		
}
