package uk.ac.ebi.fgpt.sampletab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.expgraph.properties.SampleCommentType;
import uk.ac.ebi.fg.biosd.model.expgraph.properties.SampleCommentValue;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;

public class RelationshipInverter extends AbstractDriver {


    @Option(name = "--max", aliases = { "-m" }, usage = "maximum number of attributes to create/delete at once")
    protected int maxCount = 1000;

    @Option(name = "--rollback", aliases = { "-r" }, usage = "rollback database modifications")
    protected boolean rollback = false;
    
    private EntityManager em = null;
    private EntityManagerFactory emf = null;
    
    private static final String DERIVEDFROM = "Derived From";
    private static final String DERIVEDTO = "Derived To";

    private static Logger log =  LoggerFactory.getLogger(RelationshipInverter.class); 
    
    @Override
    public void doMain(String[] args){
        super.doMain(args);

        emf = Resources.getInstance().getEntityManagerFactory();
        em = emf.createEntityManager();
        
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        //mark as only temporary if appropriate
        if (rollback) transaction.setRollbackOnly();

//        Set<List<String>> derivedFroms = getDerivedFrom();
//        Set<List<String>> derivedTos = getDerivedFrom();
        
        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em);
        AccessibleDAO<MSI> msiDAO = new AccessibleDAO<MSI>(MSI.class, em);
        
        Date now = new Date();
        
        //get all derived from without an inverse
        for (String[] derivedFrom : getDerivedFromWithoutInverse() ) {
            //TODO create the inverses       
            log.info("creating "+derivedFrom[1]+" -> "+derivedFrom[0]);
            
            SampleCommentValue v = new SampleCommentValue(derivedFrom[0], new SampleCommentType(DERIVEDTO));
            
            BioSample bs = biosampleDAO.find(derivedFrom[1]);
            if (bs == null) {
                log.warn("Unable to retrieve "+derivedFrom[1]+" to add inverse to");
            } else {
                bs.addPropertyValue(v);
                
                //update the updatedate of the sample
                bs.setUpdateDate(now);
                
                biosampleDAO.mergeBean(bs);
                
                //update the update date for the msi(s) containing this submission
                //TODO review this
                Date updateDate = new Date();
                for(MSI msi : bs.getMSIs()) {
                	msi.setUpdateDate(updateDate);
                	msiDAO.mergeBean(msi);
                }
            }
        }
        
        //rollback the transaction
        if (rollback) {
        	log.info("rolling back transaction");
            transaction.rollback();
        	log.info("rolled back transaction");
        } else {
        	log.info("commiting transaction");
            transaction.commit();
        	log.info("commited transaction");
        }
        
        //start a new transaction
        
        transaction = em.getTransaction();
        transaction.begin();
        //mark as only temporary if appropriate
        if (rollback) transaction.setRollbackOnly();
        
        //get all derived to without an inverse
        for (String[] derivedTo : getDerivedToWithoutInverse() ) {
            //TODO delete the derived to       
            log.info("deleting "+derivedTo[1]+" <- "+derivedTo[0]);
                        
            BioSample bs = biosampleDAO.find(derivedTo[0]);
            Iterator<ExperimentalPropertyValue> i = bs.getPropertyValues().iterator();
            
            while (i.hasNext()) {
                ExperimentalPropertyValue v = i.next();
                if (v.getTermText().equals(derivedTo[1]) && v.getType().getTermText().equals(DERIVEDTO)) {
                    i.remove();
                    //update the updatedate of the sample
                    bs.setUpdateDate(now);
                }
            }
            
            biosampleDAO.mergeBean(bs);
            
            /*
            //update the update date for the msi(s) containing this submission
            Date updateDate = new Date();
            for(MSI msi : bs.getMSIs()) {
            	msi.setUpdateDate(updateDate);
            	msiDAO.mergeBean(msi);
            }
            */
        }
        
        //rollback the transaction
        if (rollback) {
        	log.info("rolling back transaction");
	        transaction.rollback();
	    	log.info("rolled back transaction");
	    } else {
	    	log.info("commiting transaction");
	        transaction.commit();
	    	log.info("commited transaction");
        }

