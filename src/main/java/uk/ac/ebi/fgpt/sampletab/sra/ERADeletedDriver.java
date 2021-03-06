package uk.ac.ebi.fgpt.sampletab.sra;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;

public class ERADeletedDriver extends AbstractDriver {

    
    protected ERADAO eraDao = new ERADAO();
    
    private Logger log = LoggerFactory.getLogger(getClass());
    
    private Date now = new Date();
    
	public ERADeletedDriver() {
		
	}

    @Override
    public void doMain(String[] args){
        super.doMain(args);

    	try {
			eraDao.setup();
		} catch (ClassNotFoundException e) {
			log.error("Unable to find oracle driver", e);
			return;
		}
    	
    	//this is a list of all deleted things in ERA
    	List<String> deletedSamples = eraDao.getPrivateSamples();
    	
		EntityManagerFactory emf = Resources.getInstance().getEntityManagerFactory(); 
        EntityManager em = emf.createEntityManager();
        
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        
        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em); 
        for (String biosampleAccession : deletedSamples) {
        	
        	log.trace("Checking "+biosampleAccession);
        	
        	boolean isPublic = false;
        	//get the sample object from hibernate
        	BioSample bioSample = biosampleDAO.find(biosampleAccession);
        	//check if it was a valid object
        	if (bioSample != null) {
	        	bioSample.isPublic();
	        	
	        	if (isPublic) {
		        	//this is a sample that is public, from a source that says it should be private
		        	//so we make it private
		        	log.info("Discovered that "+biosampleAccession+" needs to be made private");
		        	bioSample.setPublicFlag(false);
		        	//since we changed something, mark it in the update date
                    bioSample.setUpdateDate(now);
		
		        	//persist it back into the database
		            biosampleDAO.mergeBean(bioSample);
		            
		        	log.info("Finished making "+biosampleAccession+" private");
	        	}
        	}        	
        }
        log.info("Commiting transaction");
        transaction.commit();
        log.info("Commited transaction");
        log.info("Closing entity manager");
        em.close();
        log.info("Closed entity manager");
        log.info("Closing entity manager factory");
        emf.close();
        log.info("Closed entity manager factory");
    }

	public static void main(String[] args) {
		new ERADeletedDriver().doMain(args);
	}
}
