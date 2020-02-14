/* 
Copyright (c) 2010, NHIN Direct Project
All rights reserved.

Authors:
   Greg Meyer      gm2552@cerner.com
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.  Neither the name of the The NHIN Direct Project (nhindirect.org). 
nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
THE POSSIBILITY OF SUCH DAMAGE.
*/


package org.nhindirect.config.resources;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nhindirect.config.model.TrustBundle;
import org.nhindirect.config.model.TrustBundleDomainReltn;
import org.nhindirect.config.model.exceptions.CertificateConversionException;
import org.nhindirect.config.model.utils.CertUtils;
import org.nhindirect.config.repository.AddressRepository;
import org.nhindirect.config.repository.DomainRepository;
import org.nhindirect.config.repository.TrustBundleAnchorRepository;
import org.nhindirect.config.repository.TrustBundleDomainReltnRepository;
import org.nhindirect.config.repository.TrustBundleRepository;
import org.nhindirect.config.resources.util.EntityModelConversion;
import org.nhindirect.config.store.TrustBundleAnchor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriTemplate;

import edu.emory.mathcs.backport.java.util.Arrays;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Resource for managing address resources in the configuration service.
 * <p>
 * Although not required, this class is instantiated using the Jersey SpringServlet and dependencies are defined in the Sprint context XML file.
 * @author Greg Meyer
 * @since 2.0
 */
@RestController
@RequestMapping("trustbundle")
public class TrustBundleResource extends ProtectedResource
{	
    private static final Log log = LogFactory.getLog(TrustBundleResource.class);
    
    /**
     * TrustBundle repository is injected by Spring
     */
    protected TrustBundleRepository bundleRepo;
  
    /**
     * TrustBundleAnchor repository is injected by Spring
     */
    protected TrustBundleAnchorRepository bundleAnchorRepo;
    
    /**
     * TrustBundleDomainReltn repository is injected by Spring
     */
    protected TrustBundleDomainReltnRepository reltnRepo;
    
    /**
     * Domain repository is injected by Spring
     */
    protected DomainRepository domainRepo;
    
    /**
     * Address repository is injected by Spring
     */
    protected AddressRepository addRepo;
    
    /**
     * Producer template is defined in the context XML file an injected by Spring
     */
    protected ProducerTemplate template;
    
    /**
     * Constructor
     */
    public TrustBundleResource()
    {
		
	}
    
    /**
     * Sets the trustBundle repository.  Auto populate by Spring
     * @param bundleRepo The trustBundle repository.
     */
    @Autowired
    public void setTrustBundleRepository(TrustBundleRepository bundleRepo) 
    {
        this.bundleRepo = bundleRepo;
    }
    
    /**
     * Sets the trustBundleAnchor repository.  Auto populate by Spring
     * @param bundleAnchorRepo The trustBundle anchor repository.
     */
    @Autowired
    public void setTrustBundleRepository(TrustBundleAnchorRepository bundleAnchorRepo) 
    {
        this.bundleAnchorRepo = bundleAnchorRepo;
    }
    
    /**
     * Sets the trustBundleDomainReltn repository.  Auto populate by Spring
     * @param reltnRepo The trustBundleDomainReltn repository.
     */
    @Autowired
    public void setTrustBundleDomainReltnRepository(TrustBundleDomainReltnRepository reltnRepo) 
    {
        this.reltnRepo = reltnRepo;
    }
    
    /**
     * Sets the domain repository.  Auto populate by Spring
     * @param domainRepo The domain repository.
     */
    @Autowired
    public void setDomainRepository(DomainRepository domainRepo) 
    {
        this.domainRepo = domainRepo;
    }
    
    /**
     * Sets the address repository.  Auto populated by Spring
     * @param addRepo Address repository
     */
    @Autowired
    public void setAddressRepository(AddressRepository addRepo) 
    {
        this.addRepo = addRepo;
    }
    
    /**
     * Sets the producer template.  Auto populate by Spring
     * @param template The producer template.
     */
    @Autowired
    @Qualifier("bundleRefresh")
    public void setTemplate(ProducerTemplate template) 
    {
        this.template = template;
    }
    