        log.info("Closing entity manager");
        em.close();
        log.info("Closed entity manager");
        log.info("Closing entity manager factory");
        emf.close();
        log.info("Closed entity manager factory");
    }
    
    private Set<String[]> getDerivedFromWithoutInverse() {
        
        Query q = em.createQuery("SELECT bs1.acc, str(substr(pv1.termText, 0, 15)) FROM BioSample bs1 JOIN bs1.propertyValues AS pv1 JOIN pv1.type AS pt1 WHERE pt1.termText = '"+DERIVEDFROM+"' AND bs1.acc NOT IN ( SELECT str(substr(pv2.termText, 0, 15)) FROM BioSample bs2 JOIN bs2.propertyValues AS pv2 JOIN pv2.type AS pt2 WHERE pt2.termText = '"+DERIVEDTO+"' AND bs2.acc = str(substr(pv1.termText, 0, 15)))");
        
        q.setMaxResults(maxCount);
        
        Set<String[]> toReturn = new HashSet<String[]>();
        for (Object result : q.getResultList()) {
            Object[] a = (Object[]) result;
            String[] b = new String[2];
            b[0] = (String) a[0];
            b[1] = (String) a[1];
            toReturn.add(b);
        }
        
        return toReturn;
    }
    
    
    private Set<List<String>> getDerivedFrom() {
        
        Query q = em.createQuery("FROM BioSample bs INNER JOIN bs.propertyValues AS pv INNER JOIN pv.type AS pt WHERE pt.termText = 'Derived From'");
        
        List<BioSample> results = q.getResultList();
        Set<List<String>> toReturn = new HashSet<List<String>>();
        for (BioSample bs : results) {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : bs.getPropertyValues()) {
                if (pv.getType().getTermText().equals(DERIVEDFROM)) {
                    List<String> pair = new ArrayList<String>(2);
                    pair.set(0, bs.getAcc());
                    pair.set(1, pv.getTermText());
                    toReturn.add(Collections.unmodifiableList(pair));
                }
            }
        }
        
        return toReturn;
    }
    
    private Set<String[]> getDerivedToWithoutInverse() {
        Query q = em.createQuery("SELECT bs1.acc, str(substr(pv1.termText, 0, 15)) FROM BioSample bs1 JOIN bs1.propertyValues AS pv1 JOIN pv1.type AS pt1 WHERE pt1.termText = '"+DERIVEDTO+"' AND bs1.acc NOT IN ( SELECT str(substr(pv2.termText, 0, 15)) FROM BioSample bs2 JOIN bs2.propertyValues AS pv2 JOIN pv2.type AS pt2 WHERE pt2.termText = '"+DERIVEDFROM+"' AND bs2.acc = str(substr(pv1.termText, 0, 15)))");
        
        q.setMaxResults(maxCount);
        
        Set<String[]> toReturn = new HashSet<String[]>();
        for (Object result : q.getResultList()) {
            Object[] a = (Object[]) result;
            String[] b = new String[2];
            b[0] = (String) a[0];
            b[1] = (String) a[1];
            toReturn.add(b);
        }
        
        return toReturn;
    }
    
    private Set<List<String>> getDerivedTo() {
        
        Query q = em.createQuery("FROM BioSample bs INNER JOIN bs.propertyValues AS pv INNER JOIN pv.type AS pt WHERE pt = 'Derived To'");
        
        List<BioSample> results = q.getResultList();
        Set<List<String>> toReturn = new HashSet<List<String>>();
        for (BioSample bs : results) {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : bs.getPropertyValues()) {
                if (pv.getType().getTermText().equals(DERIVEDTO)) {
                    List<String> pair = new ArrayList<String>(2);
                    pair.set(0, bs.getAcc());
                    pair.set(1, pv.getTermText());
                    toReturn.add(Collections.unmodifiableList(pair));
                }
            }
        }
        
        return toReturn;
    }
    

    public static void main(String[] args)  {
        new RelationshipInverter().doMain(args);

    }
}
