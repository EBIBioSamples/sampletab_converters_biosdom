package uk.ac.ebi.fgpt.sampletab.sra;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;

public class ERADeletedDriver extends AbstractDriver {

    
    protected ERADAO eraDom = new ERADAO();
    
    private Logger log = LoggerFactory.getLogger(getClass());
    
    
	public ERADeletedDriver() {
		
	}

    @Override
    public void doMain(String[] args){
        super.doMain(args);

    	try {
			eraDom.setup();
		} catch (ClassNotFoundException e) {
			log.error("Unable to find oracle driver", e);
			return;
		}
    	
    	//this is a list of all deleted things in ERA
    	List<String> deletedSamples = eraDom.getPrivateSamples();
    	

        EntityManager em = Resources.getInstance().getEntityManagerFactory().createEntityManager();
        
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        
        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em); 
        for (String biosampleAccession : deletedSamples) {
        	
        	log.info("Checking "+biosampleAccession);
        	
        	boolean isPublic = false;
        	//get the sample object from hibernate
        	BioSample bioSample = biosampleDAO.find(biosampleAccession);
        	//check if it was a valid object
        	if (bioSample != null) {
	        	if ((bioSample.getPublicFlag() != null && bioSample.getPublicFlag() == true ) ) {
	        		//the sample itself is public
	        		isPublic = true;
	        	} else if (bioSample.getReleaseDate() != null && bioSample.getReleaseDate().before(new Date())) {
	        		//the samples release date is in the past
	        		isPublic = true;
	        	} else {
	        		//the submission may be public, even if the sample is not set to public
	        		//have to handle the legacy of multiple owner MSIs
	        		for (MSI msi : bioSample.getMSIs()) {
	        			if (msi.getPublicFlag() != null && msi.getPublicFlag() == true) {
	        				//the MSI itself is flagged as public
	        				isPublic = true;
	        			} else if (msi.getReleaseDate() != null && msi.getReleaseDate().before(new Date())) {
	        				//the MSI has a release date in the past
	        				isPublic = true;	        				
	        			}
	        		}
	        	}
	        	
	        	if (isPublic) {
		        	//this is a sample that is public, from a source that says it should be private
		        	//so we make it private
		        	log.info("Discovered that "+biosampleAccession+" needs to be made private");
		        	bioSample.setPublicFlag(false);
		
		        	//persist it back into the database
		            biosampleDAO.mergeBean(bioSample);
		            
		        	log.info("Finished making "+biosampleAccession+" private");
	        	}
        	}        	
        }
        log.info("Commiting transaction");
        transaction.commit();
        log.info("Closing entity manager");
        em.close();
    }

	public static void main(String[] args) {
		new ERADeletedDriver().doMain(args);
	}
}
