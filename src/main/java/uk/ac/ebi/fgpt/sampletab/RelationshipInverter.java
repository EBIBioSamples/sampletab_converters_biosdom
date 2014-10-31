package uk.ac.ebi.fgpt.sampletab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.expgraph.properties.SampleCommentType;
import uk.ac.ebi.fg.biosd.model.expgraph.properties.SampleCommentValue;
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
    
    private static final String DERIVEDFROM = "Derived From";
    private static final String DERIVEDTO = "Derived To";

    private static Logger log =  LoggerFactory.getLogger(RelationshipInverter.class); 
    
    @Override
    public void doMain(String[] args){
        super.doMain(args);

        em = Resources.getInstance().getEntityManagerFactory().createEntityManager();
        
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        //mark as only temporary if appropriate
        if (rollback) transaction.setRollbackOnly();

//        Set<List<String>> derivedFroms = getDerivedFrom();
//        Set<List<String>> derivedTos = getDerivedFrom();
        
        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em);
        
        
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
                
                biosampleDAO.mergeBean(bs);
            }
        }
        
        //get all derived to without an inverse
        for (String[] derivedTo : getDerivedToWithoutInverse() ) {
            //TODO delete the derived to       
            log.info("deleting "+derivedTo[1]+" <- "+derivedTo[0]);
                        
            BioSample bs = biosampleDAO.find(derivedTo[1]);
            for (ExperimentalPropertyValue v : Collections.unmodifiableCollection(bs.getPropertyValues())) {
                if (v.getTermText().equals(derivedTo[0]) && v.getType().getTermText().equals(DERIVEDTO)) {
                    bs.getPropertyValues().remove(v);
                }
            }
            
            biosampleDAO.mergeBean(bs);
        }
        
        //rollback the transaction
        if (rollback) 
            transaction.rollback();
        else {
            transaction.commit();
        }
        
        em.close();
    }
    
    private Set<String[]> getDerivedFromWithoutInverse() {
        
        Query q = em.createQuery("SELECT bs1.acc, str(pv1.termText) " +
        		"FROM BioSample bs1 INNER JOIN bs1.propertyValues AS pv1 INNER JOIN pv1.type AS pt1 " +
        		"WHERE pt1.termText = 'Derived From' AND bs1.acc NOT IN " +
        		"( SELECT str(pv2.termText) FROM BioSample bs2 INNER JOIN bs2.propertyValues AS pv2 INNER JOIN pv2.type AS pt2 " +
        		"WHERE pt2.termText = 'Derived To' )");
        
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
        
        Query q = em.createQuery("FROM BioSample bs INNER JOIN bs.propertyValues AS pv INNER JOIN pv.type AS pt WHERE pt = 'Derived From'");
        
        List<BioSample> results = q.getResultList();
        Set<List<String>> toReturn = new HashSet<List<String>>();
        for (BioSample bs : results) {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : bs.getPropertyValues()) {
                if (pv.getType().getTermText().equals("Derived From")) {
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
        
        Query q = em.createQuery("SELECT bs1.acc, str(pv1.termText) " +
                "FROM BioSample bs1 INNER JOIN bs1.propertyValues AS pv1 INNER JOIN pv1.type AS pt1 " +
                "WHERE pt1.termText = 'Derived To' AND bs1.acc NOT IN " +
                "( SELECT str(pv2.termText) FROM BioSample bs2 INNER JOIN bs2.propertyValues AS pv2 INNER JOIN pv2.type AS pt2 " +
                "WHERE pt2.termText = 'Derived From' )");
        
        q.setMaxResults(maxCount);
        
        Set<String[]> toReturn = new HashSet<String[]>();
        for (Object result : q.getResultList()) {
            Object[] a = (Object[]) result;
            String[] b = (String[]) a;
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
                if (pv.getType().getTermText().equals("Derived To")) {
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