    /**
     * Gets all trust bundles in the system.
     * @param fetchAnchors Indicates if the retrieval should also include the trust anchors in the bundle.  When only needing bundle names,
     * this parameter should be set to false for better performance. 
     * @return A JSON representation of a collection of all trust bundles in the system.  Returns a status of 204 if no trust bundles exist.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Flux<TrustBundle>> getTrustBundles(@RequestParam(name="fetchAnchors", defaultValue="true") boolean fetchAnchors)
    {
    	try
    	{
    		final Flux<TrustBundle> retVal = bundleRepo.findAll()
    		.flatMap(bundle -> 
    		{
    			
    			final Flux<TrustBundleAnchor> anchorFlux = (!fetchAnchors) ? Flux.fromIterable(new ArrayList<TrustBundleAnchor>()) :
    							bundleAnchorRepo.findByTrustBundleId(bundle.getId());

    			return anchorFlux.map(anchor -> new BundleAnchorTuple(bundle, anchor))
    			.switchIfEmpty(Mono.just(new BundleAnchorTuple(bundle, null)))
    			.collectList();
    		})
    		.map(tupls ->
    		{
    			final List<org.nhindirect.config.store.TrustBundleAnchor> anchors = new ArrayList<>();
    			
    			for (BundleAnchorTuple tupl : tupls)
    				if (tupl.getAnchor() != null)
    					anchors.add(tupl.getAnchor());
    			
    			return EntityModelConversion.toModelTrustBundle(tupls.get(0).getBundle() , anchors);
    		});
    		
    		
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).body(retVal);
    	}
    	catch (Throwable e)
    	{
    		log.error("Error looking up trust bundles", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }
    
    @GetMapping(value="domains/bundles/reltns", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Flux<TrustBundleDomainReltn>> getAllTrustBundleDomainRelts(@RequestParam(name="fetchAnchors", defaultValue="true") boolean fetchAnchors)
    {
    	try
    	{
    		final Flux<TrustBundleDomainReltn> retVal = reltnRepo.findAll()
    				.map(bundleReltn -> 
    				{ 
    					final List<TrustBundleAnchor> anchors = (!fetchAnchors) ? new ArrayList<TrustBundleAnchor>() :
							bundleAnchorRepo.findByTrustBundleId(bundleReltn.getTrustBundleId()).collectList().block();

    					final List<org.nhindirect.config.store.Address> addrs = (!fetchAnchors) ? new ArrayList<org.nhindirect.config.store.Address>() :
    						addRepo.findByDomainId(bundleReltn.getDomainId()).collectList().block();
    					
    		    		final TrustBundleDomainReltn newReltn = new TrustBundleDomainReltn();

    		    		newReltn.setIncoming(bundleReltn.isIncoming());
    		    		newReltn.setOutgoing(bundleReltn.isOutgoing());
    		    		newReltn.setDomain(EntityModelConversion.toModelDomain(domainRepo.findById(bundleReltn.getDomainId()).block(), addrs));
    		    		newReltn.setTrustBundle(EntityModelConversion.toModelTrustBundle(bundleRepo.findById(bundleReltn.getTrustBundleId()).block(), anchors));
    		    		
    		    		return newReltn;
    				});
    		
    		
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).body(retVal);
    	}
    	catch (Throwable e)
    	{
    		log.error("Error looking up trust bundles", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}    	
    }
    
    /**
     * Gets all trust bundles associated to a domain.
     * @param domainName The name of the domain to fetch trust bundles for.
     * @param fetchAnchors  Indicates if the retrieval should also include the trust anchors in the bundle.  When only needing bundle names,
     * this parameter should be set to false for better performance. 
     * @return  A JSON representation of a collection of trust bundle that are associated to the given domain.  Returns a status of
     * 404 if a domain with the given name does not exist or a status of 404 if no trust bundles are associated with the given name.
     */
    @GetMapping(value="domains/{domainName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Flux<TrustBundleDomainReltn>> getTrustBundlesByDomain(@PathVariable("domainName") String domainName, 
    		@RequestParam(name="fetchAnchors", defaultValue="true") boolean fetchAnchors)
    {
    	
    	// make sure the domain exists
    	org.nhindirect.config.store.Domain entityDomain;
    	try
    	{
    		entityDomain = domainRepo.findByDomainNameIgnoreCase(domainName).block();
    		if (entityDomain == null)
    		{
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    		}    		
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}

    	try
    	{
    		final Flux<TrustBundleDomainReltn> retVal = reltnRepo.findByDomainId(entityDomain.getId())
    	    		.flatMap(bundleReltn -> 
    	    		{
    	    			return bundleRepo.findById(bundleReltn.getTrustBundleId())
    	        		.flatMap(bundle -> 
    	        		{
    	        			
    	        			final Flux<TrustBundleAnchor> anchorFlux = (!fetchAnchors) ? Flux.fromIterable(new ArrayList<TrustBundleAnchor>()) :
    	        							bundleAnchorRepo.findByTrustBundleId(bundle.getId());

    	        			return anchorFlux.map(anchor -> new BundleAnchorTuple(bundle, anchor))
    	        			.switchIfEmpty(Mono.just(new BundleAnchorTuple(bundle, null)))
    	        			.collectList();
    	        		})
    	        		.map(tupls ->
    	        		{
    	        			final List<org.nhindirect.config.store.TrustBundleAnchor> anchors = new ArrayList<>();
    	        			
    	        			for (BundleAnchorTuple tupl : tupls)
    	        				if (tupl.getAnchor() != null)
    	        					anchors.add(tupl.getAnchor());
    	        			
    	        			return EntityModelConversion.toModelTrustBundle(tupls.get(0).getBundle() , anchors);
    	        		})
    	        		.map(bundle -> new BundleReltnTuple(bundle, bundleReltn));
    	    		})
    	    		.map(tupl ->
    	    		{
    	    			final TrustBundleDomainReltn newReltn = new TrustBundleDomainReltn();
    	    			
    		    		newReltn.setIncoming(tupl.getReltn().isIncoming());
    		    		newReltn.setOutgoing(tupl.getReltn().isOutgoing());	
    		    		newReltn.setDomain(EntityModelConversion.toModelDomain(entityDomain, Collections.emptyList()));
    		    		newReltn.setTrustBundle(tupl.getBundle());
    		    		
    	    			return newReltn;
    	    		});    				

    		
    		
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).body(retVal);
    	}
    	catch (Throwable e)
    	{
    		log.error("Error looking up trust bundles", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }
    
    /**
     * Gets a trust bundle by name.
     * @param bundleName The name of the trust bundle to retrieve.
     * @return A JSON representation of a the trust bundle.  Returns a status of 404 if a trust bundle with the given name
     * does not exist.
     */
    @GetMapping(value="{bundleName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<TrustBundle>> getTrustBundleByName(@PathVariable("bundleName") String bundleName)
    {
    	try
    	{
    		final org.nhindirect.config.store.TrustBundle retBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		
    		if (retBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();

			final List<TrustBundleAnchor> anchors =
				bundleAnchorRepo.findByTrustBundleId(retBundle.getId()).collectList().block();
    		
    		final TrustBundle modelBundle = EntityModelConversion.toModelTrustBundle(retBundle, anchors);
    		
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).body(Mono.just(modelBundle));  
    		
    	}
    	catch (Throwable e)
    	{
    		log.error("Error looking up trust bundles", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }  
    
    /**
     * Adds a trust bundle to the system.
     * @param uriInfo Injected URI context used for building the location URI.
     * @param bundle The bundle to add to the system.
     * @return Status of 201 if the bundle was added or a status of 409 if a bundle with the same name already exists.
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<Void>> addTrustBundle(@RequestBody TrustBundle bundle)
    {
    	// make sure it doesn't exist
    	try
    	{
    		if (bundleRepo.findByBundleNameIgnoreCase(bundle.getBundleName()).block() != null)
    			return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	try
    	{    		
    		Map.Entry<org.nhindirect.config.store.TrustBundle, Collection<org.nhindirect.config.store.TrustBundleAnchor>> entry = EntityModelConversion.toEntityTrustBundle(bundle);
    		
    		org.nhindirect.config.store.TrustBundle addBundle = entry.getKey();
    		addBundle.setId(null);
    		
    		bundleRepo.save(addBundle).block();
    		
    		// the trust bundle does not contain any of the anchors
    		// they must be fetched from the URL... use the
    		// refresh route to force downloading the anchors
    		template.sendBody(addBundle);
    	
    		final URI uri = new UriTemplate("/{bundle}").expand("trustbundle/" + bundle.getBundleName());
    		
    		return ResponseEntity.created(uri).cacheControl(noCache).build();    		
    	}
    	catch (Exception e)
    	{
    		log.error("Error adding trust bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }   
    
    /**
     * Forces the refresh of a trust bundle.
     * @param bundleName  The name of the trust bundle to refresh.
     * @return Status of 204 if the bundle was refreshed or a status of 404 if a trust bundle with the given name does not exist.
     */
    @PostMapping("{bundle}/refreshBundle")
    public ResponseEntity<Mono<Void>> refreshTrustBundle(@PathVariable("bundle") String bundleName)    
    {
    	// make sure it exists and refresh it
    	try
    	{
    		final org.nhindirect.config.store.TrustBundle entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    		
    		template.sendBody(entityBundle);
    		
    		return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error refreshing bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }
    
    /**
     * Deletes a trust bundle.
     * @param bundleName  The name of the bundle to delete.
     * @return Status of 200 if the trust bundle was deleted or a status of 404 if a trust bundle with the given name
     * does not exist.
     */
    @DeleteMapping("{bundle}")
    public ResponseEntity<Mono<Void>> deleteBundle(@PathVariable("bundle") String bundleName)
    {
    	// make sure it exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	try
    	{    		
    		bundleAnchorRepo.deleteByTrustBundleId(entityBundle.getId()).block();
    		reltnRepo.deleteByTrustBundleId(entityBundle.getId()).block();
    		bundleRepo.deleteById(entityBundle.getId()).block();
    		
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error deleting trust bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }
    
    /**
     * Updates the signing certificate of a trust bundle.
     * @param bundleName The name of the trust bundle to update.
     * @param certData A DER encoded representation of the new signing certificate.
     * @return Status of 204 if the trust bundle's signing certificate was updated, status of 400 if the signing certificate is
     * invalid, or a status 404 if a trust bundle with the given name does not exist.
     */
    @PostMapping(value="{bundle}/signingCert", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<Void>> updateSigningCert(@PathVariable("bundle") String bundleName, @RequestBody(required=false) byte[] certData)
    {   
    	X509Certificate signingCert = null;
    	if (certData != null && certData.length > 0)
    	{
	    	try
	    	{
	    		signingCert = CertUtils.toX509Certificate(certData);		
	    	}
	    	catch (CertificateConversionException ex)
	    	{
	    		log.error("Signing certificate is not in a valid format " + bundleName, ex);
	    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(noCache).build();
	    	}
    	}
    	
    	// make sure the bundle exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// now update
    	try
    	{
    		entityBundle.setSigningCertificateData((signingCert == null) ? null : signingCert.getEncoded());
    		bundleRepo.save(entityBundle).block();
    		
    		return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error updating trust bundle signing certificate.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	
    }
    
    /**
     * Updates multiple bundle attributes.  If the URL of the bundle changes, then the bundle is automatically refreshed.
     * @param bundleName The name of the bundle to update.
     * @param bundleData The data of the trust bundle to update.  Empty or null attributes indicate that the attribute should not be changed.
     * @return Status of 204 if the bundle attributes were updated, status of 400 if the signing certificate is
     * invalid, or a status 404 if a trust bundle with the given name does not exist.
     */
    @PostMapping(value="{bundle}/bundleAttributes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<Void>> updateBundleAttributes(@PathVariable("bundle") String bundleName, @RequestBody TrustBundle bundleData)
    {  
    	// make sure the bundle exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// check to see if the bundle info is the same... if so, then exit
    	if (entityBundle.getBundleName().equals(bundleData.getBundleName()) &&
    		entityBundle.getBundleURL().equals(bundleData.getBundleURL()) &&
    		entityBundle.getRefreshInterval() == bundleData.getRefreshInterval())
    	{
    		if (bundleData.getSigningCertificateData() == null && entityBundle.getSigningCertificateData() == null)
    			return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    		
    		else if (bundleData.getSigningCertificateData() != null && entityBundle.getSigningCertificateData() != null
    				&& Arrays.equals(bundleData.getSigningCertificateData(), entityBundle.getSigningCertificateData()))
    				return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    	}
    
    	final String oldBundleURL = entityBundle.getBundleURL();
    	
    	// if there is a signing certificate in the request, make sure it's valid
    	X509Certificate newSigningCert = null;
    	if (bundleData.getSigningCertificateData() != null)
    	{
        	
        	try
        	{
        		newSigningCert = CertUtils.toX509Certificate(bundleData.getSigningCertificateData());		
        	}
        	catch (CertificateConversionException ex)
        	{
        		log.error("Signing certificate is not in a valid format " + bundleName, ex);
        		return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(noCache).build();
        	}
    	}

    	// update the bundle
    	try
    	{
			if (newSigningCert == null)
				entityBundle.setSigningCertificateData(null);
			else
				entityBundle.setSigningCertificateData(newSigningCert.getEncoded());
    		
			if (!StringUtils.isEmpty(bundleData.getBundleName()))
				entityBundle.setBundleName(bundleData.getBundleName());
			
			entityBundle.setRefreshInterval(bundleData.getRefreshInterval());
			
			if (!StringUtils.isEmpty(bundleData.getBundleURL()))
				entityBundle.setBundleURL(bundleData.getBundleURL());				
			
			bundleRepo.save(entityBundle).block();
			
			
			// if the URL changed, the bundle needs to be refreshed
			if (bundleData.getBundleURL() != null && !bundleData.getBundleURL().isEmpty() && !oldBundleURL.equals(bundleData.getBundleURL()))
			{
				entityBundle = bundleRepo.findById(entityBundle.getId()).block();

				template.sendBody(entityBundle);
			}
    		
    		return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error updating trust bundle attributes.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    }
    
    /**
     * Associates a trust bundle to a domain along with directional trust.
     * @param bundleName The name of the bundle to associate to a domain.
     * @param domainName The name of the domain to associate to a bundle.
     * @param incoming Indicates if trust should be allowed for incoming messages.
     * @param outgoing Indicates if trust should be allowed for outgoing messages.
     * @return Status of 204 if the association was made or a status of 404 if either a domain or trust bundle with its given name
     * does not exist.
     */
    @PostMapping("{bundle}/{domain}")
    public ResponseEntity<Mono<Void>> associateTrustBundleToDomain(@PathVariable("bundle") String bundleName, @PathVariable("domain") String domainName,
    		@RequestParam(name="incoming", defaultValue="true") boolean incoming, @RequestParam(name="outgoing", defaultValue="true") boolean outgoing)
    {
    	// make sure the bundle exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// make sure the domain exists
    	org.nhindirect.config.store.Domain entityDomain;
    	try
    	{
    		entityDomain = domainRepo.findByDomainNameIgnoreCase(domainName).block();
    		if (entityDomain == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    		
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// now make the association
    	try
    	{
    		final org.nhindirect.config.store.TrustBundleDomainReltn reltn = 
    				new org.nhindirect.config.store.TrustBundleDomainReltn();
    		
    		reltn.setDomainId(entityDomain.getId());
    		reltn.setTrustBundleId(entityBundle.getId());
    		reltn.setIncoming(incoming);
    		reltn.setOutgoing(outgoing);
    		
    		reltnRepo.save(reltn).block();

    		return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error associating trust bundle to domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}    	
    }
    
    /**
     * Removes the association of a trust bundle from a domain.
     * @param bundleName The name of the trust bundle to remove from the domain.
     * @param domainName The name of the domain to remove from the trust bundle.
     * @return Status of 200 if the association was removed or a status of 404 if either a domain or trust bundle with its given name
     * does not exist.
     */
    @DeleteMapping("{bundle}/{domain}")
    public ResponseEntity<Mono<Void>> disassociateTrustBundleFromDomain(@PathVariable("bundle") String bundleName, @PathVariable("domain") String domainName)
    {    
    	// make sure the bundle exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// make sure the domain exists
    	org.nhindirect.config.store.Domain entityDomain;
    	try
    	{
    		entityDomain = domainRepo.findByDomainNameIgnoreCase(domainName).block();
    		if (entityDomain == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    		
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// now make the disassociation
    	try
    	{
    		reltnRepo.deleteByDomainIdAndTrustBundleId(entityDomain.getId(), entityBundle.getId()).block();

    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error disassociating trust bundle from domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}       	
    }
    
    /**
     * Removes all trust bundle from a domain.
     * @param domainName The name of the domain to remove trust bundle from.
     * @return Status of 200 if trust bundles were removed from the domain or a status of 404 if a domain with the given name
     * does not exist.
     */
    @DeleteMapping("{domain}/deleteFromDomain")
    public ResponseEntity<Mono<Void>> disassociateTrustBundlesFromDomain(@PathVariable("domain") String domainName)
    {   
    	// make sure the domain exists
    	org.nhindirect.config.store.Domain entityDomain;
    	try
    	{
    		entityDomain = domainRepo.findByDomainNameIgnoreCase(domainName).block();
    		if (entityDomain == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    		
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// now make the disassociation
    	try
    	{
    		reltnRepo.deleteByDomainId(entityDomain.getId()).block();

    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error disassociating trust bundles from domain.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}  
    }
    
    /**
     * Removes a trust bundle from all domains.
     * @param bundleName The name of the trust bundle to remove from all domains.
     * @return Status of 200 if the trust bundle was removed from all domains or a status of 404 if a trust bundle with the given
     * name does not exist.
     */
    @DeleteMapping("{bundle}/deleteFromBundle")
    public ResponseEntity<Mono<Void>> disassociateTrustBundleFromDomains(@PathVariable("bundle") String bundleName)
    {   
    	// make sure the bundle exists
    	org.nhindirect.config.store.TrustBundle entityBundle;
    	try
    	{
    		entityBundle = bundleRepo.findByBundleNameIgnoreCase(bundleName).block();
    		if (entityBundle == null)
    			return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up bundle.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}
    	
    	// now make the disassociation
    	try
    	{
    		reltnRepo.deleteByTrustBundleId(entityBundle.getId()).block();
    		return ResponseEntity.status(HttpStatus.OK).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error disassociating trust bundle from domains.", e);
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(noCache).build();
    	}  
    }  
    
    static class BundleAnchorTuple
    {
    	final org.nhindirect.config.store.TrustBundle bundle;
    	final org.nhindirect.config.store.TrustBundleAnchor anchor;

		public BundleAnchorTuple(org.nhindirect.config.store.TrustBundle bundle, org.nhindirect.config.store.TrustBundleAnchor anchor)
    	{
    		this.bundle = bundle;
    		this.anchor = anchor;
    	}

		public org.nhindirect.config.store.TrustBundle getBundle()
		{
			return bundle;
		}

		public org.nhindirect.config.store.TrustBundleAnchor getAnchor()
		{
			return anchor;
		}
    	
    }   
    
    static class BundleReltnTuple
    {
    	final TrustBundle bundle;
    	final org.nhindirect.config.store.TrustBundleDomainReltn reltn;

		public BundleReltnTuple(TrustBundle bundle, org.nhindirect.config.store.TrustBundleDomainReltn reltn)
    	{
    		this.bundle = bundle;
    		this.reltn = reltn;
    	}

		public TrustBundle getBundle()
		{
			return bundle;
		}

		public org.nhindirect.config.store.TrustBundleDomainReltn getReltn()
		{
			return reltn;
		}
    	
    }      
}
